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
    val duration: Double? = null,
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
            val query = URLEncoder.encode("$artistName $trackName", "UTF-8")
            val searchUrl = URL("$BASE_URL/search?q=$query")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.apply {
                requestMethod = "GET"
                setRequestProperty("Lrclib-Client", "MuseMeta/1.0")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            val searchResponseCode = searchConn.responseCode
            if (searchResponseCode != 200) {
                searchConn.disconnect()
                if (searchResponseCode == 404) return@withContext LyricsFetchResult.NotFound
                val errorMsg = searchConn.errorStream?.bufferedReader()?.readText() ?: "HTTP $searchResponseCode"
                return@withContext LyricsFetchResult.Error(errorMsg)
            }

            val searchBody = searchConn.inputStream.bufferedReader().readText()
            searchConn.disconnect()

            val results = json.decodeFromString<List<LrcLibResponse>>(searchBody)
            if (results.isEmpty()) return@withContext LyricsFetchResult.NotFound

            val bestMatch = pickBestResult(results, trackName, artistName, durationMs)
            if (bestMatch != null) {
                return@withContext LyricsFetchResult.Success(
                    syncedLrc = bestMatch.syncedLyrics,
                    plainLyrics = bestMatch.plainLyrics,
                    isInstrumental = bestMatch.instrumental
                )
            }

            return@withContext LyricsFetchResult.NotFound

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch lyrics", e)
            return@withContext LyricsFetchResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Pick the best result from a list of candidates.
     * 1. Prefer results with synced lyrics.
     * 2. Among those, prefer exact-ish title/artist match.
     * 3. Among those, prefer closer duration.
     */
    private fun pickBestResult(
        results: List<LrcLibResponse>,
        trackName: String,
        artistName: String,
        durationMs: Long
    ): LrcLibResponse? {
        val durationSec = durationMs / 1000.0
        val normalizedTrack = trackName.lowercase().trim()
        val normalizedArtist = artistName.lowercase().trim()

        fun score(result: LrcLibResponse): Int {
            var s = 0
            // +3 for synced lyrics
            if (result.syncedLyrics != null) s += 3
            // +1 for plain lyrics (tiebreaker if no synced)
            if (result.plainLyrics != null) s += 1
            // +2 if track name matches closely
            if (result.trackName.lowercase().trim() == normalizedTrack) s += 2
            else if (normalizedTrack.contains(result.trackName.lowercase().trim()) ||
                result.trackName.lowercase().trim().contains(normalizedTrack)) s += 1
            // +2 if artist matches closely
            if (result.artistName.lowercase().trim() == normalizedArtist) s += 2
            else if (normalizedArtist.contains(result.artistName.lowercase().trim()) ||
                result.artistName.lowercase().trim().contains(normalizedArtist)) s += 1
            // +1 if duration is within 5 seconds
            if (result.duration != null && kotlin.math.abs(result.duration - durationSec) <= 5.0) s += 1
            return s
        }

        return results.maxByOrNull { score(it) }
    }
}
