package com.mymusicplayer.data.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MetadataParserTest {

    private lateinit var parser: MetadataParser

    @BeforeEach
    fun setUp() {
        parser = MetadataParser()
    }

    @Test
    fun `parseArtists with single artist returns that artist`() {
        val result = parser.parseArtists("John Doe")
        assertEquals(listOf("John Doe"), result)
    }

    @Test
    fun `parseArtists with feat delimiter splits correctly`() {
        val result = parser.parseArtists("John Smith feat. Jane Doe")
        assertEquals(listOf("John Smith", "Jane Doe"), result)
    }

    @Test
    fun `parseArtists with ft delimiter splits correctly`() {
        val result = parser.parseArtists("Artist A ft. Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with ampersand splits correctly`() {
        val result = parser.parseArtists("Artist A & Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with forward slash splits correctly`() {
        val result = parser.parseArtists("Artist A / Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with comma splits correctly`() {
        val result = parser.parseArtists("Artist A, Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with semicolon splits correctly`() {
        val result = parser.parseArtists("Artist A; Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with multiple delimiters splits all`() {
        val result = parser.parseArtists("Artist A feat. Artist B & Artist C")
        assertEquals(listOf("Artist A", "Artist B", "Artist C"), result)
    }

    @Test
    fun `parseArtists with featuring word splits correctly`() {
        val result = parser.parseArtists("Artist A featuring Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with and word splits correctly`() {
        val result = parser.parseArtists("Artist A and Artist B")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with three artists parses all`() {
        val result = parser.parseArtists("Artist A / Artist B / Artist C")
        assertEquals(listOf("Artist A", "Artist B", "Artist C"), result)
    }

    @Test
    fun `parseArtists trims whitespace`() {
        val result = parser.parseArtists("  Artist A   feat.   Artist B  ")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }

    @Test
    fun `parseArtists with empty string returns unknown`() {
        val result = parser.parseArtists("")
        assertEquals(listOf(""), result)
    }

    @Test
    fun `parseArtists with no delimiter returns single`() {
        val result = parser.parseArtists("Queen")
        assertEquals(listOf("Queen"), result)
    }

    @Test
    fun `parseArtists with three artists using commas`() {
        val result = parser.parseArtists("A, B, C")
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun `parseArtists deduplicates`() {
        val result = parser.parseArtists("A / A / B")
        assertEquals(listOf("A", "B"), result)
    }
}
