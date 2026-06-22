package com.mymusicplayer.domain.model

data class Track(
    val id: Long,
    val title: String,
    val artists: List<Artist>,
    val album: Album?,
    val duration: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val comment: String?,
    val filePath: String,
    val fileSize: Long,
    val format: String?,
    val dateAdded: Long,
    val lastPlayed: Long?,
    val playCount: Int,
    val rating: Int,
    val lyricsPath: String?,
    val rawArtistTag: String?
)
