package com.mymusicplayer.domain.repository

import com.mymusicplayer.data.scanner.ScanProgress
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllTracks(sort: String): Flow<List<Track>>
    fun searchTracks(query: String): Flow<List<Track>>
    fun getSmartSortedTracks(): Flow<List<Track>>
    fun getTrackById(id: Long): Flow<Track?>
    fun getTracksByAlbum(albumId: Long): Flow<List<Track>>
    fun getTracksByArtist(artistId: Long): Flow<List<Track>>
    fun getAllAlbums(): Flow<List<Album>>
    fun getAlbumById(id: Long): Flow<Album?>
    fun getAllArtists(): Flow<List<Artist>>
    fun getArtistById(id: Long): Flow<Artist?>
    fun getTracksForArtist(artistId: Long): Flow<List<Track>>
    fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>>
    fun getFavoriteTracks(): Flow<List<Track>>
    suspend fun updateTrackRating(trackId: Long, rating: Int)
    suspend fun incrementPlayCount(trackId: Long)
    suspend fun rescanLibrary(
        excludedPaths: List<String> = emptyList(),
        scanDirectoryPath: String? = null,
        minFileSize: Long = 0L,
        minDuration: Long = 0L
    ): Flow<ScanProgress>
    suspend fun deleteTrackById(trackId: Long)
    suspend fun updateAlbumArt(trackId: Long, imageBytes: ByteArray, applyToAll: Boolean = false)
    suspend fun updateLyricsPath(trackId: Long, path: String?)
    suspend fun editTrackMetadata(
        trackId: Long,
        title: String?,
        artists: List<String>?,
        albumTitle: String?,
        year: Int?,
        trackNumber: Int?,
        genre: String?,
        comment: String?
    )
}
