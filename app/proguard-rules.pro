# MuseMeta ProGuard Rules

# Keep jAudiotagger
-keep class org.jaudiotagger.** { *; }

# Keep Room entities
-keep class com.mymusicplayer.data.db.entity.** { *; }

# Keep serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Missing AWT classes from jAudiotagger
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
