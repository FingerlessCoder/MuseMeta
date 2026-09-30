package com.mymusicplayer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.data.db.entity.PlaylistEntity
import com.mymusicplayer.data.network.ArtistImageFetcher
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Playlist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import com.mymusicplayer.ui.components.ScrollbarMath
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class HomeTab { Tracks, Albums, Artists }

data class HomeUiState(
    val isLoading: Boolean = true,
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
    private val settingsDataStore: SettingsDataStore,
    private val artistImageFetcher: ArtistImageFetcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Single source of truth for which tab is shown and how each tab is sorted.
     *
     * Sort state is per tab, not global: one shared (mode, dir) meant that
     * switching to Albums with the Tracks tab on `play_count` left Albums showing
     * a sort it has no notion of, and the index bar then bucketed the list by a
     * different field than the list was ordered by. Each tab now remembers its
     * own mode, seeded from the persisted DEFAULT_SORT clamped to the modes that
     * tab supports, so a tab can never land on a mode it cannot render.
     *
     * Seeded/updated by DataStore DEFAULT_SORT (distinctUntilChanged, so
     * unrelated prefs edits don't rebuild lists) and by the Home sort menu
     * (session-only, not persisted). The main combine below is the ONLY writer
     * of sorted lists — no fire-and-forget refresh* collectors.
     */
    private val sortState = MutableStateFlow(HomeSortState())

    private var lastArtMapTracksKey: List<Long> = emptyList()
    private var lastArtMapArtistsKey: List<Long> = emptyList()
    private var lastArtMapVersion: Int = -1
    private var lastArtMap: Map<Long, String?> = emptyMap()
    private var avatarPrefetchStarted = false

    private fun buildArtistArtMap(
        artists: List<Artist>,
        tracks: List<Track>,
        version: Int = lastArtMapVersion
    ): Map<Long, String?> {
        val tracksKey = tracks.map { it.id }
        val artistsKey = artists.map { it.id }
        if (tracksKey == lastArtMapTracksKey && artistsKey == lastArtMapArtistsKey && version == lastArtMapVersion) {
            return lastArtMap
        }
        val map = artists.associate { artist ->
            val spotifyFile = artistImageFetcher.getArtistArtFile(artist.id)
            val artPath = if (spotifyFile.exists()) spotifyFile.absolutePath
            else tracks
                .firstOrNull { track -> track.artists.any { it.id == artist.id } && track.album?.artPath != null }
                ?.album?.artPath
            artist.id to artPath
        }
        lastArtMapTracksKey = tracksKey
        lastArtMapArtistsKey = artistsKey
        lastArtMapVersion = version
        lastArtMap = map
        return map
    }

    /**
     * Recomputes the derived (filtered / artist-art) fields from the base
     * fields. All state mutations must pass through this so the stored derived
     * lists stay consistent and are never stale after a partial copy().
     */
    private fun HomeUiState.withDerived(): HomeUiState {
        val genre = genreFilter
        return copy(
            filteredTracks = when {
                genre != null -> tracks.filter { (it.genre ?: "Unknown").equals(genre, ignoreCase = true) }
                else -> tracks
            },
            filteredAlbums = albums,
            filteredArtists = artists,
            artistArtMap = buildArtistArtMap(artists, tracks)
        )
    }

    private fun parseSortRaw(raw: String): Pair<String, String> =
        if (raw.endsWith("_desc")) raw.removeSuffix("_desc") to "desc" else raw to "asc"

    init {
        viewModelScope.launch {
            settingsDataStore.defaultSort.distinctUntilChanged().collect { raw ->
                val (mode, dir) = parseSortRaw(raw)
                sortState.update { it.copy(defaultSort = mode to dir) }
            }
        }
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
                content to scanDone
            }.combine(sortState) { contentAndScan: Pair<CombinedContent, Boolean>, sort: HomeSortState ->
                val (content, scanDone) = contentAndScan
                Triple(content, scanDone, sort)
            }.combine(artistImageFetcher.updates) { contentScanSort: Triple<CombinedContent, Boolean, HomeSortState>, artVersion: Int ->
                val (content, scanDone, sort) = contentScanSort
                val hasContent = content.tracks.isNotEmpty() || content.albums.isNotEmpty()
                val genre = _uiState.value.genreFilter
                // Each list is sorted by ITS OWN tab's mode, not by whichever
                // mode happens to be on screen.
                val tracks = sortTracks(content.tracks, sort.effective(HomeTab.Tracks))
                val albumTrackCounts = content.tracks.groupingBy { it.album?.id }.eachCount()
                val albums = sortAlbums(content.albums, sort.effective(HomeTab.Albums), albumTrackCounts)
                val artists = sortArtists(content.artists, sort.effective(HomeTab.Artists))
                // What the UI shows in the sort chip/arrow is the current tab's.
                val (mode, dir) = sort.effective(sort.tab)
                val filteredTracks = when {
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
                        .take(256),
                    sortMode = mode,
                    sortDir = dir,
                    selectedTab = sort.tab,
                    genreFilter = genre,
                    filteredTracks = filteredTracks,
                    filteredAlbums = albums,
                    filteredArtists = artists,
                    artistArtMap = buildArtistArtMap(artists, tracks, artVersion)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
        startAvatarPrefetch()
    }

    fun selectTab(tab: HomeTab) {
        // Routed through sortState (not a plain copy()) so the combine re-runs
        // and the sort chip/arrow switch to the newly selected tab's mode.
        sortState.update { it.copy(tab = tab) }
    }

    fun setSortMode(sort: String) {
        sortState.update { state ->
            val (mode, dir) = state.effective(state.tab)
            if (sort !in legalSortModes(state.tab)) state
            else state.copy(overrides = state.overrides + (state.tab to (sort to dir)))
        }
    }

    fun setSortDir(dir: String) {
        sortState.update { state ->
            val (mode, _) = state.effective(state.tab)
            state.copy(overrides = state.overrides + (state.tab to (mode to dir)))
        }
    }

    fun setGenreFilter(genre: String?) {
        _uiState.value = _uiState.value.copy(genreFilter = genre).withDerived()
    }

    private fun startAvatarPrefetch() {
        if (avatarPrefetchStarted) return
        avatarPrefetchStarted = true
        viewModelScope.launch {
            val artists = try {
                musicRepository.getAllArtists().first { it.isNotEmpty() }
            } catch (_: Exception) {
                return@launch
            }
            for (artist in artists) {
                try {
                    artistImageFetcher.fetchArtistImage(artist.name, artist.id)
                } catch (_: Exception) {
                }
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

/**
 * Sort state, kept per tab.
 *
 * [overrides] holds what the user picked in each tab this session; anything they
 * have not touched falls back to the persisted [defaultSort], clamped to the
 * modes [tab] actually supports. One object instead of separate flows so
 * switching tabs and changing the sort are a single emission — the combine that
 * produces the sorted lists then always sees them together.
 */
private data class HomeSortState(
    val tab: HomeTab = HomeTab.Tracks,
    val overrides: Map<HomeTab, Pair<String, String>> = emptyMap(),
    val defaultSort: Pair<String, String> = "name" to "asc"
) {
    fun effective(tab: HomeTab): Pair<String, String> =
        overrides[tab] ?: run {
            val (mode, dir) = defaultSort
            (if (mode in legalSortModes(tab)) mode else fallbackSortMode(tab)) to dir
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
private fun sortTracks(tracks: List<Track>, sort: Pair<String, String>): List<Track> {
    val (mode, dir) = sort
    val sorted: List<Track> = when (mode) {
        "name" -> tracks.sortedWith(compareBy({ it.title.lowercase() }, { it.id }))
        "date_added" -> tracks.sortedWith(compareBy({ it.dateAdded }, { it.id }))
        "play_count" -> tracks.sortedWith(compareBy({ it.playCount }, { it.id }))
        "duration" -> tracks.sortedWith(compareBy({ it.duration }, { it.id }))
        "rating" -> tracks.sortedWith(compareBy({ it.rating }, { it.id }))
        "year" -> tracks.sortedWith(compareByDescending<Track> { it.year ?: 0 }.thenBy { it.id })
        "genre" -> tracks.sortedWith(compareBy({ it.genre ?: "" }, { it.id }))
        "artist" -> tracks.sortedWith(compareBy({ it.artists.firstOrNull()?.name?.lowercase() ?: "" }, { it.id }))
        "album" -> tracks.sortedWith(compareBy({ it.album?.title?.lowercase() ?: "" }, { it.id }))
        else -> tracks.sortedBy { it.id }
    }
    return if (dir == "desc") sorted.reversed() else sorted
}

/**
 * In-memory sort for albums.
 *
 * The two alphabetical modes (the ones that get an index bar) go through
 * [ScrollbarMath.indexLetterComparator] rather than a plain lowercase compare.
 * A code-point compare scatters everything the bar calls `#` — `(` before `A`,
 * digits before `M`, CJK after `Z` — so `(g)i-dle` landed above `A` while the
 * bar's single `#` sat at the bottom, and the bar's jump targets no longer
 * matched the list. Grouping those names into one trailing bucket keeps list
 * order and bar order the same sequence.
 */
private fun sortAlbums(
    albums: List<Album>,
    sort: Pair<String, String>,
    trackCounts: Map<Long?, Int> = emptyMap()
): List<Album> {
    val (mode, dir) = sort
    val descending = dir == "desc"
    return when (mode) {
        "year" -> albums.sortedWith(
            orderBy<Album, Int>({ it.year ?: 0 }, descending).thenBy { it.id }
        )
        "track_count" -> albums.sortedWith(
            orderBy<Album, Int>({ trackCounts[it.id] ?: it.trackCount }, descending)
                .thenBy { it.id }
        )
        else -> albums.sortedWith(
            indexOrder(name = { it.indexKey(mode) }, id = { it.id }, descending = descending)
        )
    }
}

private fun sortArtists(artists: List<Artist>, sort: Pair<String, String>): List<Artist> {
    val (mode, dir) = sort
    return artists.sortedWith(
        indexOrder(name = { it.indexKey(mode) }, id = { it.id }, descending = dir == "desc")
    )
}

/**
 * Orders by [selector] in the direction [descending] asks for.
 *
 * The direction is baked into the comparator rather than applied with
 * `reversed()` on the finished list, because the alphabetical modes need a
 * direction-aware comparator of their own: a blanket reverse would also move the
 * `#` bucket to the top, away from where the index bar draws it.
 */
private fun <T, K : Comparable<K>> orderBy(
    selector: (T) -> K,
    descending: Boolean
): Comparator<T> =
    if (descending) compareByDescending(selector) else compareBy(selector)

/**
 * Orders by the name the alphabet index bar buckets on, with `id` as the
 * tiebreaker so equal names never reorder between emissions. See
 * [ScrollbarMath.indexLetterComparator] for why this is not a plain string
 * compare, and why [descending] goes into the comparator instead of a blanket
 * `reversed()`.
 */
private fun <T> indexOrder(
    name: (T) -> String,
    id: (T) -> Long,
    descending: Boolean
): Comparator<T> = compareBy(ScrollbarMath.indexLetterComparator(descending), name)
    .thenBy(id)
