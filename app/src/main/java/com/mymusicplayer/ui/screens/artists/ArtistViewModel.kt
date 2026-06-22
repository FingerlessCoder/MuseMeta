package com.mymusicplayer.ui.screens.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.domain.model.Artist
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

data class ArtistListUiState(
    val artists: List<Artist> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(ArtistListUiState())
    val listState: StateFlow<ArtistListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(ArtistDetailUiState())
    val detailState: StateFlow<ArtistDetailUiState> = _detailState.asStateFlow()

    val artists = musicRepository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            artists.collect { artistList ->
                _listState.value = ArtistListUiState(
                    artists = artistList,
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

    fun getFilteredArtists(): List<Artist> {
        val state = _listState.value
        if (!state.isSearchActive) return state.artists
        val query = state.searchQuery.lowercase()
        return state.artists.filter {
            it.name.lowercase().contains(query)
        }
    }

    fun loadArtistDetail(artistId: Long) {
        viewModelScope.launch {
            _detailState.value = ArtistDetailUiState(isLoading = true)
            musicRepository.getArtistById(artistId).collect { artist ->
                _detailState.value = _detailState.value.copy(artist = artist)
            }
        }
        viewModelScope.launch {
            musicRepository.getTracksForArtist(artistId).collect { trackList ->
                _detailState.value = _detailState.value.copy(
                    tracks = trackList,
                    isLoading = false
                )
            }
        }
    }
}
