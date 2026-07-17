package com.mymusicplayer.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.audio.PlaybackMode
import com.mymusicplayer.data.lyrics.LrcParser
import com.mymusicplayer.data.lyrics.LyricLine
import com.mymusicplayer.data.lyrics.LyricsCache
import com.mymusicplayer.data.lyrics.LyricsFetchResult
import com.mymusicplayer.data.lyrics.LyricsFetcher
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val playbackMode: PlaybackMode = PlaybackMode.LIST,
    val isFavorite: Boolean = false,
    val selectedTab: Int = 0,
    val syncedLyrics: List<LyricLine>? = null,
    val currentLyricIndex: Int = -1,
    val lyricsText: String? = null,
    val lyricsLoading: Boolean = false,
    val lyricsError: String? = null,
    val queueTracks: List<Track> = emptyList(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0
)

class PlayerViewModel(
    private val musicPlayerController: MusicPlayerController,
    private val musicRepository: MusicRepository,
    private val lyricsFetcher: LyricsFetcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackUpdateJob: Job? = null
    private var lyricsFetchJob: Job? = null

    init {
        viewModelScope.launch {
            musicPlayerController.playbackState.collect { state ->
                val newTrackId = if (state.currentTrackId != null &&
                    state.currentTrackId != _uiState.value.currentTrack?.id) {
                    state.currentTrackId
                } else null

                if (newTrackId != null) {
                    viewModelScope.launch {
                        musicRepository.incrementPlayCount(newTrackId)
                    }
                }

                val currentTrack = if (state.currentTrackId != null &&
                    state.currentTrackId != _uiState.value.currentTrack?.id) {
                    val trackId = state.currentTrackId

                    val dbTrack = musicRepository.getTrackById(trackId).first()
                    _uiState.value = _uiState.value.copy(
                        isFavorite = (dbTrack?.rating ?: 0) >= 4
                    )

                    val cachedInfo = musicPlayerController.getCachedTrackInfo(trackId)
                    dbTrack ?: cachedInfo
                } else {
                    _uiState.value.currentTrack
                }

                _uiState.value = _uiState.value.copy(
                    currentTrack = currentTrack,
                    isPlaying = state.isPlaying,
                    currentPosition = state.currentPosition,
                    duration = state.duration,
                    queueSize = state.queueSize,
                    queueIndex = state.queueIndex,
                    playbackMode = state.playbackMode,
                    sleepTimerMinutes = _uiState.value.sleepTimerMinutes
                )

                if (newTrackId != null && currentTrack != null) {
                    checkCachedLyrics(currentTrack)
                }
            }
        }

        startPositionUpdates()

        viewModelScope.launch {
            musicPlayerController.sleepTimerRemainingSeconds.collectLatest { remaining ->
                _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
        }
    }

    private fun checkCachedLyrics(track: Track) {
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                syncedLyrics = null,
                currentLyricIndex = -1,
                lyricsText = null,
                lyricsLoading = false,
                lyricsError = null
            )

            val trackId = track.id
            val context = lyricsFetcher.context

            val cached = LyricsCache.loadLyrics(context, trackId)
            if (cached != null) {
                val result = LrcParser.parse(cached)
                if (result.lines.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(syncedLyrics = result.lines)
                    return@launch
                }
                if (cached.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(lyricsText = cached)
                    return@launch
                }
            }

            val dbTrack = musicRepository.getTrackById(trackId).first()
            val lyricsPath = dbTrack?.lyricsPath
            if (lyricsPath != null) {
                val file = java.io.File(lyricsPath)
                if (file.exists()) {
                    val content = file.readText()
                    val result = LrcParser.parse(content)
                    if (result.lines.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(syncedLyrics = result.lines)
                        return@launch
                    }
                    if (content.isNotBlank()) {
                        _uiState.value = _uiState.value.copy(lyricsText = content)
                        return@launch
                    }
                }
            }

        }
    }

    fun triggerLyricsFetch() {
        val track = _uiState.value.currentTrack ?: return

        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                syncedLyrics = null,
                currentLyricIndex = -1,
                lyricsText = null,
                lyricsLoading = true,
                lyricsError = null
            )

            val trackId = track.id
            val context = lyricsFetcher.context

            val cached = LyricsCache.loadLyrics(context, trackId)
            if (cached != null) {
                val result = LrcParser.parse(cached)
                if (result.lines.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        syncedLyrics = result.lines,
                        lyricsLoading = false
                    )
                    return@launch
                }
                if (cached.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(
                        lyricsText = cached,
                        lyricsLoading = false
                    )
                    return@launch
                }
            }

            val dbTrack = musicRepository.getTrackById(trackId).first()
            val lyricsPath = dbTrack?.lyricsPath
            if (lyricsPath != null) {
                val file = java.io.File(lyricsPath)
                if (file.exists()) {
                    val content = file.readText()
                    val result = LrcParser.parse(content)
                    if (result.lines.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            syncedLyrics = result.lines,
                            lyricsLoading = false
                        )
                        return@launch
                    }
                    if (content.isNotBlank()) {
                        _uiState.value = _uiState.value.copy(
                            lyricsText = content,
                            lyricsLoading = false
                        )
                        return@launch
                    }
                }
            }

            val artistName = track.artists.joinToString(", ") { it.name }
            val albumName = track.album?.title
            val duration = track.duration

            when (val fetchResult = lyricsFetcher.fetchLyrics(
                trackName = track.title,
                artistName = artistName,
                albumName = albumName,
                durationMs = duration
            )) {
                is LyricsFetchResult.Success -> {
                    val lrcContent = fetchResult.syncedLrc
                    if (lrcContent != null) {
                        val result = LrcParser.parse(lrcContent)
                        if (result.lines.isNotEmpty()) {
                            LyricsCache.saveLyrics(context, trackId, lrcContent)
                            musicRepository.updateLyricsPath(
                                trackId, LyricsCache.getLyricsFile(context, trackId).absolutePath
                            )
                            _uiState.value = _uiState.value.copy(
                                syncedLyrics = result.lines,
                                lyricsLoading = false
                            )
                            return@launch
                        }
                    }
                    val plain = fetchResult.plainLyrics
                    if (!plain.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(
                            lyricsText = plain,
                            lyricsLoading = false
                        )
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        lyricsError = if (fetchResult.isInstrumental) null else "No lyrics found",
                        lyricsLoading = false,
                        lyricsText = if (fetchResult.isInstrumental) "♫ Instrumental" else null
                    )
                }
                is LyricsFetchResult.NotFound -> {
                    _uiState.value = _uiState.value.copy(
                        lyricsError = "No lyrics found",
                        lyricsLoading = false
                    )
                }
                is LyricsFetchResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        lyricsError = fetchResult.message,
                        lyricsLoading = false
                    )
                }
            }
        }
    }

    private fun startPositionUpdates() {
        playbackUpdateJob?.cancel()
        playbackUpdateJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                val pos = musicPlayerController.getCurrentPosition()
                val dur = musicPlayerController.getDuration()
                val lyrics = _uiState.value.syncedLyrics
                val lyricIndex = if (lyrics != null) {
                    LrcParser.findLineIndex(lyrics, pos)
                } else -1
                _uiState.value = _uiState.value.copy(
                    currentPosition = pos,
                    duration = dur,
                    currentLyricIndex = lyricIndex
                )
            }
        }
    }

    fun togglePlayPause() {
        musicPlayerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        musicPlayerController.seekTo(positionMs)
    }

    fun skipToNext() {
        musicPlayerController.skipToNext()
    }

    fun skipToPrevious() {
        musicPlayerController.skipToPrevious()
    }

    fun cyclePlaybackMode() {
        musicPlayerController.cyclePlaybackMode()
    }

    fun toggleFavorite() {
        val current = _uiState.value
        val trackId = current.currentTrack?.id ?: return
        val newFav = !current.isFavorite
        _uiState.value = current.copy(isFavorite = newFav)
        viewModelScope.launch {
            musicRepository.updateTrackRating(trackId, if (newFav) 5 else 0)
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    fun setSleepTimer(minutes: Int) {
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = minutes)
        musicPlayerController.startSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        _uiState.value = _uiState.value.copy(sleepTimerMinutes = 0)
        musicPlayerController.cancelSleepTimer()
    }

    fun removeCurrentTrackFromQueue() {
        val index = _uiState.value.queueIndex
        if (index >= 0) {
            if (index > 0) {
                musicPlayerController.skipToPrevious()
            }
            musicPlayerController.removeTrack(index)
        }
    }

    fun removeTrackFromQueue(index: Int) {
        musicPlayerController.removeTrack(index)
    }

    fun clearQueue() {
        musicPlayerController.clearQueue()
    }

    fun editTrackMetadata(
        trackId: Long,
        title: String?,
        artists: List<String>?,
        albumTitle: String?,
        year: Int?,
        trackNumber: Int?,
        genre: String?,
        comment: String?
    ) {
        viewModelScope.launch {
            musicRepository.editTrackMetadata(
                trackId = trackId,
                title = title,
                artists = artists,
                albumTitle = albumTitle,
                year = year,
                trackNumber = trackNumber,
                genre = genre,
                comment = comment
            )
            // Refresh the displayed track so the player reflects the edit immediately
            val updated = musicRepository.getTrackById(trackId).first()
            if (updated != null) {
                _uiState.value = _uiState.value.copy(currentTrack = updated)
            }
        }
    }

    fun updateAlbumArt(imageBytes: ByteArray, applyToAll: Boolean = false) {
        val trackId = _uiState.value.currentTrack?.id ?: return
        viewModelScope.launch {
            musicRepository.updateAlbumArt(trackId, imageBytes, applyToAll)
            // Refresh the displayed track so the new cover shows immediately
            val updated = musicRepository.getTrackById(trackId).first()
            if (updated != null) {
                _uiState.value = _uiState.value.copy(currentTrack = updated)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackUpdateJob?.cancel()
    }
}
