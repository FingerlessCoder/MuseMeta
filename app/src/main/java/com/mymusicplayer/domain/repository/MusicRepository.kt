package com.mymusicplayer.domain.repository

import android.content.IntentSender
import android.net.Uri
import com.mymusicplayer.data.scanner.ScanProgress
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import kotlinx.coroutines.flow.Flow

sealed class WriteResult {
    object Success : WriteResult()
    data class Error(val message: String) : WriteResult()
    data class PermissionRequired(val intentSender: IntentSender) : WriteResult()
}

interface MusicRepository {
    fun getAllTracks(sort: String): Flow<List<Track>>
    fun searchTracks(query: String): Flow<List<Track>>
    fun getSmartSortedTracks(): Flow<List<Track>>
    fun getTrackById(id: Long): Flow<Track?>
    fun getTracksByAlbum(albumId: Long): Flow<List<Track>>
    fun getTracksByArtist(artistId: Long): Flow<List<Track>>
    fun getAllAlbums(): Flow<List<Album>>
    fun getAllAlbums(sort: String): Flow<List<Album>>
    fun getAlbumById(id: Long): Flow<Album?>
    fun getAllArtists(): Flow<List<Artist>>
    fun getAllArtists(sort: String): Flow<List<Artist>>
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
    suspend fun updateLyricsPath(trackId: Long, path: String?)

    /**
     * Triggers an incremental scan for specific file paths.
     */
    fun triggerIncrementalScan(paths: List<String>)

    /**
     * Updates album art for a track. On Android 10+, uses MediaStore to write the artwork.
     */
    suspend fun updateAlbumArt(
        trackId: Long,
        imageBytes: ByteArray,
        applyToAll: Boolean = false
    ): WriteResult

    /**
     * Edits track metadata. On Android 10+, uses MediaStore to write the tags.
     */
    suspend fun editTrackMetadata(
        trackId: Long,
        title: String?,
        artists: List<String>?,
        albumTitle: String?,
        year: Int?,
        trackNumber: Int?,
        genre: String?
    ): WriteResult

    /**
     * Updates metadata for all tracks in an album.
     */
    suspend fun batchUpdateTrackMetadata(
        albumId: Long,
        albumTitle: String?,
        year: Int?,
        genre: String?
    ): WriteResult
}
