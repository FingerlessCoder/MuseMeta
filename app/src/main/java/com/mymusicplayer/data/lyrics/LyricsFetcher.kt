package com.mymusicplayer.data.lyrics

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class LrcLibResponse(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val duration: Double,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

sealed class LyricsFetchResult {
    data class Success(
        val syncedLrc: String?,
        val plainLyrics: String?,
        val isInstrumental: Boolean
    ) : LyricsFetchResult()
    data object NotFound : LyricsFetchResult()
    data class Error(val message: String) : LyricsFetchResult()
}

object LyricsCache {
    private const val TAG = "LyricsCache"

    private fun getLyricsDir(context: Context): File {
        val dir = File(context.cacheDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLyricsFile(context: Context, trackId: Long): File {
        return File(getLyricsDir(context), "${trackId}.lrc")
    }

    fun saveLyrics(context: Context, trackId: Long, lrcContent: String): File {
        val file = getLyricsFile(context, trackId)
        file.writeText(lrcContent)
        Log.d(TAG, "Saved lyrics for track $trackId to ${file.absolutePath}")
        return file
    }

    fun loadLyrics(context: Context, trackId: Long): String? {
        val file = getLyricsFile(context, trackId)
        return if (file.exists()) file.readText() else null
    }

    fun deleteLyrics(context: Context, trackId: Long) {
        val file = getLyricsFile(context, trackId)
        if (file.exists()) file.delete()
    }

    fun hasLyrics(context: Context, trackId: Long): Boolean {
        return getLyricsFile(context, trackId).exists()
    }
}

class LyricsFetcher(val context: Context) {

    companion object {
        private const val TAG = "LyricsFetcher"
        private const val BASE_URL = "https://lrclib.net/api"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 15_000
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationMs: Long
    ): LyricsFetchResult = withContext(Dispatchers.IO) {
        try {
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
            val durationSec = (durationMs / 1000.0).coerceAtLeast(1.0)

            val urlBuilder = StringBuilder()
                .append("$BASE_URL/get?artist_name=$encodedArtist&track_name=$encodedTrack")
            if (!albumName.isNullOrBlank()) {
                urlBuilder.append("&album_name=${URLEncoder.encode(albumName, "UTF-8")}")
            }
            urlBuilder.append("&duration=$durationSec")

            val url = URL(urlBuilder.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Lrclib-Client", "MuseMeta/1.0")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                connection.disconnect()
                return@withContext LyricsFetchResult.NotFound
            }
            if (responseCode != 200) {
                val errorMsg = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                connection.disconnect()
                return@withContext LyricsFetchResult.Error(errorMsg)
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val parsed = json.decodeFromString<LrcLibResponse>(responseBody)
            return@withContext LyricsFetchResult.Success(
                syncedLrc = parsed.syncedLyrics,
                plainLyrics = parsed.plainLyrics,
                isInstrumental = parsed.instrumental
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics", e)
            return@withContext LyricsFetchResult.Error(e.message ?: "Unknown error")
        }
    }
}
