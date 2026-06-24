package com.mymusicplayer.ui.screens.directory_picker

import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.data.scanner.FileSystemScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DirectoryEntry(
    val name: String,
    val path: String,
    val audioCount: Int,
    val isSelected: Boolean = true
)

data class DirectoryPickerUiState(
    val entries: List<DirectoryEntry> = emptyList(),
    val selectedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class DirectoryPickerViewModel constructor(
    private val settingsDataStore: SettingsDataStore,
    private val fileSystemScanner: FileSystemScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryPickerUiState())
    val uiState: StateFlow<DirectoryPickerUiState> = _uiState.asStateFlow()

    private val selectedPaths = mutableSetOf<String>()

    fun loadDirectories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val rootPath = Environment.getExternalStorageDirectory().absolutePath
                val dirs = withContext(Dispatchers.IO) {
                    fileSystemScanner.findDirectoriesWithAudio(rootPath)
                }

                selectedPaths.clear()
                val entries = dirs.map { dir ->
                    selectedPaths.add(dir.path)
                    DirectoryEntry(
                        name = dir.name,
                        path = dir.path,
                        audioCount = dir.audioCount,
                        isSelected = true
                    )
                }

                _uiState.value = DirectoryPickerUiState(
                    entries = entries,
                    selectedCount = entries.size,
                    totalCount = entries.size,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("DirectoryPickerVM", "Failed to load directories", e)
                val msg = when {
                    e is SecurityException -> "Storage permission denied. Grant file access in Settings."
                    else -> e.message ?: "Unknown error"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = msg
                )
            }
        }
    }

    fun toggleSelection(path: String) {
        val updated = _uiState.value.entries.map { entry ->
            if (entry.path == path) {
                val newSelected = !entry.isSelected
                if (newSelected) selectedPaths.add(path) else selectedPaths.remove(path)
                entry.copy(isSelected = newSelected)
            } else entry
        }
        _uiState.value = _uiState.value.copy(
            entries = updated,
            selectedCount = selectedPaths.size
        )
    }

    fun toggleSelectAll() {
        val allSelected = _uiState.value.selectedCount < _uiState.value.totalCount
        val updated = _uiState.value.entries.map { entry ->
            if (allSelected) selectedPaths.add(entry.path) else selectedPaths.remove(entry.path)
            entry.copy(isSelected = allSelected)
        }
        _uiState.value = _uiState.value.copy(
            entries = updated,
            selectedCount = if (allSelected) _uiState.value.totalCount else 0
        )
    }

    fun saveSelected() {
        viewModelScope.launch {
            if (selectedPaths.isEmpty()) {
                settingsDataStore.setScanDirectoryPath(null)
            } else {
                settingsDataStore.setScanDirectoryPath(selectedPaths.joinToString("|"))
            }
        }
    }
}
