package com.mymusicplayer.domain.repository

import android.content.Context
import android.util.Log
import com.mymusicplayer.data.db.dao.AlbumDao
import java.io.File
import com.mymusicplayer.data.db.dao.ArtistDao
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.AlbumEntity
import com.mymusicplayer.data.db.entity.ArtistEntity
import com.mymusicplayer.data.db.entity.TrackArtistEntity
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
class MusicRepositoryImpl(
    private val context: Context,
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
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

    override fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>> {
        return playlistDao.getTracksInPlaylist(playlistId).map { entities ->
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

    override suspend fun deleteTrackById(trackId: Long) {
        trackDao.deleteTrackById(trackId)
    }

    override suspend fun updateAlbumArt(
        trackId: Long,
        imageBytes: ByteArray,
        applyToAll: Boolean
    ) {
        withContext(Dispatchers.IO) {
            val track = trackDao.getTrackByIdOnce(trackId) ?: return@withContext
            val albumId = track.albumId ?: return@withContext

            // Normalize: downscale large images and re-encode to JPEG so embedding
            // succeeds across formats and doesn't bloat the audio files.
            val resized = resizeArtwork(imageBytes)

            // Best-effort: embed into the audio file(s). The DB/cache update below
            // always runs, so the UI reflects the new cover even if a file write fails.
            val targets = if (applyToAll) {
                trackDao.getTracksByAlbum(albumId).first()
            } else {
                listOf(track)
            }
            for (t in targets) {
                val ok = metadataParser.writeAlbumArt(t.filePath, resized)
                if (!ok) Log.w("MusicRepository", "Embedded art write failed for ${t.filePath}")
            }

            // Single source of truth for the UI: shared album cache + DB.
            val artDir = File(context.cacheDir, "album_art")
            artDir.mkdirs()
            val artFile = File(artDir, "$albumId.jpg")
            artFile.writeBytes(resized)
            albumDao.updateAlbumArt(albumId, artFile.absolutePath)
        }
    }

    private fun resizeArtwork(bytes: ByteArray, maxSize: Int = 1024): ByteArray {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            val scale = if (w > 0 && h > 0) (maxSize.toFloat() / maxOf(w, h)).coerceAtMost(1f) else 1f
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val resized = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bmp, (w * scale).toInt(), (h * scale).toInt(), true
                )
            } else bmp
            val out = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            if (resized != bmp) resized.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            Log.w("MusicRepository", "Artwork resize failed; using original bytes", e)
            bytes
        }
    }

    override suspend fun rescanLibrary(
        excludedPaths: List<String>,
        scanDirectoryPath: String?,
        minFileSize: Long,
        minDuration: Long
    ): Flow<ScanProgress> {
        return scanRepository.scanLibrary(
            excludedPaths, scanDirectoryPath, minFileSize, minDuration
        )
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

            // Best-effort: write embedded tags into the audio file. The SQLite
            // update below always runs so the library reflects the edit even if
            // the file is read-only or the format can't be tagged.
            val fileWritten = metadataParser.writeMetadata(
                filePath = track.filePath,
                title = title,
                artists = artists,
                albumTitle = albumTitle,
                year = year,
                trackNumber = trackNumber,
                genre = genre,
                comment = comment
            )
            if (!fileWritten) {
                Log.w("MusicRepository", "Embedded tag write failed for ${track.filePath}; updating database only")
            }

            // Resolve album: a renamed album reuses an existing one (case-insensitive)
            // or creates a new one, and the track is re-linked to it.
            var albumId = track.albumId
            if (albumTitle != null) {
                val existingAlbum = albumDao.getAlbumByTitleNormalized(albumTitle)
                albumId = existingAlbum?.id ?: albumDao.insertAlbum(
                    AlbumEntity(title = albumTitle, year = year ?: track.year)
                )
            }

            val updated = track.copy(
                title = title ?: track.title,
                albumId = albumId,
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
                        artistDao.insertArtist(ArtistEntity(name = artistName))
                    }
                    artistDao.insertTrackArtistRelation(
                        TrackArtistEntity(
                            trackId = trackId,
                            artistId = artistId,
                            role = "ARTIST"
                        )
                    )
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
