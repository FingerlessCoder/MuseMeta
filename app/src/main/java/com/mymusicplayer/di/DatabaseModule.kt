package com.mymusicplayer.di

import androidx.room.Room
import com.mymusicplayer.data.db.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { Room.databaseBuilder(androidContext(), AppDatabase::class.java, "musemeta.db").build() }
    single { get<AppDatabase>().trackDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().playlistDao() }
}
