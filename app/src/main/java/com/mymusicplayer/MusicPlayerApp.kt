package com.mymusicplayer

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mymusicplayer.di.appModule
import com.mymusicplayer.di.databaseModule
import org.jaudiotagger.tag.TagOptionSingleton
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MusicPlayerApp : Application(), ImageLoaderFactory {
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_disk_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            // Cache decoded album art in memory + disk so list rows don't re-decode
            // (and re-read from disk) the same art on every scroll reveal.
            .build()
    }
}
