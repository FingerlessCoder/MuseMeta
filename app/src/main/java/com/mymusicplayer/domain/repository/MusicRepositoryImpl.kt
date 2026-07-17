package com.mymusicplayer.domain.repository

import android.media.MediaScannerConnection
import android.content.ContentValues
import android.content.Intent
import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import com.mymusicplayer.service.ScanService
import java.io.FileOutputStream
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
        return trackDao.getAllTracks(sort).map { entities -> mapTracksBulk(entities) }
    }

    override fun searchTracks(query: String): Flow<List<Track>> {
        return trackDao.searchTracks(query).map { entities -> mapTracksBulk(entities) }
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
        ).map { entities -> mapTracksBulk(entities) }
    }

    override fun getTrackById(id: Long): Flow<Track?> {
        return trackDao.getTrackById(id).map { it?.let { entity -> mapTrackSingle(entity) } }
    }

    override fun getTracksByAlbum(albumId: Long): Flow<List<Track>> {
        return trackDao.getTracksByAlbum(albumId).map { entities -> mapTracksBulk(entities) }
    }

    override fun getTracksByArtist(artistId: Long): Flow<List<Track>> {
        return trackDao.getTracksByArtist(artistId).map { entities -> mapTracksBulk(entities) }
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

    override fun getAllAlbums(sort: String): Flow<List<Album>> {
        return albumDao.getAllAlbumsSorted(sort).map { entities ->
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

    override fun getAllArtists(sort: String): Flow<List<Artist>> {
        val source = if (sort == "name_desc") artistDao.getAllArtistsWithCountsDesc()
        else artistDao.getAllArtistsWithCounts()
        return source.map { entities ->
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
        return trackDao.getTracksByArtist(artistId).map { entities -> mapTracksBulk(entities) }
    }

    override fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>> {
        return playlistDao.getTracksInPlaylist(playlistId).map { entities -> mapTracksBulk(entities) }
    }

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return trackDao.getSmartSortedTracks().map { entities ->
            val favorites = entities.filter { it.rating >= 4 }
            mapTracksBulk(favorites)
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

    override suspend fun updateLyricsPath(trackId: Long, path: String?) {
        withContext(Dispatchers.IO) {
            trackDao.updateLyricsPath(trackId, path)
        }
    }

    override suspend fun updateAlbumArt(
        trackId: Long,
        imageBytes: ByteArray,
        applyToAll: Boolean
    ): WriteResult {
        return withContext(Dispatchers.IO) {
            val track = trackDao.getTrackByIdOnce(trackId) ?: return@withContext WriteResult.Error("Track not found")
            val albumId = track.albumId ?: return@withContext WriteResult.Error("Album not found")

            val resized = resizeArtwork(imageBytes)

            val targets = if (applyToAll) {
                trackDao.getTracksByAlbumOnce(albumId)
            } else {
                listOf(track)
            }

            val result = performSafeWrite(targets) { tempFile ->
                metadataParser.writeAlbumArt(tempFile.absolutePath, resized, context.cacheDir)
            }

            if (result is WriteResult.Success) {
                // Update cache and DB
                val artDir = File(context.cacheDir, "album_art")
                artDir.mkdirs()
                val artFile = File(artDir, "$albumId.jpg")
                artFile.writeBytes(resized)
                albumDao.updateAlbumArt(albumId, artFile.absolutePath)
            }

            result
        }
    }

    override suspend fun editTrackMetadata(
        trackId: Long,
        title: String?,
        artists: List<String>?,
        albumTitle: String?,
        year: Int?,
        trackNumber: Int?,
        genre: String?
    ): WriteResult {
        return withContext(Dispatchers.IO) {
            val track = trackDao.getTrackByIdOnce(trackId) ?: return@withContext WriteResult.Error("Track not found")

            val result = performSafeWrite(listOf(track)) { tempFile ->
                metadataParser.writeMetadata(
                    filePath = tempFile.absolutePath,
                    title = title,
                    artists = artists,
                    albumTitle = albumTitle,
                    year = year,
                    trackNumber = trackNumber,
                    genre = genre
                )
            }

            if (result is WriteResult.Success) {
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
                    genre = genre ?: track.genre
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
            result
        }
    }

    override suspend fun batchUpdateTrackMetadata(
        albumId: Long,
        albumTitle: String?,
        year: Int?,
        genre: String?
    ): WriteResult {
        return withContext(Dispatchers.IO) {
            val tracks = trackDao.getTracksByAlbumOnce(albumId)
            if (tracks.isEmpty()) return@withContext WriteResult.Error("No tracks found in album")

            val result = performSafeWrite(tracks) { tempFile ->
                metadataParser.writeMetadata(
                    filePath = tempFile.absolutePath,
                    albumTitle = albumTitle,
                    year = year,
                    genre = genre
                )
            }

            if (result is WriteResult.Success) {
                // Resolve/Update Album
                var targetAlbumId = albumId
                if (albumTitle != null) {
                    val existingAlbum = albumDao.getAlbumByTitleNormalized(albumTitle)
                    targetAlbumId = existingAlbum?.id ?: albumDao.insertAlbum(
                        AlbumEntity(title = albumTitle, year = year ?: tracks.first().year)
                    )
                }

                for (track in tracks) {
                    val updated = track.copy(
                        albumId = targetAlbumId,
                        year = year ?: track.year,
                        genre = genre ?: track.genre
                    )
                    trackDao.updateTrack(updated)
                }
                
                // If the album was renamed to a new ID, we might have leftover empty albums.
                // Room/DAO usually handles this if we have cleanup logic, but here we just update.
            }
            result
        }
    }

    override fun triggerIncrementalScan(paths: List<String>) {
        if (paths.isEmpty()) return
        val intent = Intent(context, ScanService::class.java).apply {
            action = ScanService.ACTION_SCAN_PATHS
            putStringArrayListExtra(ScanService.EXTRA_PATHS, ArrayList(paths))
        }
        context.startService(intent)
    }

    private suspend fun performSafeWrite(
        targets: List<TrackEntity>,
        writeBlock: (File) -> Boolean
    ): WriteResult {
        // Correctly map targets to URIs
        val targetsWithUris = targets.mapNotNull { track ->
            getUriForPath(track.filePath)?.let { track to it }
        }
        
        if (targetsWithUris.isEmpty() && targets.isNotEmpty()) {
            return WriteResult.Error("Could not resolve MediaStore URIs for tracks")
        }

        val allUris = targetsWithUris.map { it.second }

        try {
            for ((track, uri) in targetsWithUris) {
                val extension = File(track.filePath).extension.ifBlank { "mp3" }
                val tempFile = File(context.cacheDir, "temp_write_${System.currentTimeMillis()}.$extension")
                
                try {
                    val originalFile = File(track.filePath)
                    if (!originalFile.exists()) {
                        Log.w("MusicRepository", "File not found on disk: ${track.filePath}")
                        continue
                    }

                    // 1. Copy original file to temp
                    originalFile.copyTo(tempFile, overwrite = true)

                    // 2. Apply modifications to temp file
                    if (!writeBlock(tempFile)) {
                        Log.e("MusicRepository", "jAudiotagger failed to write to temp file: ${tempFile.absolutePath}")
                        return WriteResult.Error("Failed to write tags to temporary file for ${track.title}")
                    }

                    // 3. Write temp file back to original URI
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: return WriteResult.Error("Failed to open output stream for ${track.title}")

                    Log.d("MusicRepository", "Successfully overwritten: ${track.filePath}")

                } finally {
                    tempFile.delete()
                }
            }
            
            // Notify system MediaStore about the changes
            val paths = targets.map { it.filePath }.toTypedArray()
            MediaScannerConnection.scanFile(context, paths, null) { path, uri ->
                Log.d("MusicRepository", "System scan completed for $path: $uri")
            }

            // Trigger internal incremental scan for affected files
            triggerIncrementalScan(targets.map { it.filePath })

            return WriteResult.Success
        } catch (securityException: SecurityException) {
            Log.d("MusicRepository", "Caught SecurityException, requesting permission", securityException)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, allUris)
                WriteResult.PermissionRequired(pendingIntent.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                    ?: return WriteResult.Error(securityException.message ?: "Security Exception")
                WriteResult.PermissionRequired(recoverableSecurityException.userAction.actionIntent.intentSender)
            } else {
                WriteResult.Error("Write permission denied: ${securityException.message}")
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Error during safe write", e)
            return WriteResult.Error(e.message ?: "Unknown write error")
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

    private fun getUriForPath(path: String): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        return context.contentResolver.query(
            collection, projection, selection, selectionArgs, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                Uri.withAppendedPath(collection, id.toString())
            } else null
        }
    }

    /**
     * Maps a list of track entities to domain models in a single pass.
     * Loads all albums and all track→artist relations once (2 queries total)
     * instead of 2 queries per track, eliminating the N+1 bottleneck that made
     * the library load slow.
     */
    private suspend fun mapTracksBulk(entities: List<TrackEntity>): List<Track> {
        if (entities.isEmpty()) return emptyList()
        val albumMap = albumDao.getAllAlbumsOnce().associateBy { it.id }
        val artistRelations = artistDao.getAllArtistRelations()
        val artistsByTrack = artistRelations.groupBy(
            keySelector = { it.track_id },
            valueTransform = { Artist(id = it.id, name = it.name) }
        )
        return entities.map { entity ->
            Track(
                id = entity.id,
                title = entity.title,
                artists = artistsByTrack[entity.id].orEmpty(),
                album = entity.albumId?.let { albumMap[it] }?.let { album ->
                    Album(
                        id = album.id,
                        title = album.title,
                        albumArtist = album.albumArtist,
                        year = album.year,
                        artPath = album.artPath
                    )
                },
                duration = entity.duration,
                trackNumber = entity.trackNumber,
                discNumber = entity.discNumber,
                year = entity.year,
                genre = entity.genre,
                comment = entity.comment,
                filePath = entity.filePath,
                fileSize = entity.fileSize,
                format = entity.format,
                dateAdded = entity.dateAdded,
                lastPlayed = entity.lastPlayed,
                playCount = entity.playCount,
                rating = entity.rating,
                lyricsPath = entity.lyricsPath,
                rawArtistTag = entity.rawArtistTag
            )
        }
    }

    /**
     * Maps a single track entity (used where only one track is needed and a
     * full bulk pass would be wasteful). Keeps the original per-track queries.
     */
    private suspend fun mapTrackSingle(entity: TrackEntity): Track {
        return mapTracksBulk(listOf(entity)).first()
    }
}
