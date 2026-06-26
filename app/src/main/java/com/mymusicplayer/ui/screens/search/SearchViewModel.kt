package com.mymusicplayer.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList()
) {
    val hasResults: Boolean get() = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
}

class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val allAlbums = musicRepository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allArtists = musicRepository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, isSearching = query.isNotBlank())
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300L)
            val q = query.lowercase()

            musicRepository.searchTracks(q).first { result ->
                _uiState.value = _uiState.value.copy(tracks = result, isSearching = false)
                true
            }

            val matchedAlbums = allAlbums.value.filter {
                it.title.lowercase().contains(q) ||
                (it.albumArtist?.lowercase()?.contains(q) == true)
            }
            val matchedArtists = allArtists.value.filter {
                it.name.lowercase().contains(q)
            }
            _uiState.value = _uiState.value.copy(
                albums = matchedAlbums,
                artists = matchedArtists,
                isSearching = false
            )
        }
    }

    fun clearSearch() {
        _uiState.value = SearchUiState()
        searchJob?.cancel()
    }

    fun playTrack(track: Track) {
        musicPlayerController.initialize()
        musicPlayerController.play(
            track.filePath, track.id,
            title = track.title,
            artist = track.artists.firstOrNull()?.name,
            albumArtPath = track.album?.artPath
        )
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            musicRepository.getTracksByAlbum(album.id).first { tracks ->
                if (tracks.isNotEmpty()) {
                    musicPlayerController.initialize()
                    musicPlayerController.playFromQueue(
                        trackPaths = tracks.map { it.filePath },
                        startIndex = 0,
                        trackIds = tracks.map { it.id },
                        titles = tracks.map { it.title },
                        artists = tracks.map { it.artists.firstOrNull()?.name },
                        albumArtPaths = tracks.map { it.album?.artPath }
                    )
                }
                true
            }
        }
    }
}
