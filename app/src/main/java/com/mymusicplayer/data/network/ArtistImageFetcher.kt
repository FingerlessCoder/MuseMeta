package com.mymusicplayer.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Data classes for MusicBrainz Artist Search response.
 * GET /ws/2/artist/?query=artist:{name}&fmt=json&limit=3
 */
@Serializable
data class MusicBrainzSearchResponse(
    val artists: List<MusicBrainzArtist>? = null
)

@Serializable
data class MusicBrainzArtist(
    val id: String? = null,
    val name: String? = null,
    val score: Int = 0
)

/**
 * Data classes for MusicBrainz Artist Detail (with URL relations).
 * GET /ws/2/artist/{mbid}?inc=url-rels&fmt=json
 */
@Serializable
data class MusicBrainzArtistDetail(
    val relations: List<MusicBrainzRelation>? = null
)

@Serializable
data class MusicBrainzRelation(
    val type: String? = null,
    val url: MusicBrainzUrl? = null
)

@Serializable
data class MusicBrainzUrl(
    val resource: String? = null
)

/**
 * Data class for Spotify oEmbed response.
 * GET https://open.spotify.com/oembed?url=...
 */
@Serializable
data class SpotifyOEmbedResponse(
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,

    @SerialName("title")
    val title: String? = null
)

class ArtistImageFetcher(private val context: Context) {

    companion object {
        private const val TAG = "ArtistImageFetcher"
        private const val MUSICBRAINZ_API = "https://musicbrainz.org/ws/2"
        private const val SPOTIFY_OEMBED = "https://open.spotify.com/oembed"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 15_000
        private const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val USER_AGENT = "MuseMeta/1.0 (musicplayer)"
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

    /** Fetch JSON text from a URL with common headers. */
    private fun fetchJson(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val code = conn.responseCode
        if (code != 200) {
            val body = try { conn.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
            conn.disconnect()
            throw java.io.IOException("HTTP $code from ${url.host} — $body")
        }
        return conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
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

            // ── Step 1: Search MusicBrainz for the artist ──
            val t1 = System.currentTimeMillis()
            val searchUrl = URL("$MUSICBRAINZ_API/artist/?query=artist:$encodedName&fmt=json&limit=3")
            Log.d(TAG, "Searching MusicBrainz for '$artistName' ...")
            val searchBody = fetchJson(searchUrl)
            val searchResult = json.decodeFromString<MusicBrainzSearchResponse>(searchBody)
            val mbid = searchResult.artists?.firstOrNull()?.id
            if (mbid == null) {
                Log.w(TAG, "No MusicBrainz result for '$artistName'")
                return@withContext null
            }
            val t2 = System.currentTimeMillis()
            Log.d(TAG, "MusicBrainz search found MBID $mbid for '$artistName' in ${t2 - t1}ms")

            // ── Step 2: Get artist relations (Spotify URL) ──
            delay(1000) // MusicBrainz rate limit: 1 req/s
            val relUrl = URL("$MUSICBRAINZ_API/artist/$mbid?inc=url-rels&fmt=json")
            Log.d(TAG, "Fetching relations for MBID $mbid ...")
            val relBody = fetchJson(relUrl)
            val artistDetail = json.decodeFromString<MusicBrainzArtistDetail>(relBody)
            val spotifyUrl = artistDetail.relations
                ?.firstOrNull { rel ->
                    rel.type == "free streaming" &&
                            rel.url?.resource?.contains("open.spotify.com/artist/") == true
                }
                ?.url?.resource
            if (spotifyUrl == null) {
                Log.w(TAG, "No Spotify URL in MusicBrainz relations for '$artistName'")
                return@withContext null
            }
            val spotifyId = spotifyUrl.substringAfterLast("/")
            val t3 = System.currentTimeMillis()
            Log.d(TAG, "Found Spotify artist ID $spotifyId in ${t3 - t2}ms")

            // ── Step 3: Get oEmbed thumbnail_url ──
            val oembedUrl = URL("$SPOTIFY_OEMBED?url=https://open.spotify.com/artist/$spotifyId")
            Log.d(TAG, "Fetching oEmbed for Spotify ID $spotifyId ...")
            val oembedBody = fetchJson(oembedUrl)
            val oembed = json.decodeFromString<SpotifyOEmbedResponse>(oembedBody)
            val imageUrl = oembed.thumbnailUrl
            if (imageUrl == null) {
                Log.w(TAG, "No thumbnail_url in oEmbed response for '$artistName'")
                return@withContext null
            }
            val t4 = System.currentTimeMillis()
            Log.d(TAG, "Got oEmbed thumbnail in ${t4 - t3}ms")

            // ── Step 4: Download the image ──
            Log.d(TAG, "Downloading artist image from $imageUrl ...")
            val imgConn = URL(imageUrl).openConnection() as HttpURLConnection
            imgConn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val imgCode = imgConn.responseCode
            if (imgCode != 200) {
                imgConn.disconnect()
                Log.w(TAG, "Image download returned $imgCode")
                return@withContext null
            }
            imgConn.inputStream.use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }
            imgConn.disconnect()

            val total = System.currentTimeMillis() - t0
            Log.d(TAG, "Downloaded artist image for '$artistName' to ${cachedFile.absolutePath} in ${total}ms")
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


}
