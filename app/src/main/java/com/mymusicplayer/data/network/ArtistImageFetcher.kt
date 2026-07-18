package com.mymusicplayer.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class SpotifySearchResponse(
    val success: Boolean = false,
    val results: List<SpotifyArtistResult>? = null
)

@Serializable
data class SpotifyArtistResult(
    val id: String? = null,
    val name: String? = null,
    val thumbnail: String? = null
)

class ArtistImageFetcher(private val context: Context) {

    companion object {
        private const val TAG = "ArtistImageFetcher"
        private const val SPOTIFY_API = "https://spotify.xwolf.space/api/search"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 15_000
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private val json = Json { ignoreUnknownKeys = true }
    }

    private fun getArtistArtDir(): File {
        val dir = File(context.cacheDir, "artist_art")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getArtistArtFile(artistId: Long): File {
        return File(getArtistArtDir(), "${artistId}.jpg")
    }

    private fun isCacheValid(file: File): Boolean {
        if (!file.exists()) return false
        val age = System.currentTimeMillis() - file.lastModified()
        return age < CACHE_TTL_MS
    }

    suspend fun fetchArtistImage(artistName: String, artistId: Long): String? = withContext(Dispatchers.IO) {
        val cachedFile = getArtistArtFile(artistId)
        if (isCacheValid(cachedFile)) {
            Log.d(TAG, "Cache hit for artist $artistId ($artistName)")
            return@withContext cachedFile.absolutePath
        }
        if (cachedFile.exists()) {
            cachedFile.delete()
            Log.d(TAG, "Cache expired for artist $artistId ($artistName), re-fetching")
        }

        try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            val searchUrl = URL("$SPOTIFY_API?q=$encodedName&type=artist&limit=5")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "MuseMeta/1.0")
            }

            val responseCode = searchConn.responseCode
            if (responseCode != 200) {
                searchConn.disconnect()
                Log.w(TAG, "Spotify search returned $responseCode for '$artistName'")
                return@withContext null
            }

            val body = searchConn.inputStream.bufferedReader().readText()
            searchConn.disconnect()

            val response = json.decodeFromString<SpotifySearchResponse>(body)
            val results = response.results
            if (results.isNullOrEmpty()) {
                Log.w(TAG, "No Spotify result for '$artistName'")
                return@withContext null
            }

            val bestMatch = findBestMatch(artistName, results) ?: run {
                Log.w(TAG, "No acceptable Spotify match for '$artistName'")
                return@withContext null
            }

            val imageUrl = bestMatch.thumbnail ?: run {
                Log.w(TAG, "No thumbnail for '${bestMatch.name}'")
                return@withContext null
            }

            val imgUrl = URL(imageUrl)
            val imgConn = imgUrl.openConnection() as HttpURLConnection
            imgConn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "MuseMeta/1.0")
            }

            if (imgConn.responseCode != 200) {
                imgConn.disconnect()
                Log.w(TAG, "Image download returned ${imgConn.responseCode} for '${bestMatch.name}'")
                return@withContext null
            }

            imgConn.inputStream.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }
            imgConn.disconnect()

            Log.d(TAG, "Downloaded artist image for '${bestMatch.name}' (searched '$artistName') to ${cachedFile.absolutePath}")
            return@withContext cachedFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist image for '$artistName'", e)
            return@withContext null
        }
    }

    private fun findBestMatch(query: String, results: List<SpotifyArtistResult>): SpotifyArtistResult? {
        val normalizedQuery = query.lowercase().trim()

        val exact = results.firstOrNull {
            it.name?.lowercase()?.trim() == normalizedQuery
        }
        if (exact != null) return exact

        val startsWith = results.firstOrNull {
            it.name?.lowercase()?.trim()?.startsWith(normalizedQuery) == true
        }
        if (startsWith != null) return startsWith

        val contains = results.firstOrNull {
            it.name?.lowercase()?.trim()?.contains(normalizedQuery) == true ||
                    normalizedQuery.contains(it.name?.lowercase()?.trim() ?: "")
        }
        if (contains != null) return contains

        return results.firstOrNull { it.thumbnail != null }
    }
}
