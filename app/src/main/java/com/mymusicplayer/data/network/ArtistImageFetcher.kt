package com.mymusicplayer.data.network

import android.content.Context
import android.net.Uri
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
        val t0 = System.currentTimeMillis()
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

            val t1 = System.currentTimeMillis()
            Log.d(TAG, "Connecting to $searchUrl ...")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "MuseMeta/1.0")
            }

            val responseCode = searchConn.responseCode
            val t2 = System.currentTimeMillis()
            Log.d(TAG, "Search response code $responseCode in ${t2 - t1}ms for '$artistName'")

            if (responseCode != 200) {
                searchConn.disconnect()
                Log.w(TAG, "Spotify search returned $responseCode for '$artistName' in ${t2 - t1}ms")
                return@withContext null
            }

            val body = searchConn.inputStream.bufferedReader().readText()
            val t3 = System.currentTimeMillis()
            Log.d(TAG, "Read search body (${body.length} bytes) in ${t3 - t2}ms for '$artistName'")

            searchConn.disconnect()

            val response = json.decodeFromString<SpotifySearchResponse>(body)
            val results = response.results
            if (results.isNullOrEmpty()) {
                Log.w(TAG, "No Spotify result for '$artistName' (took ${t3 - t0}ms total)")
                return@withContext null
            }

            val bestMatch = findBestMatch(artistName, results) ?: run {
                Log.w(TAG, "No acceptable Spotify match for '$artistName' among ${results.size} results (took ${t3 - t0}ms total)")
                return@withContext null
            }

            Log.d(TAG, "Best match: '${bestMatch.name}' (id=${bestMatch.id}) for '$artistName'")

            val imageUrl = bestMatch.thumbnail ?: run {
                Log.w(TAG, "No thumbnail for '${bestMatch.name}'")
                return@withContext null
            }

            Log.d(TAG, "Downloading image from $imageUrl ...")
            val imgUrl = URL(imageUrl)
            val imgConn = imgUrl.openConnection() as HttpURLConnection
            imgConn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "MuseMeta/1.0")
            }

            val imgResponseCode = imgConn.responseCode
            val t4 = System.currentTimeMillis()
            Log.d(TAG, "Image response code $imgResponseCode in ${t4 - t3}ms")

            if (imgResponseCode != 200) {
                imgConn.disconnect()
                Log.w(TAG, "Image download returned $imgResponseCode for '${bestMatch.name}' after ${t4 - t3}ms")
                return@withContext null
            }

            imgConn.inputStream.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }
            imgConn.disconnect()

            val t5 = System.currentTimeMillis()
            val total = t5 - t0
            Log.d(TAG, "Downloaded artist image for '${bestMatch.name}' (searched '$artistName') " +
                    "to ${cachedFile.absolutePath} in ${total}ms " +
                    "(search=${t3 - t1}ms, download=${t5 - t3}ms)")
            return@withContext cachedFile.absolutePath

        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - t0
            Log.e(TAG, "Failed to fetch artist image for '$artistName' after ${elapsed}ms", e)
            return@withContext null
        }
    }

    fun saveImageFromUri(uri: Uri, artistId: Long): String? {
        val file = getArtistArtFile(artistId)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Saved manual image for artist $artistId to ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save manual image for artist $artistId", e)
            null
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
