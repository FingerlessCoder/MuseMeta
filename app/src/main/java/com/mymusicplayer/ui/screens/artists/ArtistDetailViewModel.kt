package com.mymusicplayer.ui.screens.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.network.ArtistImageFetcher
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artistArtPath: String? = null,
    val isLoading: Boolean = true,
    val totalDuration: Long = 0
)

class ArtistDetailViewModel(
    private val artistId: Long,
    private val musicRepository: MusicRepository,
    private val musicPlayerController: MusicPlayerController,
    private val artistImageFetcher: ArtistImageFetcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            musicRepository.getArtistById(artistId).collect { artist ->
                _uiState.value = _uiState.value.copy(artist = artist)
            }
        }
        viewModelScope.launch {
            musicRepository.getTracksForArtist(artistId).collect { tracks ->
                val albums = tracks.mapNotNull { it.album }.distinctBy { it.id }
                val cachedArtistArt = artistImageFetcher.getArtistArtFile(artistId).takeIf { it.exists() }?.absolutePath
                val artistArtPath = cachedArtistArt
                    ?: albums.firstOrNull { it.artPath != null }?.artPath
                val totalDuration = tracks.sumOf { it.duration }
                _uiState.value = _uiState.value.copy(
                    tracks = tracks,
                    albums = albums,
                    artistArtPath = artistArtPath,
                    totalDuration = totalDuration,
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            val artist = musicRepository.getArtistById(artistId).first()
            if (artist != null) {
                val networkPath = artistImageFetcher.fetchArtistImage(artist.name, artistId)
                if (networkPath != null) {
                    _uiState.value = _uiState.value.copy(artistArtPath = networkPath)
                }
            }
        }
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            musicPlayerController.initialize()
            val tracks = _uiState.value.tracks
            val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            musicPlayerController.playFromQueue(
                trackPaths = tracks.map { it.filePath },
                startIndex = index,
                trackIds = tracks.map { it.id },
                titles = tracks.map { it.title },
                artists = tracks.map { it.artists.joinToString(" · ") { artist -> artist.name } },
                albumArtPaths = tracks.map { it.album?.artPath }
            )
        }
    }

    fun playAll() {
        val tracks = _uiState.value.tracks
        if (tracks.isNotEmpty()) {
            viewModelScope.launch {
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
        }
    }

    fun shuffleAll() {
        val tracks = _uiState.value.tracks.toMutableList()
        if (tracks.isNotEmpty()) {
            tracks.shuffle()
            viewModelScope.launch {
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
        }
    }
}
