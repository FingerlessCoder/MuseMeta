package com.mymusicplayer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.preferences.SettingsDataStore
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
        get() = filteredTracks.isNotEmpty() && selectedTrackIds.containsAll(filteredTracks.map { it.id })

    val favoriteIds: Set<Long>
        get() = allTracks.filter { it.rating >= 4 }.map { it.id }.toSet()

    val allSelectedAreFavorites: Boolean
        get() = selectedTrackIds.isNotEmpty() && selectedTrackIds.all { it in favoriteIds }
}

class MultiSelectViewModel(
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultiSelectUiState())
    val uiState: StateFlow<MultiSelectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = settingsDataStore.defaultSort.first()
            val sortKey = if (raw.endsWith("_desc")) raw else "$raw"
            musicRepository.getAllTracks(sortKey).collect { tracks ->
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
        val target = state.filteredTracks
        _uiState.value = state.copy(
            selectedTrackIds = if (state.allSelected) emptySet() else target.map { it.id }.toSet()
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
        val authority = "${context.packageName}.fileprovider"
        if (tracks.size == 1) {
            val track = tracks.first()
            val file = java.io.File(track.filePath)
            if (!file.exists()) return
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share track"))
        } else {
            val uris = tracks.mapNotNull { track ->
                val file = java.io.File(track.filePath)
                if (!file.exists()) return@mapNotNull null
                androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            }
            if (uris.isEmpty()) return
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putExtra(android.content.Intent.EXTRA_STREAM, java.util.ArrayList(uris))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share tracks"))
        }
    }
}
