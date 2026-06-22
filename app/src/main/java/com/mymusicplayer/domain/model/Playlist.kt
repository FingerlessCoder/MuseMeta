package com.mymusicplayer.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val description: String?,
    val isSmart: Boolean,
    val smartRuleJson: String?,
    val trackCount: Int = 0
)
