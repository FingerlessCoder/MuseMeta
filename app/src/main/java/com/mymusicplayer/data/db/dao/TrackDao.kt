package com.mymusicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mymusicplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("""
        SELECT t.* FROM tracks t
        ORDER BY 
            CASE WHEN :sort = 'name' 
                THEN CASE WHEN SUBSTR(t.title, 1, 1) BETWEEN 'A' AND 'Z' THEN 0
                          WHEN SUBSTR(t.title, 1, 1) BETWEEN 'a' AND 'z' THEN 0
                          ELSE 1
                     END
            END ASC,
            CASE WHEN :sort = 'name' THEN t.title END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'date_added' THEN t.date_added END DESC,
            CASE WHEN :sort = 'play_count' THEN t.play_count END DESC,
            CASE WHEN :sort = 'duration' THEN t.duration END ASC,
            CASE WHEN :sort = 'rating' THEN t.rating END DESC
    """)
    fun getAllTracks(sort: String = "name"): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        WHERE (:query IS NULL OR t.title LIKE '%' || :query || '%'
            OR t.raw_artist_tag LIKE '%' || :query || '%')
        ORDER BY t.title ASC
    """)
    fun searchTracks(query: String?): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        WHERE (:minSize IS NULL OR t.file_size >= :minSize)
        AND (:maxSize IS NULL OR t.file_size <= :maxSize)
        AND (:minDuration IS NULL OR t.duration >= :minDuration)
        AND (:maxDuration IS NULL OR t.duration <= :maxDuration)
        ORDER BY t.title ASC
    """)
    fun filterTracks(
        minSize: Long? = null,
        maxSize: Long? = null,
        minDuration: Long? = null,
        maxDuration: Long? = null
    ): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        ORDER BY 
            (t.play_count * :playCountWeight + 
             CASE WHEN t.last_played IS NOT NULL 
                THEN (1.0 - (CAST(:now - t.last_played AS REAL) / :decayDays)) * :recencyWeight
                ELSE 0 END +
             (CAST(t.rating AS REAL) / 5.0) * :ratingWeight
            ) DESC
    """)
    fun getSmartSortedTracks(
        playCountWeight: Double = 0.4,
        recencyWeight: Double = 0.35,
        ratingWeight: Double = 0.25,
        now: Long = System.currentTimeMillis(),
        decayDays: Long = 86_400_000L * 30
    ): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    fun getTrackById(trackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackByIdOnce(trackId: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE album_id = :albumId ORDER BY COALESCE(disc_number, 1), COALESCE(track_number, 9999)")
    fun getTracksByAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN track_artists ta ON t.id = ta.track_id
        WHERE ta.artist_id = :artistId
        ORDER BY t.year DESC, t.album_id, t.track_number
    """)
    fun getTracksByArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)

    @Query("SELECT file_path FROM tracks WHERE id = :trackId")
    suspend fun getTrackPath(trackId: Long): String?

    @Query("UPDATE tracks SET play_count = play_count + 1, last_played = :now WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tracks SET rating = :rating WHERE id = :trackId")
    suspend fun updateRating(trackId: Long, rating: Int)

    @Query("SELECT DISTINCT file_path FROM tracks")
    suspend fun getAllFilePaths(): List<String>

    @Query("SELECT id FROM tracks WHERE file_path = :filePath LIMIT 1")
    suspend fun getTrackIdByPath(filePath: String): Long?

    @Query("DELETE FROM tracks WHERE file_path NOT IN (:existingPaths)")
    suspend fun deleteRemovedTracks(existingPaths: List<String>)
}
