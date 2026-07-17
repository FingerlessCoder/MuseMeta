package com.mymusicplayer.data.lyrics

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LrcParseResult(
    val lines: List<LyricLine>,
    val metadata: Map<String, String> = emptyMap()
)

object LrcParser {

    private val LINE_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[\.:](\d{2,3}))?\](.*)""")
    private val METADATA_REGEX = Regex("""\[([a-z]+):(.*)\]""", RegexOption.IGNORE_CASE)

    fun parse(lrcContent: String): LrcParseResult {
        if (lrcContent.isBlank()) return LrcParseResult(emptyList())

        val rawLines = lrcContent.lines()
        val lyricLines = mutableListOf<LyricLine>()
        val metadata = mutableMapOf<String, String>()

        var offsetMs = 0L

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val metaMatch = METADATA_REGEX.matchEntire(trimmed)
            if (metaMatch != null) {
                val key = metaMatch.groupValues[1].lowercase()
                val value = metaMatch.groupValues[2].trim()
                metadata[key] = value
                if (key == "offset") {
                    offsetMs = value.toLongOrNull() ?: 0L
                }
                continue
            }

            val match = LINE_REGEX.matchEntire(trimmed)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val centis = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                val text = match.groupValues[4].trim()

                var timestampMs = minutes * 60_000L + seconds * 1000L + centis
                if (offsetMs != 0L) {
                    timestampMs = (timestampMs + offsetMs).coerceAtLeast(0L)
                }

                lyricLines.add(LyricLine(timestampMs, text))
            }
        }

        lyricLines.sortWith(compareBy({ it.timestampMs }, { it.text }))

        return LrcParseResult(lyricLines.toList(), metadata)
    }

    /**
     * Binary search to find the current lyric line index for a given playback position.
     * Returns -1 if position is before any lyric line.
     * Returns the index of the line whose timestamp <= position < next line's timestamp.
     * Returns last index if position is after all lines.
     */
    fun findLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (positionMs < lines.first().timestampMs) return -1
        if (positionMs >= lines.last().timestampMs) return lines.lastIndex

        var low = 0
        var high = lines.lastIndex

        while (low < high) {
            val mid = (low + high + 1) / 2
            if (lines[mid].timestampMs <= positionMs) {
                low = mid
            } else {
                high = mid - 1
            }
        }

        return low
    }
}
