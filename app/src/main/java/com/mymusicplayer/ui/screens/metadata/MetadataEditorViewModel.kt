package com.mymusicplayer.ui.screens.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetadataEditorState(
    val isVisible: Boolean = false,
    val track: Track? = null,
    val title: String = "",
    val artists: String = "",
    val albumTitle: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val genre: String = "",
    val comment: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null
)

@HiltViewModel
class MetadataEditorViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MetadataEditorState())
    val state: StateFlow<MetadataEditorState> = _state.asStateFlow()

    fun openEditor(track: Track) {
        _state.value = MetadataEditorState(
            isVisible = true,
            track = track,
            title = track.title,
            artists = track.artists.joinToString(" / ") { it.name },
            albumTitle = track.album?.title ?: "",
            year = track.year?.toString() ?: "",
            trackNumber = track.trackNumber?.toString() ?: "",
            genre = track.genre ?: "",
            comment = track.comment ?: ""
        )
    }

    fun dismiss() {
        _state.value = MetadataEditorState()
    }

    fun updateTitle(value: String) {
        _state.value = _state.value.copy(title = value)
    }

    fun updateArtists(value: String) {
        _state.value = _state.value.copy(artists = value)
    }

    fun updateAlbumTitle(value: String) {
        _state.value = _state.value.copy(albumTitle = value)
    }

    fun updateYear(value: String) {
        _state.value = _state.value.copy(year = value)
    }

    fun updateTrackNumber(value: String) {
        _state.value = _state.value.copy(trackNumber = value)
    }

    fun updateGenre(value: String) {
        _state.value = _state.value.copy(genre = value)
    }

    fun updateComment(value: String) {
        _state.value = _state.value.copy(comment = value)
    }

    fun save() {
        val s = _state.value
        val track = s.track ?: return

        _state.value = s.copy(isSaving = true)

        viewModelScope.launch {
            musicRepository.editTrackMetadata(
                trackId = track.id,
                title = s.title,
                artists = s.artists.split("/").map { it.trim() }.filter { it.isNotBlank() },
                albumTitle = s.albumTitle.ifBlank { null },
                year = s.year.toIntOrNull(),
                trackNumber = s.trackNumber.toIntOrNull(),
                genre = s.genre.ifBlank { null },
                comment = s.comment.ifBlank { null }
            )

            _state.value = _state.value.copy(
                isSaving = false,
                saveSuccess = true
            )
        }
    }
}
