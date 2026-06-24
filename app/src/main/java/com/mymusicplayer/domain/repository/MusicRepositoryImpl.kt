package com.mymusicplayer.domain.repository

import com.mymusicplayer.data.db.dao.AlbumDao
import com.mymusicplayer.data.db.dao.ArtistDao
import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.TrackEntity
import com.mymusicplayer.data.scanner.MetadataParser
import com.mymusicplayer.data.scanner.ScanProgress
import com.mymusicplayer.data.scanner.ScanRepository
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
class MusicRepositoryImpl constructor(
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val metadataParser: MetadataParser,
    private val scanRepository: ScanRepository
) : MusicRepository {

    override fun getAllTracks(sort: String): Flow<List<Track>> {
        return trackDao.getAllTracks(sort).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchTracks(query: String): Flow<List<Track>> {
        return trackDao.searchTracks(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSmartSortedTracks(): Flow<List<Track>> {
        val now = System.currentTimeMillis()
        val decayDays = 86_400_000L * 30
        return trackDao.getSmartSortedTracks(
            playCountWeight = 0.4,
            recencyWeight = 0.35,
            ratingWeight = 0.25,
            now = now,
            decayDays = decayDays
        ).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTrackById(id: Long): Flow<Track?> {
        return trackDao.getTrackById(id).map { it?.toDomain() }
    }

    override fun getTracksByAlbum(albumId: Long): Flow<List<Track>> {
        return trackDao.getTracksByAlbum(albumId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getTracksByArtist(artistId: Long): Flow<List<Track>> {
        return trackDao.getTracksByArtist(artistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllAlbums(): Flow<List<Album>> {
        return albumDao.getAllAlbums().map { entities ->
            entities.map { entity ->
                Album(
                    id = entity.id,
                    title = entity.title,
                    albumArtist = entity.albumArtist,
                    year = entity.year,
                    artPath = entity.artPath,
                    trackCount = 0,
                    totalDuration = 0
                )
            }
        }
    }

    override fun getAlbumById(id: Long): Flow<Album?> {
        return albumDao.getAlbumById(id).map { entity ->
            entity?.let {
                Album(
                    id = it.id,
                    title = it.title,
                    albumArtist = it.albumArtist,
                    year = it.year,
                    artPath = it.artPath
                )
            }
        }
    }

    override fun getAllArtists(): Flow<List<Artist>> {
        return artistDao.getAllArtistsWithCounts().map { entities ->
            entities.map { entity ->
                Artist(
                    id = entity.id,
                    name = entity.name,
                    trackCount = entity.track_count,
                    albumCount = entity.album_count
                )
            }
        }
    }

    override fun getArtistById(id: Long): Flow<Artist?> {
        return artistDao.getArtistById(id).map { entity ->
            entity?.let {
                Artist(id = it.id, name = it.name)
            }
        }
    }

    override fun getTracksForArtist(artistId: Long): Flow<List<Track>> {
        return trackDao.getTracksByArtist(artistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return trackDao.getSmartSortedTracks().map { entities ->
            entities.filter { it.rating >= 4 }.map { it.toDomain() }
        }
    }

    override suspend fun updateTrackRating(trackId: Long, rating: Int) {
        trackDao.updateRating(trackId, rating)
    }

    override suspend fun incrementPlayCount(trackId: Long) {
        trackDao.incrementPlayCount(trackId)
    }

    override suspend fun rescanLibrary(
        excludedPaths: List<String>,
        scanDirectoryPath: String?
    ): Flow<ScanProgress> {
        return scanRepository.scanLibrary(excludedPaths, scanDirectoryPath)
    }

    override suspend fun editTrackMetadata(
        trackId: Long,
        title: String?,
        artists: List<String>?,
        albumTitle: String?,
        year: Int?,
        trackNumber: Int?,
        genre: String?,
        comment: String?
    ) {
        withContext(Dispatchers.IO) {
            val track = trackDao.getTrackByIdOnce(trackId) ?: return@withContext

            val success = metadataParser.writeMetadata(
                filePath = track.filePath,
                title = title,
                artists = artists,
                albumTitle = albumTitle,
                year = year,
                trackNumber = trackNumber,
                genre = genre,
                comment = comment
            )

            if (success) {
                val updated = track.copy(
                    title = title ?: track.title,
                    year = year ?: track.year,
                    trackNumber = trackNumber ?: track.trackNumber,
                    genre = genre ?: track.genre,
                    comment = comment ?: track.comment
                )
                trackDao.updateTrack(updated)

                if (artists != null) {
                    artistDao.deleteArtistsForTrack(trackId)
                    for (artistName in artists) {
                        val existing = artistDao.getArtistByName(artistName)
                        val artistId = if (existing != null) {
                            existing.id
                        } else {
                            artistDao.insertArtist(
                                com.mymusicplayer.data.db.entity.ArtistEntity(name = artistName)
                            )
                        }
                        artistDao.insertTrackArtistRelation(
                            com.mymusicplayer.data.db.entity.TrackArtistEntity(
                                trackId = trackId,
                                artistId = artistId,
                                role = "ARTIST"
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun TrackEntity.toDomain(): Track {
        val albumEntity = albumId?.let { albumDao.getAlbumByIdOnce(it) }
        val artistEntities = artistDao.getArtistsForTrack(id)

        return Track(
            id = id,
            title = title,
            artists = artistEntities.map {
                Artist(id = it.id, name = it.name)
            },
            album = albumEntity?.let {
                Album(
                    id = it.id,
                    title = it.title,
                    albumArtist = it.albumArtist,
                    year = it.year,
                    artPath = it.artPath
                )
            },
            duration = duration,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            genre = genre,
            comment = comment,
            filePath = filePath,
            fileSize = fileSize,
            format = format,
            dateAdded = dateAdded,
            lastPlayed = lastPlayed,
            playCount = playCount,
            rating = rating,
            lyricsPath = lyricsPath,
            rawArtistTag = rawArtistTag
        )
    }
}
