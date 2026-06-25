package com.mymusicplayer.data.scanner

import android.content.Context
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

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

class ScanRepository constructor(
    private val context: Context,
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val mediaStoreScanner: MediaStoreScanner,
    private val metadataParser: MetadataParser,
    private val fileSystemScanner: FileSystemScanner
) {

    companion object {
        private const val TAG = "ScanRepository"
        private const val PARALLEL_PARSERS = 4
        private const val PROGRESS_INTERVAL_MS = 150L
        private val EXCLUDED_DIRS = setOf(
            "Notifications", "Ringtones", "Alarms", "podcasts"
        )
    }

    fun scanLibrary(
        excludedPaths: List<String> = emptyList(),
        scanDirectoryPath: String? = null,
        minFileSize: Long = 0L,
        minDuration: Long = 0L
    ): Flow<ScanProgress> = callbackFlow {
        try {
            trySend(ScanProgress(ScanPhase.DISCOVERING, 0f, "Discovering audio files..."))

            val allFiles = if (scanDirectoryPath != null) {
                val paths = scanDirectoryPath.split("|").filter { it.isNotBlank() }
                paths.flatMap { path -> fileSystemScanner.scanDirectory(path) }
            } else {
                mediaStoreScanner.scanAll()
            }

            val audioFiles = allFiles.filter { file ->
                if (excludedPaths.isNotEmpty() && excludedPaths.any { file.path.startsWith(it, ignoreCase = true) }) {
                    return@filter false
                }
                if (minFileSize > 0L && file.size < minFileSize) return@filter false
                true
            }

            if (audioFiles.isEmpty()) {
                trySend(ScanProgress(ScanPhase.COMPLETE, 1f, "No audio files found"))
                close()
                return@callbackFlow
            }

            trySend(ScanProgress(
                ScanPhase.DISCOVERING, 0.3f,
                "Found ${audioFiles.size} audio files"
            ))

            val existingPaths = trackDao.getAllFilePaths()
            val scannedPaths = audioFiles.map { it.path }.toSet()
            val totalFiles = audioFiles.size

            val parsedResults = mutableListOf<Pair<MediaStoreAudioFile, ParsedMetadata?>>()
            var lastEmitMs = System.currentTimeMillis()

            audioFiles.chunked(PARALLEL_PARSERS).forEachIndexed { batchIndex, batch ->
                val deferred = batch.map { file ->
                    async(Dispatchers.IO) {
                        file to metadataParser.parse(file.path, extractAlbumArt = false)
                    }
                }
                val batchResults = deferred.awaitAll()
                parsedResults.addAll(batchResults)

                val processed = min((batchIndex + 1) * PARALLEL_PARSERS, totalFiles)
                val now = System.currentTimeMillis()
                if (now - lastEmitMs >= PROGRESS_INTERVAL_MS || processed == totalFiles) {
                    val progress = 0.3f + (processed.toFloat() / totalFiles) * 0.5f
                    val fileName = batchResults.lastOrNull()?.first?.let {
                        File(it.path).name
                    } ?: ""
                    trySend(ScanProgress(
                        ScanPhase.PARSING, progress,
                        "Parsing $processed/$totalFiles — $fileName"
                    ))
                    lastEmitMs = now
                }
            }

            trySend(ScanProgress(ScanPhase.DB_WRITE, 0.9f, "Writing to database..."))

            withContext(Dispatchers.IO) {
                writeBatch(parsedResults, scannedPaths, minDuration)
            }

            trySend(ScanProgress(
                ScanPhase.COMPLETE, 1f,
                "Scan complete: ${parsedResults.count { it.second != null }} tracks"
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            trySend(ScanProgress(ScanPhase.ERROR, 0f, "Scan failed: ${e.message}"))
        } finally {
            close()
        }

        awaitClose { }
    }

    private suspend fun writeBatch(
        parsedResults: List<Pair<MediaStoreAudioFile, ParsedMetadata?>>,
        scannedPaths: Set<String>,
        minDuration: Long = 0L
    ) {
        trackDao.deleteRemovedTracks(scannedPaths.toList())

        val albumCache = mutableMapOf<String, Long>()
        val firstTrackPathPerAlbum = mutableMapOf<Long, String>()
        val validTracks = parsedResults.mapNotNull { (file, meta) ->
            meta ?: return@mapNotNull null
            if (minDuration > 0L && meta.duration < minDuration) return@mapNotNull null
            Triple(file, meta, resolveAlbumId(meta, albumCache))
        }

        validTracks.chunked(50).forEach { chunk ->
            for ((file, metadata, albumId) in chunk) {
                val existingId = trackDao.getTrackIdByPath(file.path)
                val trackEntity = TrackEntity(
                    title = metadata.title,
                    albumId = albumId,
                    duration = metadata.duration,
                    trackNumber = metadata.trackNumber,
                    discNumber = metadata.discNumber,
                    year = metadata.year,
                    genre = metadata.genre,
                    comment = metadata.comment,
                    filePath = file.path,
                    fileSize = file.size,
                    bitrate = metadata.bitrate,
                    sampleRate = metadata.sampleRate,
                    format = metadata.format,
                    dateAdded = System.currentTimeMillis(),
                    rawArtistTag = metadata.artists.joinToString(" / ")
                )

                val trackId: Long
                if (existingId != null) {
                    trackDao.updateTrack(trackEntity.copy(id = existingId))
                    trackId = existingId
                } else {
                    trackId = trackDao.insertTrack(trackEntity)
                }

                artistDao.deleteArtistsForTrack(trackId)
                val relations = metadata.artists.map { artistName ->
                    val artistId = resolveArtistId(artistName)
                    TrackArtistEntity(
                        trackId = trackId,
                        artistId = artistId,
                        role = "ARTIST"
                    )
                }
                artistDao.insertTrackArtistRelations(relations)

                if (albumId != null && albumId !in firstTrackPathPerAlbum) {
                    firstTrackPathPerAlbum[albumId] = file.path
                }
            }
        }

        artistDao.deleteOrphanedArtists()
        mergeDuplicateAlbums()

        extractAlbumArt(firstTrackPathPerAlbum)
    }

    private suspend fun extractAlbumArt(albumTrackMap: Map<Long, String>) {
        val artDir = File(context.cacheDir, "album_art")
        artDir.mkdirs()

        for ((albumId, filePath) in albumTrackMap) {
            if (albumId == 0L) continue
            try {
                val album = albumDao.getAlbumByIdOnce(albumId) ?: continue

                if (album.albumArtist == null) {
                    val meta = metadataParser.parse(filePath, extractAlbumArt = false) ?: continue
                    val firstArtist = meta.artists.firstOrNull()
                    if (firstArtist != null) {
                        albumDao.updateAlbumArtist(albumId, firstArtist)
                    }
                }

                if (album.artPath == null) {
                    val meta = metadataParser.parse(filePath, extractAlbumArt = true) ?: continue
                    val bytes = meta.albumArtBytes ?: continue
                    val artFile = File(artDir, "${albumId}.jpg")
                    artFile.writeBytes(bytes)
                    albumDao.updateAlbumArt(albumId, artFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract for album $albumId", e)
            }
        }
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

}
