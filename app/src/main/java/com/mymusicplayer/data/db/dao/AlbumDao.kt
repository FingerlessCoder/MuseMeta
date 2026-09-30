package com.mymusicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mymusicplayer.data.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY title COLLATE NOCASE ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("""
        SELECT * FROM albums
        ORDER BY
            CASE WHEN :sort = 'title' THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'album_artist' THEN COALESCE(album_artist, '') END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'album_artist_desc' THEN COALESCE(album_artist, '') END COLLATE NOCASE DESC,
            CASE WHEN :sort = 'year' THEN year END DESC,
            CASE WHEN :sort = 'track_count' THEN (SELECT COUNT(*) FROM tracks WHERE tracks.album_id = albums.id) END DESC,
            CASE WHEN :sort = 'year_desc' THEN year END ASC,
            CASE WHEN :sort = 'track_count_desc' THEN (SELECT COUNT(*) FROM tracks WHERE tracks.album_id = albums.id) END ASC,
            CASE WHEN :sort = 'title_desc' THEN title END COLLATE NOCASE DESC,
            id ASC
    """)
    fun getAllAlbumsSorted(sort: String = "title"): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    fun getAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbumByIdOnce(albumId: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE title = :title COLLATE NOCASE LIMIT 1")
    suspend fun getAlbumByTitle(title: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun getAlbumByTitleNormalized(title: String): AlbumEntity?

    @Query("SELECT * FROM albums")
    suspend fun getAllAlbumsOnce(): List<AlbumEntity>

    @Query("UPDATE tracks SET album_id = :targetId WHERE album_id = :duplicateId")
    suspend fun reassignTracksToAlbum(targetId: Long, duplicateId: Long)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbumById(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("UPDATE albums SET art_path = :artPath WHERE id = :albumId")
    suspend fun updateAlbumArt(albumId: Long, artPath: String?)

    @Query("UPDATE albums SET album_artist = :artist WHERE id = :albumId")
    suspend fun updateAlbumArtist(albumId: Long, artist: String)

    @Query("SELECT COUNT(*) FROM tracks WHERE album_id = :albumId")
    fun getTrackCountForAlbum(albumId: Long): Flow<Int>

    @Query("SELECT SUM(duration) FROM tracks WHERE album_id = :albumId")
    fun getAlbumDuration(albumId: Long): Flow<Long?>
}
