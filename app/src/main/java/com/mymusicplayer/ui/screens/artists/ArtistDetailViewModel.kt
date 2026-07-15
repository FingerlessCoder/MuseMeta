package com.mymusicplayer.ui.screens.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true
)

class ArtistDetailViewModel(
    private val artistId: Long,
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.getArtistById(artistId).collect { artist ->
                _uiState.value = _uiState.value.copy(artist = artist)
            }
        }
        viewModelScope.launch {
            musicRepository.getTracksForArtist(artistId).collect { tracks ->
                val albums = tracks.mapNotNull { it.album }.distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    tracks = tracks,
                    albums = albums,
                    isLoading = false
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
