package com.mymusicplayer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mymusicplayer.data.db.entity.ArtistEntity
import com.mymusicplayer.data.db.entity.TrackArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    fun getArtistById(artistId: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun getArtistByName(name: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtist(artist: ArtistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtists(artists: List<ArtistEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackArtistRelation(relation: TrackArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackArtistRelations(relations: List<TrackArtistEntity>)

    @Query("""
        SELECT a.*, COUNT(DISTINCT ta.track_id) as track_count,
               COUNT(DISTINCT t.album_id) as album_count
        FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artist_id
        INNER JOIN tracks t ON ta.track_id = t.id
        GROUP BY a.id
        ORDER BY a.name COLLATE NOCASE ASC
    """)
    fun getAllArtistsWithCounts(): Flow<List<ArtistWithCounts>>

    @Query("""
        SELECT a.*, COUNT(DISTINCT ta.track_id) as track_count,
               COUNT(DISTINCT t.album_id) as album_count
        FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artist_id
        INNER JOIN tracks t ON ta.track_id = t.id
        GROUP BY a.id
        ORDER BY a.name COLLATE NOCASE DESC
    """)
    fun getAllArtistsWithCountsDesc(): Flow<List<ArtistWithCounts>>

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artist_id
        WHERE ta.track_id = :trackId
    """)
    suspend fun getArtistsForTrack(trackId: Long): List<ArtistEntity>

    @Query("DELETE FROM track_artists WHERE track_id = :trackId")
    suspend fun deleteArtistsForTrack(trackId: Long)

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artist_id FROM track_artists)")
    suspend fun deleteOrphanedArtists()
}

data class ArtistWithCounts(
    val id: Long,
    val name: String,
    val track_count: Int,
    val album_count: Int
)
