package com.mymusicplayer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.data.db.entity.PlaylistEntity
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Playlist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class HomeTab { Tracks, Albums, Artists }

data class HomeUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val selectedTab: HomeTab = HomeTab.Tracks,
    val sortMode: String = "name"
) {
    val trackCount: Int get() = tracks.size
    val albumCount: Int get() = albums.size
    val artistCount: Int get() = artists.size
    val favoriteCount: Int get() = favoriteTracks.size
    val playlistCount: Int get() = playlists.size
    val recentlyPlayedCount: Int get() = recentlyPlayed.size

    val filteredTracks: List<Track>
        get() = if (searchQuery.isBlank()) tracks
        else tracks.filter { it.title.contains(searchQuery, ignoreCase = true) }

    val filteredAlbums: List<Album>
        get() = if (searchQuery.isBlank()) albums
        else albums.filter { it.title.contains(searchQuery, ignoreCase = true) }

    val filteredArtists: List<Artist>
        get() = if (searchQuery.isBlank()) artists
        else artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
}

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine 5 content flows, then chain with scanDone flag.
            // Separating the 6th flow avoids Kotlin type inference
            // issues with the 6-param combine overload.
            combine(
                musicRepository.getAllTracks("name"),
                musicRepository.getAllAlbums(),
                musicRepository.getAllArtists(),
                musicRepository.getFavoriteTracks(),
                playlistDao.getAllPlaylists()
            ) { tracks: List<Track>, albums: List<Album>, artists: List<Artist>, favorites: List<Track>, playlistEntities: List<PlaylistEntity> ->
                CombinedContent(tracks, albums, artists, favorites, playlistEntities)
            }.combine(settingsDataStore.scanCompletedOnce) { content: CombinedContent, scanDone: Boolean ->
                val hasContent = content.tracks.isNotEmpty() || content.albums.isNotEmpty()
                HomeUiState(
                    isLoading = !hasContent && !scanDone,
                    tracks = content.tracks,
                    albums = content.albums,
                    artists = content.artists,
                    favoriteTracks = content.favorites,
                    playlists = content.playlistEntities.map { entity ->
                        Playlist(
                            id = entity.id,
                            name = entity.name,
                            description = entity.description,
                            isSmart = entity.isSmart,
                            smartRuleJson = entity.smartRuleJson
                        )
                    },
                    recentlyPlayed = content.tracks
                        .filter { it.lastPlayed != null && it.lastPlayed > 0L }
                        .sortedByDescending { it.lastPlayed }
                        .take(10),
                    sortMode = _uiState.value.sortMode,
                    selectedTab = _uiState.value.selectedTab,
                    searchQuery = _uiState.value.searchQuery
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectTab(tab: HomeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun setSortMode(sort: String) {
        _uiState.value = _uiState.value.copy(sortMode = sort)
        viewModelScope.launch {
            musicRepository.getAllTracks(sort).first { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
                true
            }
        }
    }

    fun playTrack(track: Track) {
        musicPlayerController.initialize()
        val allTracks = uiState.value.tracks
        val trackIndex = allTracks.indexOfFirst { it.id == track.id }
        if (trackIndex >= 0 && allTracks.size > 1) {
            musicPlayerController.playFromQueue(
                trackPaths = allTracks.map { it.filePath },
                startIndex = trackIndex,
                trackIds = allTracks.map { it.id },
                titles = allTracks.map { it.title },
                artists = allTracks.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                albumArtPaths = allTracks.map { it.album?.artPath }
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

    fun playRandom() {
        val allTracks = uiState.value.tracks
        if (allTracks.isEmpty()) return
        val randomIndex = kotlin.random.Random.nextInt(allTracks.size)
        playTrack(allTracks[randomIndex])
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
                        artists = tracks.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                        albumArtPaths = tracks.map { it.album?.artPath }
                    )
                }
                true
            }
        }
    }

    fun playArtistTracks(artist: Artist) {
        viewModelScope.launch {
            musicRepository.getTracksForArtist(artist.id).first { tracks ->
                if (tracks.isNotEmpty()) {
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
                true
            }
        }
    }

}

/** Internal holder for the 5-way combine used in HomeViewModel init. */
private data class CombinedContent(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val favorites: List<Track>,
    val playlistEntities: List<PlaylistEntity>
)
