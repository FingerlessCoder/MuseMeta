package com.mymusicplayer.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

data class ScannedAudioFile(
    val uri: Uri,
    val path: String,
    val size: Long,
    val dateModified: Long,
    // Metadata fields — populated by MediaStoreScanner, null for FileSystemScanner
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val duration: Long? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val bitrate: Int? = null,
    val mimeType: String? = null
) {
    val hasEmbeddedMetadata: Boolean get() = title != null
}

class MediaStoreScanner(
    private val contentResolver: ContentResolver
) {

    companion object {
        private const val TAG = "MediaStoreScanner"

        private val SUPPORTED_MIME_TYPES = setOf(
            "audio/mpeg",
            "audio/flac",
            "audio/mp4",
            "audio/ogg",
            "audio/wav",
            "audio/x-wav",
            "audio/x-flac"
        )

        private const val MIN_FILE_SIZE_BYTES = 10_000L
    }

    fun scanAll(): List<ScannedAudioFile> {
        val results = mutableListOf<ScannedAudioFile>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.BITRATE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val bitrateCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)

            while (cursor.moveToNext()) {
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                if (mime != null && mime !in SUPPORTED_MIME_TYPES) continue

                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (path == null || path.isBlank()) continue

                val file = File(path)
                if (!file.exists() || !file.canRead()) continue

                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                if (size < MIN_FILE_SIZE_BYTES) continue

                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val uri = Uri.withAppendedPath(collectionUri, id.toString())

                val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                val artist = if (artistCol >= 0) cursor.getString(artistCol) else null
                val album = if (albumCol >= 0) cursor.getString(albumCol) else null
                val albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null
                val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                val trackRaw = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                val yearRaw = if (yearCol >= 0) cursor.getString(yearCol) else null
                val bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0

                results.add(
                    ScannedAudioFile(
                        uri = uri,
                        path = path,
                        size = size,
                        dateModified = date,
                        title = title?.takeIf { it.isNotBlank() },
                        artist = artist?.takeIf { it.isNotBlank() },
                        album = album?.takeIf { it.isNotBlank() },
                        albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                        duration = if (durationMs > 0L) durationMs else null,
                        trackNumber = if (trackRaw > 0) trackRaw else null,
                        year = yearRaw?.toIntOrNull(),
                        genre = null, // Genre requires a sub-query; skip for performance
                        bitrate = if (bitrate > 0) bitrate else null,
                        mimeType = mime
                    )
                )
            }
        } ?: Log.w(TAG, "MediaStore query returned null cursor")

        Log.d(TAG, "Found ${results.size} audio files")
        return results
    }

    fun scanIncremental(lastScanTime: Long): List<ScannedAudioFile> {
        val results = mutableListOf<ScannedAudioFile>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.BITRATE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "${MediaStore.Audio.Media.DATE_MODIFIED} > ?"

        contentResolver.query(
            collectionUri,
            projection,
            selection,
            arrayOf(lastScanTime.toString()),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val bitrateCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)

            while (cursor.moveToNext()) {
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                if (mime != null && mime !in SUPPORTED_MIME_TYPES) continue

                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (path == null || path.isBlank()) continue
                if (!File(path).exists()) continue

                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                if (size < MIN_FILE_SIZE_BYTES) continue

                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val uri = Uri.withAppendedPath(collectionUri, id.toString())

                val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                val artist = if (artistCol >= 0) cursor.getString(artistCol) else null
                val album = if (albumCol >= 0) cursor.getString(albumCol) else null
                val albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null
                val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                val trackRaw = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                val yearRaw = if (yearCol >= 0) cursor.getString(yearCol) else null
                val bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0

                results.add(
                    ScannedAudioFile(
                        uri = uri,
                        path = path,
                        size = size,
                        dateModified = date,
                        title = title?.takeIf { it.isNotBlank() },
                        artist = artist?.takeIf { it.isNotBlank() },
                        album = album?.takeIf { it.isNotBlank() },
                        albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                        duration = if (durationMs > 0L) durationMs else null,
                        trackNumber = if (trackRaw > 0) trackRaw else null,
                        year = yearRaw?.toIntOrNull(),
                        genre = null,
                        bitrate = if (bitrate > 0) bitrate else null,
                        mimeType = mime
                    )
                )
            }
        }

        Log.d(TAG, "Incremental scan found ${results.size} new/changed files")
        return results
    }

    /**
     * Find directories that contain audio files by querying MediaStore and
     * grouping results by parent directory. No filesystem I/O needed.
     */
    fun findDirectoriesWithAudio(): List<DirectoryAudioInfo> {
        val dirCounts = mutableMapOf<String, Int>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(collectionUri, projection, selection, null, null)?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol < 0) return@use
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val dir = File(path).parentFile ?: continue
                val dirPath = dir.absolutePath
                dirCounts[dirPath] = (dirCounts[dirPath] ?: 0) + 1
            }
        }

        return dirCounts.map { (path, count) ->
            DirectoryAudioInfo(
                name = File(path).name,
                path = path,
                audioCount = count
            )
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Scan audio files within specific directory paths by querying MediaStore
     * with path-prefix matching. Results include embedded metadata — no file I/O.
     */
    fun scanByPaths(directoryPaths: List<String>): List<ScannedAudioFile> {
        if (directoryPaths.isEmpty()) return emptyList()

        val results = mutableListOf<ScannedAudioFile>()
        val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.BITRATE
        )

        // Path-prefix matching: DATA LIKE '/path/%'
        val pathClauses = directoryPaths.map { path ->
            val escaped = path.replace("'", "''")
            "${MediaStore.Audio.Media.DATA} LIKE '${escaped}/%'"
        }
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0) AND (${pathClauses.joinToString(" OR ")})"

        contentResolver.query(collectionUri, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val bitrateCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)

            while (cursor.moveToNext()) {
                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                if (path == null || path.isBlank()) continue
                if (!File(path).exists()) continue

                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                if (size < MIN_FILE_SIZE_BYTES) continue

                val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                val uri = Uri.withAppendedPath(collectionUri, id.toString())

                val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                val artist = if (artistCol >= 0) cursor.getString(artistCol) else null
                val album = if (albumCol >= 0) cursor.getString(albumCol) else null
                val albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null
                val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                val trackRaw = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                val yearRaw = if (yearCol >= 0) cursor.getString(yearCol) else null
                val bitrate = if (bitrateCol >= 0) cursor.getInt(bitrateCol) else 0

                results.add(
                    ScannedAudioFile(
                        uri = uri,
                        path = path,
                        size = size,
                        dateModified = date,
                        title = title?.takeIf { it.isNotBlank() },
                        artist = artist?.takeIf { it.isNotBlank() },
                        album = album?.takeIf { it.isNotBlank() },
                        albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                        duration = if (durationMs > 0L) durationMs else null,
                        trackNumber = if (trackRaw > 0) trackRaw else null,
                        year = yearRaw?.toIntOrNull(),
                        genre = null,
                        bitrate = if (bitrate > 0) bitrate else null,
                        mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    )
                )
            }
        }

        return results
    }
}
