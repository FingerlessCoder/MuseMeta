package com.mymusicplayer.ui.screens.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val totalDuration: Long = 0
)

class AlbumDetailViewModel(
    private val albumId: Long,
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.getAlbumById(albumId).collect { album ->
                _uiState.value = _uiState.value.copy(album = album)
            }
        }
        viewModelScope.launch {
            musicRepository.getTracksByAlbum(albumId).collect { tracks ->
                val totalDuration = tracks.sumOf { it.duration }
                _uiState.value = _uiState.value.copy(
                    tracks = tracks,
                    isLoading = false,
                    totalDuration = totalDuration
                )
            }
        }
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            musicPlayerController.initialize()
            val tracks = _uiState.value.tracks
            val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            musicPlayerController.playFromQueue(
                trackPaths = tracks.map { it.filePath },
                startIndex = index,
                trackIds = tracks.map { it.id },
                titles = tracks.map { it.title },
                artists = tracks.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                albumArtPaths = tracks.map { it.album?.artPath }
            )
        }
    }

    fun playAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isNotEmpty()) {
            viewModelScope.launch {
                musicPlayerController.initialize()
                musicPlayerController.playFromQueue(
                    trackPaths = tracks.map { it.filePath },
                    startIndex = 0,
                    trackIds = tracks.map { it.id },
                    titles = tracks.map { it.title },
                    artists = tracks.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                    albumArtPaths = tracks.map { it.album?.artPath }
                )
            }
        }
    }

    fun updateAlbumArt(imageBytes: ByteArray, applyToAll: Boolean = true) {
        viewModelScope.launch {
            val tracks = _uiState.value.tracks
            val targetTrack = tracks.firstOrNull() ?: return@launch
            val result = musicRepository.updateAlbumArt(targetTrack.id, imageBytes, applyToAll)
            if (result is com.mymusicplayer.domain.repository.WriteResult.Success) {
                // Refresh album data to reflect new art path
                _uiState.value = _uiState.value.copy() // triggers recomposition via flows
            }
        }
    }

    fun shuffleAll() {
        val tracks = _uiState.value.tracks.toMutableList()
        if (tracks.isNotEmpty()) {
            tracks.shuffle()
            viewModelScope.launch {
                musicPlayerController.initialize()
                musicPlayerController.playFromQueue(
                    trackPaths = tracks.map { it.filePath },
                    startIndex = 0,
                    trackIds = tracks.map { it.id },
                    titles = tracks.map { it.title },
                    artists = tracks.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                    albumArtPaths = tracks.map { it.album?.artPath }
                )
            }
        }
    }
}
