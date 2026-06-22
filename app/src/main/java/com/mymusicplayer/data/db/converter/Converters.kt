package com.mymusicplayer.data.db.converter

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromLongList(value: List<Long>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> {
        return if (value.isBlank()) emptyList()
        else value.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString("␟")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isBlank()) emptyList()
        else value.split("␟").map { it.trim() }
    }
}
