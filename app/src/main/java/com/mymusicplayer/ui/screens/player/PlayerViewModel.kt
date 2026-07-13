package com.mymusicplayer.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.audio.PlaybackMode
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
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
    val lyricsText: String? = null,
    val queueTracks: List<Track> = emptyList(),
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Int = 0
)

class PlayerViewModel constructor(
    private val musicPlayerController: MusicPlayerController,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackUpdateJob: kotlinx.coroutines.Job? = null
    init {
        viewModelScope.launch {
            musicPlayerController.playbackState.collect { state ->
                // Track the track ID that changed for play count recording
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
                    // Use cached info for instant display; fall back to DB for full Track
                    val cachedInfo = state.currentTrackId?.let {
                        musicPlayerController.getCachedTrackInfo(it)
                    }
                    if (cachedInfo != null) {
                        cachedInfo.also { track ->
                            _uiState.value = _uiState.value.copy(isFavorite = track.rating >= 4)
                        }
                    } else {
                        musicRepository.getTrackById(state.currentTrackId).first().also { track ->
                            if (track != null) {
                                _uiState.value = _uiState.value.copy(isFavorite = track.rating >= 4)
                            }
                        }
                    }
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
            }
        }

        startPositionUpdates()

        viewModelScope.launch {
            musicPlayerController.sleepTimerRemainingSeconds.collectLatest { remaining ->
                _uiState.value = _uiState.value.copy(sleepTimerRemainingSeconds = remaining)
            }
        }
    }

    private fun startPositionUpdates() {
        playbackUpdateJob?.cancel()
        playbackUpdateJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                _uiState.value = _uiState.value.copy(
                    currentPosition = musicPlayerController.getCurrentPosition(),
                    duration = musicPlayerController.getDuration()
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

    override fun onCleared() {
        super.onCleared()
        playbackUpdateJob?.cancel()
    }
}
