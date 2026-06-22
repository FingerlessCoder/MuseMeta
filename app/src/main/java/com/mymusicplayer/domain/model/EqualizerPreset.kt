package com.mymusicplayer.domain.model

data class EqualizerPreset(
    val id: Long,
    val name: String,
    val bands: List<Int>,
    val isBuiltin: Boolean
)
