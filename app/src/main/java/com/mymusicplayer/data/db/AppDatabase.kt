package com.mymusicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mymusicplayer.data.db.converter.Converters
import com.mymusicplayer.data.db.dao.AlbumDao
import com.mymusicplayer.data.db.dao.ArtistDao
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.AlbumEntity
import com.mymusicplayer.data.db.entity.ArtistEntity
import com.mymusicplayer.data.db.entity.EqualizerPresetEntity
import com.mymusicplayer.data.db.entity.PlaylistEntity
import com.mymusicplayer.data.db.entity.PlaylistEntryEntity
import com.mymusicplayer.data.db.entity.TrackArtistEntity
import com.mymusicplayer.data.db.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        ArtistEntity::class,
        TrackArtistEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        EqualizerPresetEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
}
