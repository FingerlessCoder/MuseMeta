package com.mymusicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mymusicplayer.data.db.entity.PlaylistEntity
import com.mymusicplayer.data.db.entity.PlaylistEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrackToPlaylist(entry: PlaylistEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTracksToPlaylist(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_entries pe ON t.id = pe.track_id
        WHERE pe.playlist_id = :playlistId
        ORDER BY pe.position ASC
    """)
    fun getTracksInPlaylist(playlistId: Long): Flow<List<com.mymusicplayer.data.db.entity.TrackEntity>>

    @Query("""
        SELECT pe.track_id FROM playlist_entries pe
        WHERE pe.playlist_id = :playlistId
        ORDER BY pe.position ASC
    """)
    fun getTrackIdsInPlaylist(playlistId: Long): Flow<List<Long>>

    @Query("""
        UPDATE playlist_entries SET position = :newPosition
        WHERE playlist_id = :playlistId AND track_id = :trackId
    """)
    suspend fun reorderTrack(playlistId: Long, trackId: Long, newPosition: Int)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlist_id = :playlistId")
    fun getTrackCount(playlistId: Long): Flow<Int>
}
