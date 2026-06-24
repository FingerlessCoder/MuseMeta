package com.mymusicplayer.data.scanner

import android.util.Log
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import java.io.File

data class ParsedMetadata(
    val title: String,
    val artists: List<String>,
    val albumTitle: String?,
    val albumArtist: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val genre: String?,
    val comment: String?,
    val duration: Long,
    val bitrate: Int?,
    val sampleRate: Int?,
    val format: String?,
    val albumArtBytes: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedMetadata) return false
        return title == other.title &&
                artists == other.artists &&
                albumTitle == other.albumTitle &&
                duration == other.duration &&
                format == other.format
    }

    override fun hashCode(): Int {
        return title.hashCode() + artists.hashCode() + (albumTitle?.hashCode() ?: 0)
    }
}

class MetadataParser constructor() {

    companion object {
        private const val TAG = "MetadataParser"

        private val ARTIST_DELIMITERS = listOf(
            " feat. ", " ft. ", " featuring ",
            " & ", " and ",
            " / ", " \\ ",
            ", ", "; "
        )
    }

    fun parse(filePath: String, extractAlbumArt: Boolean = true): ParsedMetadata? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "File not accessible: $filePath")
                return null
            }

            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag: Tag? = audioFile.tag
            val audioHeader = audioFile.audioHeader

            val title = extractTag(tag, FieldKey.TITLE) ?: file.nameWithoutExtension
            val rawArtist = extractTag(tag, FieldKey.ARTIST) ?: "Unknown Artist"
            val artists = parseArtists(rawArtist)
            val albumTitle = extractTag(tag, FieldKey.ALBUM)
            val albumArtist = extractTag(tag, FieldKey.ALBUM_ARTIST)
            val year = extractTag(tag, FieldKey.YEAR)?.toIntOrNull()
            val trackNum = extractTag(tag, FieldKey.TRACK)?.toIntOrNull()
            val discNum = extractTag(tag, FieldKey.DISC_NO)?.toIntOrNull()
            val genre = extractTag(tag, FieldKey.GENRE)
            val comment = extractTag(tag, FieldKey.COMMENT)
            val duration = audioHeader.trackLength.toLong() * 1000L
            val bitrate = audioHeader.bitRateAsNumber.toInt()
            val sampleRate = audioHeader.sampleRateAsNumber.toInt()
            val format = audioHeader.format
            val albumArt = if (extractAlbumArt) extractAlbumArt(tag) else null

            ParsedMetadata(
                title = title,
                artists = artists,
                albumTitle = albumTitle,
                albumArtist = albumArtist,
                year = year,
                trackNumber = trackNum,
                discNumber = discNum,
                genre = genre,
                comment = comment,
                duration = duration,
                bitrate = bitrate,
                sampleRate = sampleRate,
                format = format,
                albumArtBytes = albumArt
            )
        } catch (e: CannotReadException) {
            Log.w(TAG, "Cannot read audio file: $filePath — ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing metadata for $filePath: ${e.message}")
            null
        }
    }

    fun writeMetadata(
        filePath: String,
        title: String? = null,
        artists: List<String>? = null,
        albumTitle: String? = null,
        year: Int? = null,
        trackNumber: Int? = null,
        genre: String? = null,
        comment: String? = null
    ): Boolean {
        return try {
            val file = File(filePath)
            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return false

            title?.let { tag.setField(tag.createField(FieldKey.TITLE, it)) }
            artists?.let { tag.setField(tag.createField(FieldKey.ARTIST, it.joinToString(" / "))) }
            albumTitle?.let { tag.setField(tag.createField(FieldKey.ALBUM, it)) }
            year?.let { tag.setField(tag.createField(FieldKey.YEAR, it.toString())) }
            trackNumber?.let { tag.setField(tag.createField(FieldKey.TRACK, it.toString())) }
            genre?.let { tag.setField(tag.createField(FieldKey.GENRE, it)) }
            comment?.let { tag.setField(tag.createField(FieldKey.COMMENT, it)) }

            audioFile.commit()
            Log.d(TAG, "Metadata written to: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing metadata to $filePath: ${e.message}")
            false
        }
    }

    private fun extractTag(tag: Tag?, fieldKey: FieldKey): String? {
        return try {
            tag?.getFirst(fieldKey)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    internal fun parseArtists(rawArtist: String): List<String> {
        val normalized = rawArtist.trim()
        val result = mutableListOf<String>()
        var remaining = normalized

        while (remaining.isNotBlank()) {
            var bestMatch: Pair<Int, String>? = null

            for (delimiter in ARTIST_DELIMITERS) {
                val idx = remaining.indexOf(delimiter, ignoreCase = true)
                if (idx >= 0) {
                    val candidate = Pair(idx, delimiter)
                    if (bestMatch == null || idx < bestMatch.first) {
                        bestMatch = candidate
                    }
                }
            }

            if (bestMatch != null) {
                val name = remaining.substring(0, bestMatch.first).trim()
                if (name.isNotBlank()) {
                    result.add(name)
                }
                remaining = remaining.substring(bestMatch.first + bestMatch.second.length).trim()
            } else {
                val name = remaining.trim()
                if (name.isNotBlank()) {
                    result.add(name)
                }
                remaining = ""
            }
        }

        return result.distinct().ifEmpty { listOf(normalized) }
    }

    private fun extractAlbumArt(tag: Tag?): ByteArray? {
        return try {
            val artwork: Artwork? = tag?.firstArtwork
            artwork?.binaryData
        } catch (e: Exception) {
            null
        }
    }
}
