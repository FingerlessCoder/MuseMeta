package com.mymusicplayer.data.scanner

import android.os.Environment
import android.util.Log
import com.mymusicplayer.data.db.dao.AlbumDao
import com.mymusicplayer.data.db.dao.ArtistDao
import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.AlbumEntity
import com.mymusicplayer.data.db.entity.ArtistEntity
import com.mymusicplayer.data.db.entity.TrackArtistEntity
import com.mymusicplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ScanProgress(
    val phase: ScanPhase,
    val progress: Float,
    val message: String
)

enum class ScanPhase {
    DISCOVERING,
    PARSING,
    DB_WRITE,
    COMPLETE,
    ERROR
}

class ScanRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataParser: MetadataParser
) {

    companion object {
        private const val TAG = "ScanRepository"
        private val EXCLUDED_DIRS = setOf(
            "Notifications", "Ringtones", "Alarms", "podcasts"
        )
    }

    fun scanLibrary(excludedPaths: List<String> = emptyList()): Flow<ScanProgress> = flow {
        emit(ScanProgress(ScanPhase.DISCOVERING, 0f, "Discovering audio files..."))

        val allFiles = mediaStoreScanner.scanAll()
        val audioFiles = if (excludedPaths.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { file ->
                excludedPaths.none { excluded ->
                    file.path.startsWith(excluded, ignoreCase = true)
                }
            }
        }
        if (audioFiles.isEmpty()) {
            emit(ScanProgress(ScanPhase.COMPLETE, 1f, "No audio files found"))
            return@flow
        }

        emit(ScanProgress(ScanPhase.DISCOVERING, 0.3f, "Found ${audioFiles.size} files"))

        val existingPaths = trackDao.getAllFilePaths()
        val scannedPaths = audioFiles.map { it.path }.toSet()

        val parsedTracks = mutableListOf<TrackEntity>()
        val allRelations = mutableListOf<TrackArtistEntity>()
        val albumCache = mutableMapOf<String, Long>()

        val totalFiles = audioFiles.size
        audioFiles.forEachIndexed { index, audioFile ->
            val progress = 0.3f + (index.toFloat() / totalFiles) * 0.5f
            val fileName = File(audioFile.path).name
            emit(ScanProgress(ScanPhase.PARSING, progress, "Parsing: $fileName"))

            val metadata = metadataParser.parse(audioFile.path) ?: return@forEachIndexed

            val albumId = resolveAlbumId(metadata, albumCache)

            val trackEntity = TrackEntity(
                title = metadata.title,
                albumId = albumId,
                duration = metadata.duration,
                trackNumber = metadata.trackNumber,
                discNumber = metadata.discNumber,
                year = metadata.year,
                genre = metadata.genre,
                comment = metadata.comment,
                filePath = audioFile.path,
                fileSize = audioFile.size,
                bitrate = metadata.bitrate,
                sampleRate = metadata.sampleRate,
                format = metadata.format,
                dateAdded = System.currentTimeMillis(),
                rawArtistTag = metadata.artists.joinToString(" / ")
            )

            parsedTracks.add(trackEntity)

            metadata.albumArtBytes?.let { bytes ->
                albumId?.let { id -> saveAlbumArt(id, bytes) }
            }

            for (artistName in metadata.artists) {
                val artistId = resolveArtistId(artistName)
                allRelations.add(
                    TrackArtistEntity(
                        trackId = 0,
                        artistId = artistId,
                        role = "ARTIST"
                    )
                )
            }
        }

        emit(ScanProgress(ScanPhase.DB_WRITE, 0.9f, "Writing to database..."))

        withContext(Dispatchers.IO) {
            trackDao.deleteRemovedTracks(scannedPaths.toList())

            parsedTracks.forEachIndexed { idx, track ->
                val existingId = trackDao.getTrackIdByPath(track.filePath)
                val trackId: Long

                if (existingId != null) {
                    trackDao.updateTrack(track.copy(id = existingId))
                    trackId = existingId
                } else {
                    trackId = trackDao.insertTrack(track)
                }

                artistDao.deleteArtistsForTrack(trackId)

                val relationRef = allRelations[idx]
                val relation = TrackArtistEntity(
                    trackId = trackId,
                    artistId = relationRef.artistId,
                    role = relationRef.role
                )
                artistDao.insertTrackArtistRelation(relation)
            }

            artistDao.deleteOrphanedArtists()
            mergeDuplicateAlbums()
        }

        emit(ScanProgress(ScanPhase.COMPLETE, 1f, "Scan complete: ${parsedTracks.size} tracks"))
    }

    private suspend fun resolveAlbumId(
        metadata: ParsedMetadata,
        albumCache: MutableMap<String, Long>
    ): Long? {
        val albumTitle = metadata.albumTitle ?: return null
        val cacheKey = albumTitle.lowercase().trim()

        albumCache[cacheKey]?.let { return it }

        val existing = albumDao.getAlbumByTitleNormalized(albumTitle)
        if (existing != null) {
            albumCache[cacheKey] = existing.id
            if (existing.artPath == null && metadata.albumArtBytes != null) {
                saveAlbumArt(existing.id, metadata.albumArtBytes)
            }
            return existing.id
        }

        val newAlbum = AlbumEntity(
            title = albumTitle.trim(),
            albumArtist = metadata.albumArtist,
            year = metadata.year
        )
        val albumId = albumDao.insertAlbum(newAlbum)
        albumCache[cacheKey] = albumId
        return albumId
    }

    private suspend fun resolveArtistId(artistName: String): Long {
        val existing = artistDao.getArtistByName(artistName)
        if (existing != null) return existing.id

        val newArtist = ArtistEntity(name = artistName)
        return artistDao.insertArtist(newArtist)
    }

    private suspend fun mergeDuplicateAlbums() {
        val allAlbums = albumDao.getAllAlbumsOnce()
        val seen = mutableMapOf<String, MutableList<AlbumEntity>>()

        for (album in allAlbums) {
            val key = album.title.lowercase().trim()
            seen.getOrPut(key) { mutableListOf() }.add(album)
        }

        for ((_, duplicates) in seen) {
            if (duplicates.size <= 1) continue
            val sorted = duplicates.sortedByDescending { it.id }
            val keep = sorted.first()
            for (duplicate in sorted.drop(1)) {
                albumDao.reassignTracksToAlbum(keep.id, duplicate.id)
                albumDao.deleteAlbumById(duplicate.id)
            }
        }
    }

    private suspend fun saveAlbumArt(albumId: Long, bytes: ByteArray) {
        val albumArtDir = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/com.mymusicplayer.musemeta/cache/album_art"
        )
        albumArtDir.mkdirs()

        val artFile = File(albumArtDir, "${albumId}.jpg")
        try {
            artFile.writeBytes(bytes)
            albumDao.updateAlbumArt(albumId, artFile.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save album art for album $albumId: ${e.message}")
        }
    }
}
