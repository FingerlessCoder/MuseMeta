package com.mymusicplayer.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SearchTab { ALL, TRACKS, ALBUMS, ARTISTS }

data class SearchResultsUiState(
    val isSearching: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val selectedTab: SearchTab = SearchTab.ALL,
    val currentQuery: String = ""
) {
    val hasResults: Boolean get() = tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
}

class SearchResultsViewModel(
    private val query: String,
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchResultsUiState())
    val uiState: StateFlow<SearchResultsUiState> = _uiState.asStateFlow()

    private val allAlbums = musicRepository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val allArtists = musicRepository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (query.isNotBlank()) {
            search(query)
        }
    }

    fun selectTab(tab: SearchTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun updateQuery(newQuery: String) {
        if (newQuery.isNotBlank()) {
            search(newQuery)
        }
    }

    private fun search(query: String) {
        _uiState.value = SearchResultsUiState(isSearching = true, selectedTab = _uiState.value.selectedTab, currentQuery = query)
        viewModelScope.launch {
            val q = query.lowercase()

            musicRepository.searchTracks(q).first { result ->
                _uiState.value = _uiState.value.copy(tracks = result, isSearching = false)
                true
            }

            val albumsSnapshot = allAlbums.first()
            val artistsSnapshot = allArtists.first()

            val matchedAlbums = albumsSnapshot.filter {
                it.title.lowercase().contains(q) ||
                    (it.albumArtist?.lowercase()?.contains(q) == true)
            }
            val matchedArtists = artistsSnapshot.filter {
                it.name.lowercase().contains(q)
            }

            val autoTab = when {
                matchedArtists.isNotEmpty() && matchedArtists.any { it.name.lowercase() == q } -> SearchTab.ARTISTS
                matchedAlbums.isNotEmpty() && matchedAlbums.any { it.title.lowercase() == q } -> SearchTab.ALBUMS
                else -> SearchTab.ALL
            }

            _uiState.value = _uiState.value.copy(
                albums = matchedAlbums,
                artists = matchedArtists,
                isSearching = false,
                selectedTab = autoTab
            )
        }
    }

    fun playTrack(track: Track) {
        musicPlayerController.initialize()
        val queue = _uiState.value.tracks
        val trackIndex = queue.indexOfFirst { it.id == track.id }
        if (trackIndex >= 0 && queue.size > 1) {
            musicPlayerController.playFromQueue(
                trackPaths = queue.map { it.filePath },
                startIndex = trackIndex,
                trackIds = queue.map { it.id },
                titles = queue.map { it.title },
                artists = queue.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                albumArtPaths = queue.map { it.album?.artPath }
            )
        } else {
            musicPlayerController.play(
                track.filePath, track.id,
                title = track.title,
                artist = track.artists.joinToString(" · ") { it.name },
                albumArtPath = track.album?.artPath
            )
        }
    }
}
