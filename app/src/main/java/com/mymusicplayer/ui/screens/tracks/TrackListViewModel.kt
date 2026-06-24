package com.mymusicplayer.ui.screens.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TrackListUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val sortMode: String = "name",
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isMultiSelectMode: Boolean = false,
    val selectedTrackIds: Set<Long> = emptySet(),
    val isShowingFilter: Boolean = false,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val minDuration: Long? = null,
    val maxDuration: Long? = null
)

class TrackListViewModel constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackListUiState())
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

    private val _sortMode = MutableStateFlow("name")
    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks: StateFlow<List<Track>> = _sortMode.flatMapLatest { sort ->
        musicRepository.getAllTracks(sort)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var autoScanTriggered = false

    init {
        viewModelScope.launch {
            tracks.collect { trackList ->
                val wasLoading = _uiState.value.isLoading
                _uiState.value = _uiState.value.copy(
                    tracks = trackList,
                    isLoading = false
                )
                if (trackList.isEmpty() && wasLoading && !autoScanTriggered) {
                    autoScanTriggered = true
                    try {
                        musicRepository.rescanLibrary().collect { }
                    } catch (_: Exception) {
                        // auto-scan failed (e.g., no permission yet)
                    }
                }
            }
        }
    }

    fun setSortMode(sort: String) {
        _sortMode.value = sort
        _uiState.value = _uiState.value.copy(sortMode = sort)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearchActive = query.isNotBlank()
        )
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            isSearchActive = false
        )
    }

    fun toggleMultiSelect() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isMultiSelectMode = !current.isMultiSelectMode,
            selectedTrackIds = emptySet()
        )
    }

    fun toggleTrackSelection(trackId: Long) {
        val current = _uiState.value
        val newSelection = if (trackId in current.selectedTrackIds) {
            current.selectedTrackIds - trackId
        } else {
            current.selectedTrackIds + trackId
        }
        _uiState.value = current.copy(selectedTrackIds = newSelection)
    }

    fun selectAll() {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedTrackIds = current.tracks.map { it.id }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = false,
            selectedTrackIds = emptySet()
        )
    }

    fun playTrack(track: Track) {
        musicPlayerController.initialize()
        musicPlayerController.play(track.filePath, track.id)
    }

    fun toggleFilter() {
        _uiState.value = _uiState.value.copy(
            isShowingFilter = !_uiState.value.isShowingFilter
        )
    }

    fun setSizeFilter(min: Long?, max: Long?) {
        _uiState.value = _uiState.value.copy(
            minSize = min,
            maxSize = max
        )
    }

    fun setDurationFilter(min: Long?, max: Long?) {
        _uiState.value = _uiState.value.copy(
            minDuration = min,
            maxDuration = max
        )
    }

    fun deleteSelected() {
        viewModelScope.launch {
            // Phase 3: implement delete
            _uiState.value = _uiState.value.copy(
                isMultiSelectMode = false,
                selectedTrackIds = emptySet()
            )
        }
    }

    fun addSelectedToPlaylist() {
        // Will be wired in Phase 2.6
    }

    fun getFilteredTracks(): List<Track> {
        val state = _uiState.value
        var result = state.tracks

        if (state.isSearchActive) {
            val query = state.searchQuery.lowercase()
            result = result.filter {
                it.title.lowercase().contains(query) ||
                it.artists.any { a -> a.name.lowercase().contains(query) } ||
                it.album?.title?.lowercase()?.contains(query) == true
            }
        }

        state.minSize?.let { min ->
            result = result.filter { it.fileSize >= min }
        }
        state.maxSize?.let { max ->
            result = result.filter { it.fileSize <= max }
        }

        state.minDuration?.let { min ->
            result = result.filter { it.duration >= min }
        }
        state.maxDuration?.let { max ->
            result = result.filter { it.duration <= max }
        }

        return result
    }
}
