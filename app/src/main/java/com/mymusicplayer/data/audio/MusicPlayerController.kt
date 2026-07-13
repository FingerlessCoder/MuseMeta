package com.mymusicplayer.data.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import java.io.File
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mymusicplayer.data.db.dao.TrackDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrackId: Long? = null,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class MusicPlayerController constructor(
    private val context: Context,
    private val trackDao: TrackDao
) {

    companion object {
        private const val TAG = "MusicPlayerController"
    }

    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val timerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = 0
            return
        }
        _sleepTimerRemainingSeconds.value = minutes * 60
        sleepTimerJob = timerScope.launch {
            while (_sleepTimerRemainingSeconds.value > 0) {
                delay(1000L)
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

    fun initialize() {
        if (exoPlayer != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(context)
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
            })
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

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        updateState()
    }

    fun skipToNext() {
        exoPlayer?.seekToNextMediaItem()
        updateState()
    }

    fun skipToPrevious() {
        exoPlayer?.seekToPreviousMediaItem()
        updateState()
    }

    fun setShuffleMode(enabled: Boolean) {
        exoPlayer?.shuffleModeEnabled = enabled
        _playbackState.value = _playbackState.value.copy(shuffleMode = enabled)
    }

    fun setRepeatMode(mode: Int) {
        exoPlayer?.repeatMode = mode
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun getCurrentMediaIndex(): Int {
        return exoPlayer?.currentMediaItemIndex ?: -1
    }

    fun getQueueSize(): Int {
        return exoPlayer?.mediaItemCount ?: 0
    }

    fun getCurrentTrackIds(): List<Long> = currentTrackIds

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun addTrack(
        filePath: String,
        trackId: Long,
        title: String? = null,
        artist: String? = null,
        albumArtPath: String? = null
    ) {
        val player = exoPlayer ?: return
        val mediaItem = buildMediaItem(filePath, trackId, title, artist, albumArtPath)
        player.addMediaItem(mediaItem)
        player.prepare()
        currentTrackIds = currentTrackIds + trackId
        currentTrackPaths = currentTrackPaths + filePath
        updateState()
    }

    fun addTrackAt(
        index: Int,
        filePath: String,
        trackId: Long,
        title: String? = null,
        artist: String? = null,
        albumArtPath: String? = null
    ) {
        val player = exoPlayer ?: return
        val mediaItem = buildMediaItem(filePath, trackId, title, artist, albumArtPath)
        player.addMediaItem(index, mediaItem)
        player.prepare()
        currentTrackIds = currentTrackIds.toMutableList().apply { add(index.coerceAtMost(size), trackId) }
        currentTrackPaths = currentTrackPaths.toMutableList().apply { add(index.coerceAtMost(size), filePath) }
        updateState()
    }

    private fun buildMediaItem(
        filePath: String,
        trackId: Long,
        title: String?,
        artist: String?,
        albumArtPath: String?
    ): MediaItem {
        val cleanPath = filePath.removePrefix("file://")
        val uri = Uri.fromFile(File(cleanPath))

        val artworkUri = if (!albumArtPath.isNullOrBlank()) {
            val cleanArtPath = albumArtPath.removePrefix("file://")
            Uri.fromFile(File(cleanArtPath))
        } else {
            Uri.EMPTY
        }

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(trackId.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
    }

    fun removeTrack(index: Int) {
        val player = exoPlayer ?: return
        if (index < 0 || index >= player.mediaItemCount) return
        val wasCurrent = index == player.currentMediaItemIndex

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

    fun release() {
        cancelSleepTimer()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        val currentIndex = player.currentMediaItemIndex

        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentTrackId = currentTrackIds.getOrNull(currentIndex),
            currentPosition = player.currentPosition,
            duration = player.duration,
            queueSize = player.mediaItemCount,
            queueIndex = currentIndex,
            shuffleMode = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    private fun onTrackCompleted() {
        val index = exoPlayer?.currentMediaItemIndex ?: return
        val trackId = currentTrackIds.getOrNull(index) ?: return
        Log.d(TAG, "Track completed: id=$trackId")
    }
}
