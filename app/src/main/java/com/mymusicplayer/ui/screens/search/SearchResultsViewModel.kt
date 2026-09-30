package com.mymusicplayer.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SearchTab { ALL, TRACKS, ALBUMS, ARTISTS }

data class SearchResultsUiState(
    val isSearching: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val selectedTab: SearchTab = SearchTab.ALL,
    val currentQuery: String = "",
    val error: String? = null
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val allArtists = musicRepository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    private var manualTab = false

    init {
        if (query.isNotBlank()) {
            search(query)
        }
    }

    fun selectTab(tab: SearchTab) {
        manualTab = true
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun updateQuery(newQuery: String) {
        if (newQuery.isNotBlank()) {
            search(newQuery)
        }
    }

    private fun search(query: String) {
        searchJob?.cancel()
        _uiState.value = SearchResultsUiState(
            isSearching = true,
            selectedTab = _uiState.value.selectedTab,
            currentQuery = query
        )
        searchJob = viewModelScope.launch {
            try {
                val q = query.lowercase()

                val albumsSnapshot = allAlbums.first()
                val artistsSnapshot = allArtists.first()

                val matchedAlbums = albumsSnapshot.filter {
                    it.title.lowercase().contains(q) ||
                        (it.albumArtist?.lowercase()?.contains(q) == true)
                }
                val matchedArtists = artistsSnapshot.filter {
                    it.name.lowercase().contains(q)
                }

                val targetTab = if (manualTab) {
                    _uiState.value.selectedTab
                } else {
                    when {
                        matchedArtists.isNotEmpty() && matchedArtists.any { it.name.lowercase() == q } -> SearchTab.ARTISTS
                        matchedAlbums.isNotEmpty() && matchedAlbums.any { it.title.lowercase() == q } -> SearchTab.ALBUMS
                        else -> SearchTab.ALL
                    }
                }

                _uiState.value = _uiState.value.copy(
                    albums = matchedAlbums,
                    artists = matchedArtists,
                    selectedTab = targetTab
                )

                musicRepository.searchTracks(q).collect { result ->
                    _uiState.value = _uiState.value.copy(tracks = result, isSearching = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSearching = false, error = e.message)
            }
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
