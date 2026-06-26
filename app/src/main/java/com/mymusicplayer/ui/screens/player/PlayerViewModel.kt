package com.mymusicplayer.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = 0,
    val isFavorite: Boolean = false,
    val selectedTab: Int = 0,
    val lyricsText: String? = null,
    val queueTracks: List<Track> = emptyList(),
    val sleepTimerMinutes: Int = 0
)

class PlayerViewModel constructor(
    private val musicPlayerController: MusicPlayerController,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var playbackUpdateJob: kotlinx.coroutines.Job? = null
    private var loadedQueueIds: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            musicPlayerController.playbackState.collect { state ->
                val currentTrack = if (state.currentTrackId != null &&
                    state.currentTrackId != _uiState.value.currentTrack?.id) {
                    musicRepository.getTrackById(state.currentTrackId).first().also { track ->
                        if (track != null) {
                            _uiState.value = _uiState.value.copy(isFavorite = track.rating >= 4)
                        }
                    }
                } else {
                    _uiState.value.currentTrack
                }

                val ids = musicPlayerController.getCurrentTrackIds()
                val queueTracks = if (ids != loadedQueueIds) {
                    loadedQueueIds = ids
                    if (ids.isNotEmpty()) {
                        ids.mapNotNull { id -> musicRepository.getTrackById(id).first() }
                    } else {
                        emptyList()
                    }
                } else {
                    _uiState.value.queueTracks
                }

                _uiState.value = _uiState.value.copy(
                    currentTrack = currentTrack,
                    isPlaying = state.isPlaying,
                    currentPosition = state.currentPosition,
                    duration = state.duration,
                    queueSize = state.queueSize,
                    queueIndex = state.queueIndex,
                    shuffleMode = state.shuffleMode,
                    repeatMode = state.repeatMode,
                    queueTracks = queueTracks,
                    sleepTimerMinutes = _uiState.value.sleepTimerMinutes
                )
            }
        }

        startPositionUpdates()
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

    fun toggleShuffle() {
        musicPlayerController.setShuffleMode(!_uiState.value.shuffleMode)
    }

    fun cycleRepeatMode() {
        val nextMode = when (_uiState.value.repeatMode) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        musicPlayerController.setRepeatMode(nextMode)
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
        if (minutes > 0) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(minutes * 60_000L)
                musicPlayerController.togglePlayPause()
                _uiState.value = _uiState.value.copy(sleepTimerMinutes = 0)
            }
        }
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
