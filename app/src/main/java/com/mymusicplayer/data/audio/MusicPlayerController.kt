package com.mymusicplayer.data.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mymusicplayer.data.audio.EqRenderersFactory
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.domain.model.Track
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class PlaybackMode {
    SHUFFLE,
    LIST,
    SINGLE
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val playbackMode: PlaybackMode = PlaybackMode.LIST
)

class MusicPlayerController(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {

    companion object {
        private const val TAG = "MusicPlayerController"

        /**
         * Never let a user seek land on (or past) the very end of an item:
         * ExoPlayer treats that as "playback finished" and immediately advances to
         * the next track, which reads as "the song got skipped".
         */
        private const val SEEK_END_GUARD_MS = 2_000L

        /** Fallback tail margin for tracks shorter than [SEEK_END_GUARD_MS]. */
        private const val TAIL_EPSILON_MS = 100L
    }

    private var exoPlayer: ExoPlayer? = null

    private val eqProcessor = GraphicEQProcessor()
    private var audioSettingsJob: Job? = null
    private var eqEnabledCache = false
    private var eqPresetCache = "Normal"
    private var eqBandsCache = listOf(0, 0, 0, 0, 0)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Full playback queue (in ExoPlayer order) exposed to the UI for the "Up Next" sheet.
    private val _queueTracks = MutableStateFlow<List<Track>>(emptyList())
    val queueTracks: StateFlow<List<Track>> = _queueTracks.asStateFlow()

    private val timerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null
    private var isCurrentTrackFavouriteStatus = false

    fun isCurrentTrackFavourite(): Boolean {
        return isCurrentTrackFavouriteStatus
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = 0
            return
        }
        _sleepTimerRemainingSeconds.value = minutes * 60
        sleepTimerJob = timerScope.launch {
            while (_sleepTimerRemainingSeconds.value > 0) {
                delay(1.seconds)
                _sleepTimerRemainingSeconds.value =
                    (_sleepTimerRemainingSeconds.value - 1).coerceAtLeast(0)
            }
            togglePlayPause()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSeconds.value = 0
    }

    private var currentTrackPaths: List<String> = emptyList()
    private var currentTrackIds: List<Long> = emptyList()

    // ── Track info cache (avoids DB query on track transition) ──
    private data class CachedTrackInfo(
        val title: String?,
        val artist: String?,
        val albumArtPath: String?
    )
    private val trackInfoCache = mutableMapOf<Long, CachedTrackInfo>()

    @UnstableApi
    fun initialize() {
        if (exoPlayer != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = EqRenderersFactory(context, eqProcessor)

        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                    if (playbackState == Player.STATE_ENDED) {
                        onTrackCompleted()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateState()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    onPlayerModeFlagsChanged()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    onPlayerModeFlagsChanged()
                }
            })
        }

        audioSettingsJob?.cancel()
        audioSettingsJob = timerScope.launch {
            settingsDataStore.equalizerEnabled.collectLatest { eqEnabledCache = it; applyAudioSettings() }
        }
        timerScope.launch {
            settingsDataStore.equalizerPreset.collectLatest { eqPresetCache = it; applyAudioSettings() }
        }
        timerScope.launch {
            settingsDataStore.equalizerBands.collectLatest {
                eqBandsCache = it.split(";").mapNotNull { v -> v.toIntOrNull() }.take(5)
                    .let { if (it.size == 5) it else listOf(0, 0, 0, 0, 0) }
                applyAudioSettings()
            }
        }
        // Restore the previously selected playback mode (shuffle / list / single).
        // ExoPlayer access must happen on the main thread, so switch context here.
        timerScope.launch {
            settingsDataStore.playbackMode.collectLatest { saved ->
                val player = exoPlayer ?: return@collectLatest
                val mode = runCatching { PlaybackMode.valueOf(saved) }.getOrDefault(PlaybackMode.LIST)
                withContext(Dispatchers.Main) {
                    player.shuffleModeEnabled = mode == PlaybackMode.SHUFFLE
                    player.repeatMode = when (mode) {
                        PlaybackMode.SHUFFLE -> Player.REPEAT_MODE_ALL
                        PlaybackMode.LIST -> Player.REPEAT_MODE_ALL
                        PlaybackMode.SINGLE -> Player.REPEAT_MODE_ONE
                    }
                }
                _playbackState.value = _playbackState.value.copy(playbackMode = mode)
            }
        }
    }

    fun play(
        trackPath: String,
        trackId: Long? = null,
        title: String? = null,
        artist: String? = null,
        albumArtPath: String? = null
    ) {
        playFromQueue(
            listOf(trackPath), 0,
            trackId?.let { listOf(it) },
            titles = title?.let { listOf(it) },
            artists = artist?.let { listOf(it) },
            albumArtPaths = albumArtPath?.let { listOf(it) }
        )
    }

    fun playFromQueue(
        trackPaths: List<String>,
        startIndex: Int,
        trackIds: List<Long>? = null,
        titles: List<String?>? = null,
        artists: List<String?>? = null,
        albumArtPaths: List<String?>? = null
    ) {
        val player = exoPlayer ?: return

        // Start the foreground service FIRST so MusicService.onCreate() creates the
        // MediaSession before playback begins. The system media controller discovers
        // the session via the MediaSessionService intent filter and displays controls
        // in the notification shade, lock screen, and OEM "灵动岛" features.
        context.startForegroundService(
            Intent(context, com.mymusicplayer.service.MusicService::class.java)
        )

        currentTrackPaths = trackPaths
        currentTrackIds = trackIds ?: trackPaths.indices.map { it.toLong() }

        // Pre-fill track info cache so PlayerViewModel doesn't need DB query
        trackInfoCache.clear()
        trackIds?.forEachIndexed { index, id ->
            trackInfoCache[id] = CachedTrackInfo(
                title = titles?.getOrNull(index),
                artist = artists?.getOrNull(index),
                albumArtPath = albumArtPaths?.getOrNull(index)
            )
        }

        val mediaItems = trackPaths.mapIndexed { index, path ->
            // Use Uri.fromFile() — it generates a proper file:/// URI and handles
            // special characters (spaces, CJK, etc.) via URL encoding automatically.
            // Uri.parse("file://$path") creates malformed URIs that Media3's
            // BitmapLoader / SimpleBitmapLoader cannot decode.
            val fileUri = Uri.fromFile(File(path))

            val artPath = albumArtPaths?.getOrNull(index)
            val artworkUri = if (!artPath.isNullOrBlank()) {
                val cleanPath = artPath.removePrefix("file://")
                Uri.fromFile(File(cleanPath))
            } else {
                Uri.EMPTY
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(titles?.getOrNull(index))
                .setArtist(artists?.getOrNull(index))
                .setArtworkUri(artworkUri)
                .build()

            MediaItem.Builder()
                .setUri(fileUri)
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()

        updateState()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateState()
    }

    /**
     * Seeks the current item, guarding against the cases that used to make the
     * progress bar jump and occasionally skip the whole track.
     * Returns true when a seek was actually issued.
     */
    fun seekTo(positionMs: Long): Boolean {
        val player = exoPlayer ?: return false
        if (player.mediaItemCount == 0) return false
        val target = clampSeekPosition(player, positionMs) ?: return false

        // A user scrub means "keep playing from here". Without this the player
        // can sit in a stalled/idle state after the seek and produce silence
        // until some other event nudges it, which read as "no decoding".
        val shouldResume = player.playWhenReady || player.isPlaying
        player.seekTo(target)
        if (shouldResume && !player.isPlaying) {
            player.play()
        }
        updateState()
        return true
    }

    /**
     * Clamps a requested seek position into a safe range for the current item.
     *
     * Guards against three failure modes that were causing the progress bar to
     * "jump" and occasionally skip the whole track:
     *  1. negative / NaN-derived positions (position came from an unknown duration),
     *  2. seeking at or past the item end, which ExoPlayer resolves as
     *     "item finished" and instantly transitions to the next track,
     *  3. seeking while the player has no prepared duration yet (state is
     *     TIME_UNSET / idle), which ExoPlayer answers by skipping forward.
     */
    private fun clampSeekPosition(player: ExoPlayer, positionMs: Long): Long? {
        val duration = player.duration
        // Unknown duration (still preparing / re-buffering): issuing the seek
        // anyway makes ExoPlayer resolve it unpredictably, so drop it.
        if (duration <= 0L || duration == C.TIME_UNSET) return null
        if (positionMs <= 0L) return 0L

        // Keep the target away from the tail. Seeking to (or past) the end makes
        // ExoPlayer declare the item finished and move straight on to the next
        // track, which is what looked like "the song got skipped".
        val maxPosition = if (duration > SEEK_END_GUARD_MS) {
            duration - SEEK_END_GUARD_MS
        } else {
            (duration - TAIL_EPSILON_MS).coerceAtLeast(0L)
        }
        return positionMs.coerceIn(0L, maxPosition)
    }

    fun skipToNext() {
        exoPlayer?.seekToNextMediaItem()
        updateState()
    }

    fun skipToPrevious() {
        exoPlayer?.seekToPreviousMediaItem()
        updateState()
    }

    fun jumpToQueueIndex(index: Int) {
        val player = exoPlayer ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
            updateState()
        }
    }

    fun cyclePlaybackMode() {
        val currentMode = _playbackState.value.playbackMode
        val nextMode = PlaybackMode.entries[(currentMode.ordinal + 1) % PlaybackMode.entries.size]
        _playbackState.value = _playbackState.value.copy(playbackMode = nextMode)
        timerScope.launch {
            settingsDataStore.setPlaybackMode(nextMode.name)
            withContext(Dispatchers.Main) {
                val player = exoPlayer ?: return@withContext
                player.shuffleModeEnabled = nextMode == PlaybackMode.SHUFFLE
                player.repeatMode = when (nextMode) {
                    PlaybackMode.SHUFFLE -> Player.REPEAT_MODE_ALL
                    PlaybackMode.LIST -> Player.REPEAT_MODE_ALL
                    PlaybackMode.SINGLE -> Player.REPEAT_MODE_ONE
                }
            }
        }
    }

    private fun onPlayerModeFlagsChanged() {
        updateState()
        val mode = getCurrentPlaybackMode()
        timerScope.launch { settingsDataStore.setPlaybackMode(mode.name) }
    }

    fun getCurrentPosition(): Long {
        val pos = exoPlayer?.currentPosition ?: 0L
        return if (pos < 0L) 0L else pos
    }

    fun getDuration(): Long {
        val duration = exoPlayer?.duration ?: 0L
        // TIME_UNSET / TIME_END_OF_SOURCE are huge negative sentinels. Normalising
        // them to 0 keeps the progress-bar fraction inside 0..1.
        return if (duration <= 0L) 0L else duration
    }

    fun getCurrentPlaybackMode(): PlaybackMode = _playbackState.value.playbackMode

    fun toggleCurrentTrackFavorite() {
        isCurrentTrackFavouriteStatus = !isCurrentTrackFavouriteStatus
        updateState()
    }

    fun getCachedTrackInfo(trackId: Long): Track? {
        if (trackId <= 0) return null
        val cached = trackInfoCache[trackId] ?: return null
        val idx = currentTrackIds.indexOf(trackId)
        if (idx < 0) return null
        val path = currentTrackPaths.getOrNull(idx) ?: return null

        return Track(
            id = trackId,
            title = cached.title ?: "Unknown",
            artists = cached.artist?.let { a ->
                a.split(" · ").map { com.mymusicplayer.domain.model.Artist(id = 0, name = it.trim()) }
            } ?: emptyList(),
            album = cached.albumArtPath?.let { artPath ->
                com.mymusicplayer.domain.model.Album(
                    id = 0,
                    title = "",
                    albumArtist = null,
                    year = null,
                    artPath = artPath
                )
            },
            duration = 0L,
            filePath = path,
            fileSize = 0L,
            rating = 0,
            // Defaults for remaining fields
            trackNumber = null,
            discNumber = null,
            year = null,
            genre = null,
            comment = null,
            format = null,
            dateAdded = 0L,
            lastPlayed = null,
            playCount = 0,
            lyricsPath = null,
            rawArtistTag = null
        )
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun removeTrack(index: Int) {
        val player = exoPlayer ?: return
        if (index < 0 || index >= player.mediaItemCount) return

        player.removeMediaItem(index)
        if (index in currentTrackIds.indices) {
            currentTrackIds = currentTrackIds.toMutableList().apply { removeAt(index) }
        }
        if (index in currentTrackPaths.indices) {
            currentTrackPaths = currentTrackPaths.toMutableList().apply { removeAt(index) }
        }
        updateState()
    }

    fun clearQueue() {
        val player = exoPlayer ?: return
        player.stop()
        player.clearMediaItems()
        currentTrackIds = emptyList()
        currentTrackPaths = emptyList()
        updateState()
    }

    fun buildAndAddMediaItems(tracks: List<Track>, addAtIndex: Int? = null) {
        val player = exoPlayer ?: return
        val items = tracks.map { track ->
            val fileUri = Uri.fromFile(File(track.filePath))
            val artPath = track.album?.artPath
            val artworkUri = if (!artPath.isNullOrBlank()) {
                Uri.fromFile(File(artPath.removePrefix("file://")))
            } else Uri.EMPTY
            MediaItem.Builder()
                .setUri(fileUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artists.joinToString(" · ") { it.name })
                        .setArtworkUri(artworkUri)
                        .build()
                )
                .build()
        }
        val trackIdsToAdd = tracks.map { it.id }
        val pathsToAdd = tracks.map { it.filePath }
        tracks.forEach { track ->
            trackInfoCache[track.id] = CachedTrackInfo(
                title = track.title,
                artist = track.artists.joinToString(" · ") { it.name },
                albumArtPath = track.album?.artPath
            )
        }
        if (addAtIndex != null) {
            items.forEachIndexed { i, item ->
                player.addMediaItem(addAtIndex + i, item)
            }
            currentTrackIds = currentTrackIds.toMutableList().apply {
                addAll(addAtIndex, trackIdsToAdd)
            }
            currentTrackPaths = currentTrackPaths.toMutableList().apply {
                addAll(addAtIndex, pathsToAdd)
            }
        } else {
            items.forEach { player.addMediaItem(it) }
            currentTrackIds = currentTrackIds + trackIdsToAdd
            currentTrackPaths = currentTrackPaths + pathsToAdd
        }
        updateState()
    }

    fun playNext(tracks: List<Track>) {
        val player = exoPlayer ?: return
        val idsToMove = tracks.map { it.id }.toSet()
        val currentIndex = player.currentMediaItemIndex
        var insertAt = (if (currentIndex < 0) player.mediaItemCount else currentIndex + 1)
            .coerceIn(0, player.mediaItemCount)
        val existingIndices = currentTrackIds.mapIndexedNotNull { index, id ->
            if (id in idsToMove) index else null
        }.sortedDescending()
        for (index in existingIndices) {
            player.removeMediaItem(index)
            if (index in currentTrackIds.indices) {
                currentTrackIds = currentTrackIds.toMutableList().apply { removeAt(index) }
            }
            if (index in currentTrackPaths.indices) {
                currentTrackPaths = currentTrackPaths.toMutableList().apply { removeAt(index) }
            }
            if (index < insertAt) insertAt--
        }
        buildAndAddMediaItems(tracks, insertAt)
    }

    fun addToQueue(tracks: List<Track>) {
        val player = exoPlayer ?: return
        buildAndAddMediaItems(tracks)
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentIndex = player.currentMediaItemIndex

        // Rebuild the full queue list (in ExoPlayer order) for the "Up Next" sheet.
        _queueTracks.value = currentTrackIds.mapNotNull { id -> getCachedTrackInfo(id) }

        val mode = when {
            player.shuffleModeEnabled -> PlaybackMode.SHUFFLE
            player.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackMode.SINGLE
            else -> PlaybackMode.LIST
        }

        val rawPosition = player.currentPosition
        val rawDuration = player.duration
        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentTrackId = currentTrackIds.getOrNull(currentIndex),
            currentPosition = if (rawPosition < 0L) 0L else rawPosition,
            duration = if (rawDuration <= 0L) 0L else rawDuration,
            queueSize = player.mediaItemCount,
            queueIndex = currentIndex,
            playbackMode = mode
        )
    }

    private fun applyAudioSettings() {
        val eqOn = eqEnabledCache

        // Software EQ: the processor is always wired into the pipeline, so band
        // changes apply regardless of hardware effect-engine availability.
        val bands = if (eqOn) eqBandsCache else listOf(0, 0, 0, 0, 0)
        eqProcessor.updateBands(bands)
    }

    private fun onTrackCompleted() {
        val index = exoPlayer?.currentMediaItemIndex ?: return
        val trackId = currentTrackIds.getOrNull(index) ?: return
        Log.d(TAG, "Track completed: id=$trackId")
    }
}
