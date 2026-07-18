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
    val sortMode: String = "name",
    val sortDir: String = "asc",
    val genreFilter: String? = null,
    // Pre-computed from the fields above (not getters) so they aren't
    // re-evaluated on every UI recomposition during scroll.
    val filteredTracks: List<Track> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val filteredArtists: List<Artist> = emptyList(),
    val artistArtMap: Map<Long, String?> = emptyMap()
) {
    val trackCount: Int get() = tracks.size
    val albumCount: Int get() = albums.size
    val artistCount: Int get() = artists.size
    val favoriteCount: Int get() = favoriteTracks.size
    val playlistCount: Int get() = playlists.size
    val recentlyPlayedCount: Int get() = recentlyPlayed.size
}

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Recomputes the derived (filtered / artist-art) fields from the base
     * fields. All state mutations must pass through this so the stored derived
     * lists stay consistent and are never stale after a partial copy().
     */
    private fun HomeUiState.withDerived(): HomeUiState {
        val query = searchQuery
        val genre = genreFilter
        return copy(
            filteredTracks = when {
                query.isNotBlank() -> tracks.filter { it.title.contains(query, ignoreCase = true) }
                genre != null -> tracks.filter { (it.genre ?: "Unknown").equals(genre, ignoreCase = true) }
                else -> tracks
            },
            filteredAlbums = if (query.isBlank()) albums
                else albums.filter { it.title.contains(query, ignoreCase = true) },
            filteredArtists = if (query.isBlank()) artists
                else artists.filter { it.name.contains(query, ignoreCase = true) },
            artistArtMap = artists.associate { artist ->
                val artPath = tracks
                    .firstOrNull { track -> track.artists.any { it.id == artist.id } && track.album?.artPath != null }
                    ?.album?.artPath
                artist.id to artPath
            }
        )
    }

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
                val query = _uiState.value.searchQuery
                val genre = _uiState.value.genreFilter
                val tracks = sortTracks(content.tracks, _uiState.value.sortMode, _uiState.value.sortDir)
                val albums = content.albums
                val artists = content.artists
                val filteredTracks = when {
                    query.isNotBlank() -> tracks.filter { it.title.contains(query, ignoreCase = true) }
                    genre != null -> tracks.filter { (it.genre ?: "Unknown").equals(genre, ignoreCase = true) }
                    else -> tracks
                }
                HomeUiState(
                    isLoading = !hasContent && !scanDone,
                    tracks = tracks,
                    albums = albums,
                    artists = artists,
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
                    recentlyPlayed = tracks
                        .filter { it.lastPlayed != null && it.lastPlayed > 0L }
                        .sortedByDescending { it.lastPlayed }
                        .take(10),
                    sortMode = _uiState.value.sortMode,
                    selectedTab = _uiState.value.selectedTab,
                    searchQuery = query,
                    genreFilter = genre,
                    filteredTracks = filteredTracks,
                    filteredAlbums = if (query.isBlank()) albums
                        else albums.filter { it.title.contains(query, ignoreCase = true) },
                    filteredArtists = if (query.isBlank()) artists
                        else artists.filter { it.name.contains(query, ignoreCase = true) },
                    artistArtMap = artists.associate { artist ->
                        val artPath = tracks
                            .firstOrNull { track -> track.artists.any { it.id == artist.id } && track.album?.artPath != null }
                            ?.album?.artPath
                        artist.id to artPath
                    }
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        viewModelScope.launch {
            settingsDataStore.defaultSort.collect { raw ->
                val (mode, dir) = if (raw.endsWith("_desc")) {
                    raw.removeSuffix("_desc") to "desc"
                } else {
                    raw to "asc"
                }
                _uiState.value = _uiState.value.copy(sortMode = mode, sortDir = dir)
                applySortForTab(_uiState.value.selectedTab)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query).withDerived()
    }

    fun selectTab(tab: HomeTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        applySortForTab(tab)
    }

    private fun applySortForTab(tab: HomeTab) {
        val sort = _uiState.value.sortMode
        val dir = _uiState.value.sortDir
        when (tab) {
            HomeTab.Albums -> refreshAlbums(sort, dir)
            HomeTab.Artists -> refreshArtists(dir)
            else -> refreshTracks(sort, dir)
        }
    }

    fun setSortMode(sort: String) {
        _uiState.value = _uiState.value.copy(sortMode = sort)
        applySortForTab(_uiState.value.selectedTab)
    }

    fun setSortDir(dir: String) {
        _uiState.value = _uiState.value.copy(sortDir = dir)
        applySortForTab(_uiState.value.selectedTab)
    }

    fun setGenreFilter(genre: String?) {
        _uiState.value = _uiState.value.copy(genreFilter = genre).withDerived()
    }

    private fun refreshTracks(sort: String, dir: String) {
        val key = if (dir == "desc") "${sort}_desc" else sort
        viewModelScope.launch {
            musicRepository.getAllTracks(key).first { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks).withDerived()
                true
            }
        }
    }

    private fun refreshAlbums(sort: String, dir: String) {
        val key = if (dir == "desc" && sort in setOf("title", "year", "track_count")) "${sort}_desc"
        else if (sort == "name") "title"
        else sort
        viewModelScope.launch {
            musicRepository.getAllAlbums(key).first { albums ->
                _uiState.value = _uiState.value.copy(albums = albums).withDerived()
                true
            }
        }
    }

    private fun refreshArtists(dir: String) {
        viewModelScope.launch {
            musicRepository.getAllArtists(if (dir == "desc") "name_desc" else "name").first { artists ->
                _uiState.value = _uiState.value.copy(artists = artists).withDerived()
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

    fun playRandomAlbum(onNavigateToPlayer: () -> Unit = {}) {
        val albums = uiState.value.albums
        if (albums.isEmpty()) return
        val album = albums[kotlin.random.Random.nextInt(albums.size)]
        playAlbum(album)
        onNavigateToPlayer()
    }

    fun playRandomArtist(onNavigateToPlayer: () -> Unit = {}) {
        val artists = uiState.value.artists
        if (artists.isEmpty()) return
        val artist = artists[kotlin.random.Random.nextInt(artists.size)]
        playArtistTracks(artist)
        onNavigateToPlayer()
    }

    fun playRandomForCurrentTab(onNavigateToPlayer: () -> Unit = {}) {
        when (uiState.value.selectedTab) {
            HomeTab.Albums -> playRandomAlbum(onNavigateToPlayer)
            HomeTab.Artists -> playRandomArtist(onNavigateToPlayer)
            else -> {
                playRandom()
                onNavigateToPlayer()
            }
        }
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

/**
 * In-memory sort for tracks. The init combine block uses a hardcoded query
 * (getAllTracks("name")) so Room emits tracks sorted by title on every DB
 * change. This re-sorts them according to the user's current preference so
 * the sort mode doesn't reset when a track advances and Room re-emits.
 */
private fun sortTracks(tracks: List<Track>, mode: String, dir: String): List<Track> {
    val sorted: List<Track> = when (mode) {
        "name" -> tracks.sortedBy { it.title.lowercase() }
        "date_added" -> tracks.sortedBy { it.dateAdded }
        "play_count" -> tracks.sortedBy { it.playCount }
        "duration" -> tracks.sortedBy { it.duration }
        "rating" -> tracks.sortedBy { it.rating }
        "year" -> tracks.sortedByDescending { it.year ?: 0 }
        "genre" -> tracks.sortedBy { it.genre ?: "" }
        "artist" -> tracks.sortedBy { it.artists.firstOrNull()?.name?.lowercase() ?: "" }
        "album" -> tracks.sortedBy { it.album?.title?.lowercase() ?: "" }
        else -> tracks
    }
    return if (dir == "desc") sorted.reversed() else sorted
}
