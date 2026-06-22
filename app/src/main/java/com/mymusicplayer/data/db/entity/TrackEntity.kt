package com.mymusicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["album_id"]),
        Index(value = ["file_path"], unique = true),
        Index(value = ["title"]),
        Index(value = ["date_added"])
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "album_id")
    val albumId: Long? = null,

    @ColumnInfo(name = "duration")
    val duration: Long = 0,

    @ColumnInfo(name = "track_number")
    val trackNumber: Int? = null,

    @ColumnInfo(name = "disc_number")
    val discNumber: Int? = null,

    @ColumnInfo(name = "year")
    val year: Int? = null,

    @ColumnInfo(name = "genre")
    val genre: String? = null,

    @ColumnInfo(name = "comment")
    val comment: String? = null,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,

    @ColumnInfo(name = "bitrate")
    val bitrate: Int? = null,

    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int? = null,

    @ColumnInfo(name = "format")
    val format: String? = null,

    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null,

    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,

    @ColumnInfo(name = "rating")
    val rating: Int = 0,

    @ColumnInfo(name = "lyrics_path")
    val lyricsPath: String? = null,

    @ColumnInfo(name = "raw_artist_tag")
    val rawArtistTag: String? = null
)
