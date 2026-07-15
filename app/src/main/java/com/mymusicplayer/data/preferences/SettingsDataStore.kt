package com.mymusicplayer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "musemeta_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val SCAN_COMPLETED_ONCE = booleanPreferencesKey("scan_completed_once")
        val SLEEP_TIMER_DURATION = intPreferencesKey("sleep_timer_duration")
        val DEFAULT_SORT = stringPreferencesKey("default_sort")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val MIN_FILE_SIZE = longPreferencesKey("min_file_size")
        val MAX_FILE_SIZE = longPreferencesKey("max_file_size")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")
        val LAST_PLAYED_TRACK_ID = longPreferencesKey("last_played_track_id")
        val LAST_PLAYED_POSITION = longPreferencesKey("last_played_position")
        val EXCLUDED_DIRS = stringPreferencesKey("excluded_dirs")
        val SCAN_DIRECTORY_PATH = stringPreferencesKey("scan_directory_path")
        val SCAN_MIN_FILE_SIZE = longPreferencesKey("scan_min_file_size_kb")
        val SCAN_MIN_DURATION = longPreferencesKey("scan_min_duration_sec")
        val AMOLED_BLACK_THEME = booleanPreferencesKey("amoled_black_theme")
        val ACCENT_COLOR_INDEX = intPreferencesKey("accent_color_index")
        val PLAYER_THEME = intPreferencesKey("player_theme")
    }

    val sleepTimerDuration: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SLEEP_TIMER_DURATION] ?: 0
    }

    val defaultSort: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_SORT] ?: "name"
    }

    val equalizerEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[EQUALIZER_ENABLED] ?: false
    }

    val equalizerPreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[EQUALIZER_PRESET] ?: "Normal"
    }

    val minFileSize: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[MIN_FILE_SIZE] ?: 0L
    }

    val maxFileSize: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[MAX_FILE_SIZE] ?: Long.MAX_VALUE
    }

    val volumeNormalization: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[VOLUME_NORMALIZATION] ?: false
    }

    val lastPlayedTrackId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_PLAYED_TRACK_ID]
    }

    val lastPlayedPosition: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_PLAYED_POSITION]
    }

    suspend fun setVolumeNormalization(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[VOLUME_NORMALIZATION] = enabled }
    }

    suspend fun setDefaultSort(sort: String) {
        context.dataStore.edit { prefs -> prefs[DEFAULT_SORT] = sort }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[EQUALIZER_ENABLED] = enabled }
    }

    suspend fun setEqualizerPreset(preset: String) {
        context.dataStore.edit { prefs -> prefs[EQUALIZER_PRESET] = preset }
    }

    suspend fun setFileSizeFilter(min: Long, max: Long) {
        context.dataStore.edit { prefs ->
            prefs[MIN_FILE_SIZE] = min
            prefs[MAX_FILE_SIZE] = max
        }
    }

    suspend fun setSleepTimerDuration(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[SLEEP_TIMER_DURATION] = minutes }
    }

    val scanCompletedOnce: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SCAN_COMPLETED_ONCE] ?: false
    }

    suspend fun setScanCompletedOnce() {
        context.dataStore.edit { prefs -> prefs[SCAN_COMPLETED_ONCE] = true }
    }

    val excludedDirs: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[EXCLUDED_DIRS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setExcludedDirs(dirs: List<String>) {
        context.dataStore.edit { prefs -> prefs[EXCLUDED_DIRS] = dirs.joinToString("|") }
    }

    val scanDirectoryPath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SCAN_DIRECTORY_PATH]?.takeIf { it.isNotBlank() }
    }

    val scanMinFileSize: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[SCAN_MIN_FILE_SIZE] ?: 2048L
    }

    val scanMinDuration: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[SCAN_MIN_DURATION] ?: 40L
    }

    val amoledBlackTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AMOLED_BLACK_THEME] ?: false
    }

    val accentColorIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[ACCENT_COLOR_INDEX] ?: 0
    }

    val playerTheme: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PLAYER_THEME] ?: 0
    }

    suspend fun setAmoledBlackTheme(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AMOLED_BLACK_THEME] = enabled }
    }

    suspend fun setAccentColorIndex(index: Int) {
        context.dataStore.edit { prefs -> prefs[ACCENT_COLOR_INDEX] = index }
    }

    suspend fun setPlayerTheme(theme: Int) {
        context.dataStore.edit { prefs -> prefs[PLAYER_THEME] = theme }
    }

    suspend fun setScanFileSizeFilter(minKb: Long) {
        context.dataStore.edit { prefs ->
            prefs[SCAN_MIN_FILE_SIZE] = minKb
        }
    }

    suspend fun setScanDurationFilter(minSec: Long) {
        context.dataStore.edit { prefs ->
            prefs[SCAN_MIN_DURATION] = minSec
        }
    }

    suspend fun setScanDirectoryPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(SCAN_DIRECTORY_PATH)
            } else {
                prefs[SCAN_DIRECTORY_PATH] = path
            }
        }
    }

    suspend fun savePlaybackState(trackId: Long, position: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_PLAYED_TRACK_ID] = trackId
            prefs[LAST_PLAYED_POSITION] = position
        }
    }

    suspend fun clearPlaybackState() {
        context.dataStore.edit { prefs ->
            prefs.remove(LAST_PLAYED_TRACK_ID)
            prefs.remove(LAST_PLAYED_POSITION)
        }
    }
}
