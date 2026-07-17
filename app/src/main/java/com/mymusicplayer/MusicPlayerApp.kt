package com.mymusicplayer

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.mymusicplayer.di.appModule
import com.mymusicplayer.di.databaseModule
import org.jaudiotagger.tag.TagOptionSingleton
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MusicPlayerApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        TagOptionSingleton.getInstance().isAndroid = true
        startKoin {
            androidContext(this@MusicPlayerApp)
            modules(appModule, databaseModule)
        }
    }
}
