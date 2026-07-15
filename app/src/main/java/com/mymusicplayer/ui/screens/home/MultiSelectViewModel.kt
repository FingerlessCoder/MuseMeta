package com.mymusicplayer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MultiSelectUiState(
    val allTracks: List<Track> = emptyList(),
    val selectedTrackIds: Set<Long> = emptySet(),
    val searchQuery: String = ""
) {
    val selectedTracks: List<Track>
        get() = allTracks.filter { it.id in selectedTrackIds }

    val filteredTracks: List<Track>
        get() = if (searchQuery.isBlank()) allTracks
        else allTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artists.any { a -> a.name.contains(searchQuery, ignoreCase = true) }
        }

    val allSelected: Boolean
        get() = allTracks.isNotEmpty() && selectedTrackIds.size == allTracks.size

    val favoriteIds: Set<Long>
        get() = allTracks.filter { it.rating >= 4 }.map { it.id }.toSet()

    val allSelectedAreFavorites: Boolean
        get() = selectedTrackIds.isNotEmpty() && selectedTrackIds.all { it in favoriteIds }
}

class MultiSelectViewModel(
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiSelectUiState())
    val uiState: StateFlow<MultiSelectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.getAllTracks("name").collect { tracks ->
                _uiState.value = _uiState.value.copy(allTracks = tracks)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleTrackSelection(trackId: Long) {
        val current = _uiState.value.selectedTrackIds
        _uiState.value = _uiState.value.copy(
            selectedTrackIds = if (trackId in current) current - trackId else current + trackId
        )
    }

    fun toggleSelectAll() {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedTrackIds = if (state.allSelected) emptySet() else state.allTracks.map { it.id }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedTrackIds = emptySet())
    }

    // ── Operations ──

    fun playNextSelected() {
        val tracks = _uiState.value.selectedTracks
        if (tracks.isEmpty()) return
        musicPlayerController.initialize()
        musicPlayerController.playNext(tracks)
        clearSelection()
    }

    fun addToQueueSelected() {
        val tracks = _uiState.value.selectedTracks
        if (tracks.isEmpty()) return
        musicPlayerController.initialize()
        musicPlayerController.addToQueue(tracks)
        clearSelection()
    }

    fun deleteSelectedTracks(onDone: () -> Unit) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedTrackIds.toList()
            for (trackId in ids) {
                musicRepository.deleteTrackById(trackId)
            }
            _uiState.value = _uiState.value.copy(selectedTrackIds = emptySet())
            onDone()
        }
    }

    fun toggleFavoriteSelected() {
        viewModelScope.launch {
            val state = _uiState.value
            val allFav = state.allSelectedAreFavorites
            val newRating = if (allFav) 0 else 5
            for (trackId in state.selectedTrackIds) {
                musicRepository.updateTrackRating(trackId, newRating)
            }
        }
    }

    fun shareSelected(context: android.content.Context) {
        val tracks = _uiState.value.selectedTracks
        if (tracks.isEmpty()) return
        val text = tracks.joinToString("\n") { track ->
            "${track.title} - ${track.artists.joinToString(", ") { it.name }}"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "${tracks.size} track${if (tracks.size != 1) "s" else ""}")
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share tracks"))
    }
}
