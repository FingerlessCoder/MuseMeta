package com.mymusicplayer.ui.screens.settings

import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sortMode: String = "name",
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: String = "Normal",
    val minFileSize: Long = 0,
    val maxFileSize: Long = Long.MAX_VALUE,
    val volumeNormalization: Boolean = false,
    val sleepTimerMinutes: Int = 0,
    val scanDirectoryPath: String = ""
)

class SettingsViewModel constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.defaultSort.collect { sort ->
                _uiState.value = _uiState.value.copy(sortMode = sort)
            }
        }
        viewModelScope.launch {
            settingsDataStore.equalizerEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(equalizerEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settingsDataStore.equalizerPreset.collect { preset ->
                _uiState.value = _uiState.value.copy(equalizerPreset = preset)
            }
        }
        viewModelScope.launch {
            settingsDataStore.minFileSize.collect { min ->
                _uiState.value = _uiState.value.copy(minFileSize = min)
            }
        }
        viewModelScope.launch {
            settingsDataStore.maxFileSize.collect { max ->
                _uiState.value = _uiState.value.copy(maxFileSize = max)
            }
        }
        viewModelScope.launch {
            settingsDataStore.volumeNormalization.collect { norm ->
                _uiState.value = _uiState.value.copy(volumeNormalization = norm)
            }
        }
        viewModelScope.launch {
            settingsDataStore.sleepTimerDuration.collect { dur ->
                _uiState.value = _uiState.value.copy(sleepTimerMinutes = dur)
            }
        }
        viewModelScope.launch {
            settingsDataStore.scanDirectoryPath.collect { path ->
                _uiState.value = _uiState.value.copy(scanDirectoryPath = path ?: "")
            }
        }
    }

    fun setSortMode(sort: String) {
        viewModelScope.launch { settingsDataStore.setDefaultSort(sort) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setEqualizerEnabled(enabled) }
    }

    fun setEqualizerPreset(preset: String) {
        viewModelScope.launch { settingsDataStore.setEqualizerPreset(preset) }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setVolumeNormalization(enabled) }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch { settingsDataStore.setSleepTimerDuration(minutes) }
    }

    fun setScanDirectoryPath(path: String) {
        viewModelScope.launch { settingsDataStore.setScanDirectoryPath(path) }
        _uiState.value = _uiState.value.copy(scanDirectoryPath = path)
    }

    fun clearScanDirectoryPath() {
        viewModelScope.launch { settingsDataStore.setScanDirectoryPath(null) }
        _uiState.value = _uiState.value.copy(scanDirectoryPath = "")
    }

    fun canManageExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
