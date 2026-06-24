package com.mymusicplayer.ui.screens.directory_picker

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class DirectoryEntry(
    val name: String,
    val path: String,
    val isSelected: Boolean = false
)

data class DirectoryPickerUiState(
    val currentPath: String = "",
    val entries: List<DirectoryEntry> = emptyList(),
    val breadcrumbs: List<String> = emptyList(),
    val selectedCount: Int = 0,
    val canGoUp: Boolean = false,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
)

class DirectoryPickerViewModel constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryPickerUiState())
    val uiState: StateFlow<DirectoryPickerUiState> = _uiState.asStateFlow()

    private val selectedPaths = mutableSetOf<String>()

    private val storageRoot: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    private val excludedTopDirs = setOf(
        "Android", "obb", "data", "cache", "tmp",
        "dalvik-cache", "lost+found"
    )

    init {
        viewModelScope.launch {
            val savedPath = settingsDataStore.scanDirectoryPath.first()
            val initialPath = if (!savedPath.isNullOrBlank()) {
                File(savedPath).parentFile?.absolutePath ?: storageRoot
            } else {
                storageRoot
            }
            navigateTo(initialPath)
        }
    }

    fun navigateTo(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return

        val entries = dir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.filter { !isExcluded(it.name) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { file ->
                DirectoryEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isSelected = file.absolutePath in selectedPaths
                )
            } ?: emptyList()

        val breadcrumbs = buildBreadcrumbs(path)

        _uiState.value = DirectoryPickerUiState(
            currentPath = path,
            entries = entries,
            breadcrumbs = breadcrumbs,
            selectedCount = selectedPaths.size,
            canGoUp = path != storageRoot,
            isLoading = false,
            isEmpty = entries.isEmpty()
        )
    }

    fun navigateInto(dirName: String) {
        val newPath = File(_uiState.value.currentPath, dirName).absolutePath
        navigateTo(newPath)
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        val parent = File(current).parent
        if (parent != null && parent.isNotEmpty()) {
            navigateTo(parent)
        }
    }

    fun toggleSelection(path: String) {
        if (path in selectedPaths) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }
        val current = _uiState.value
        val updatedEntries = current.entries.map {
            if (it.path == path) it.copy(isSelected = path in selectedPaths)
            else it
        }
        _uiState.value = current.copy(
            entries = updatedEntries,
            selectedCount = selectedPaths.size
        )
    }

    fun saveAndFinish() {
        viewModelScope.launch {
            if (selectedPaths.isEmpty()) {
                settingsDataStore.setScanDirectoryPath(null)
            } else {
                settingsDataStore.setScanDirectoryPath(selectedPaths.first())
            }
        }
    }

    private fun buildBreadcrumbs(path: String): List<String> {
        val parts = mutableListOf<String>()
        var current = path
        while (current.isNotEmpty()) {
            val name = File(current).name
            parts.add(0, if (name.isEmpty()) current else name)
            val parent = File(current).parent
            if (parent == null || parent == current) break
            current = parent
        }
        return parts
    }

    private fun isExcluded(name: String): Boolean {
        return name.lowercase() in excludedTopDirs
    }
}
