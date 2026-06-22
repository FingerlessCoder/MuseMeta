package com.mymusicplayer.ui.screens.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

data class AlbumDetailUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(AlbumListUiState())
    val listState: StateFlow<AlbumListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(AlbumDetailUiState())
    val detailState: StateFlow<AlbumDetailUiState> = _detailState.asStateFlow()

    val albums = musicRepository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            albums.collect { albumList ->
                _listState.value = AlbumListUiState(
                    albums = albumList,
                    isLoading = false
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _listState.value = _listState.value.copy(
            searchQuery = query,
            isSearchActive = query.isNotBlank()
        )
    }

    fun clearSearch() {
        _listState.value = _listState.value.copy(
            searchQuery = "",
            isSearchActive = false
        )
    }

    fun getFilteredAlbums(): List<Album> {
        val state = _listState.value
        if (!state.isSearchActive) return state.albums
        val query = state.searchQuery.lowercase()
        return state.albums.filter {
            it.title.lowercase().contains(query) ||
            (it.albumArtist?.lowercase()?.contains(query) == true)
        }
    }

    fun loadAlbumDetail(albumId: Long) {
        viewModelScope.launch {
            _detailState.value = AlbumDetailUiState(isLoading = true)
            musicRepository.getAlbumById(albumId).collect { album ->
                _detailState.value = _detailState.value.copy(album = album)
            }
        }
        viewModelScope.launch {
            musicRepository.getTracksByAlbum(albumId).collect { trackList ->
                _detailState.value = _detailState.value.copy(
                    tracks = trackList,
                    isLoading = false
                )
            }
        }
    }
}
