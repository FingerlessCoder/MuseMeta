package com.mymusicplayer.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artistCount: Int = 0,
    val isLoading: Boolean = true
) {
    val trackCount: Int get() = tracks.size
    val albumCount: Int get() = albums.size
    val totalDuration: Long get() = tracks.sumOf { it.duration }
    val recentTracks: List<Track> get() = tracks.sortedByDescending { it.dateAdded }.take(5)
    val featuredAlbum: Album? get() = albums.firstOrNull { it.artPath != null } ?: albums.firstOrNull()
}

class HomeViewModel(
    musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        musicRepository.getAllTracks("date_added"),
        musicRepository.getAllAlbums(),
        musicRepository.getAllArtists()
    ) { tracks, albums, artists ->
        HomeUiState(
            tracks = tracks,
            albums = albums,
            artistCount = artists.size,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun playTrack(track: Track) {
        musicPlayerController.initialize()
        musicPlayerController.play(track.filePath, track.id)
    }
}
