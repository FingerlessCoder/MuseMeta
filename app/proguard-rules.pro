# MuseMeta ProGuard Rules

# Keep jAudiotagger
-keep class org.jaudiotagger.** { *; }

# Keep Room entities
-keep class com.mymusicplayer.data.db.entity.** { *; }

# Keep serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Keep Hilt-generated classes in primary dex
-keep class com.mymusicplayer.MusicPlayerApp { *; }
-keep class com.mymusicplayer.Hilt_MusicPlayerApp { *; }
-keep class com.mymusicplayer.Hilt_MainActivity { *; }
-keep class com.mymusicplayer.service.Hilt_MusicService { *; }
-keep class com.mymusicplayer.service.Hilt_ScanService { *; }
-dontwarn com.mymusicplayer.Hilt_MainActivity
-dontwarn com.mymusicplayer.Hilt_MusicPlayerApp
-dontwarn com.mymusicplayer.service.Hilt_MusicService
-dontwarn com.mymusicplayer.service.Hilt_ScanService

# Missing AWT classes from jAudiotagger
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
