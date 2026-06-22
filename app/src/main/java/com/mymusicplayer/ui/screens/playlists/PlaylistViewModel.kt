package com.mymusicplayer.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.data.db.entity.PlaylistEntity
import com.mymusicplayer.data.db.entity.PlaylistEntryEntity
import com.mymusicplayer.domain.model.Playlist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(PlaylistListUiState())
    val listState: StateFlow<PlaylistListUiState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(PlaylistDetailUiState())
    val detailState: StateFlow<PlaylistDetailUiState> = _detailState.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    val playlists = playlistDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                isSmart = entity.isSmart,
                smartRuleJson = entity.smartRuleJson
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            playlists.collect { list ->
                _listState.value = PlaylistListUiState(
                    playlists = list,
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

    fun getFilteredPlaylists(): List<Playlist> {
        val state = _listState.value
        if (!state.isSearchActive) return state.playlists
        val query = state.searchQuery.lowercase()
        return state.playlists.filter {
            it.name.lowercase().contains(query) ||
            (it.description?.lowercase()?.contains(query) == true)
        }
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    fun hideCreateDialog() {
        _showCreateDialog.value = false
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistDao.createPlaylist(
                PlaylistEntity(name = name)
            )
            _showCreateDialog.value = false
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylistById(playlistId)
        }
    }

    fun loadPlaylistDetail(playlistId: Long) {
        viewModelScope.launch {
            _detailState.value = PlaylistDetailUiState(isLoading = true)
            playlistDao.getPlaylistById(playlistId).collect { entity ->
                if (entity != null) {
                    _detailState.value = _detailState.value.copy(
                        playlist = Playlist(
                            id = entity.id,
                            name = entity.name,
                            description = entity.description,
                            isSmart = entity.isSmart,
                            smartRuleJson = entity.smartRuleJson
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            playlistDao.getTracksInPlaylist(playlistId).collect { trackEntities ->
                // Convert entities to domain models
                val tracks = trackEntities.map { te ->
                    Track(
                        id = te.id,
                        title = te.title,
                        artists = emptyList(),
                        album = null,
                        duration = te.duration,
                        trackNumber = te.trackNumber,
                        discNumber = te.discNumber,
                        year = te.year,
                        genre = te.genre,
                        comment = te.comment,
                        filePath = te.filePath,
                        fileSize = te.fileSize,
                        format = te.format,
                        dateAdded = te.dateAdded,
                        lastPlayed = te.lastPlayed,
                        playCount = te.playCount,
                        rating = te.rating,
                        lyricsPath = te.lyricsPath,
                        rawArtistTag = te.rawArtistTag
                    )
                }
                _detailState.value = _detailState.value.copy(
                    tracks = tracks,
                    isLoading = false
                )
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.clearPlaylist(playlistId)
        }
    }
}
