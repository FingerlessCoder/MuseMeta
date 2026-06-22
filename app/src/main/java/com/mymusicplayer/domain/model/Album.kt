package com.mymusicplayer.domain.model

data class Album(
    val id: Long,
    val title: String,
    val albumArtist: String?,
    val year: Int?,
    val artPath: String?,
    val trackCount: Int = 0,
    val totalDuration: Long = 0
)
