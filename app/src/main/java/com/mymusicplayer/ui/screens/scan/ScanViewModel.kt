package com.mymusicplayer.ui.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.data.scanner.ScanPhase
import com.mymusicplayer.data.scanner.ScanProgress
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.DISCOVERING,
    val progress: Float = 0f,
    val message: String = "Starting scan...",
    val isComplete: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

class ScanViewModel constructor(
    private val musicRepository: MusicRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        startScan()
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = ScanUiState()
            val excludedDirs = settingsDataStore.excludedDirs.first()
            val scanDir = settingsDataStore.scanDirectoryPath.first()

            try {
                musicRepository.rescanLibrary(excludedDirs, scanDir).collect { progress ->
                    _uiState.value = ScanUiState(
                        phase = progress.phase,
                        progress = progress.progress,
                        message = progress.message,
                        isComplete = progress.phase == ScanPhase.COMPLETE,
                        isError = progress.phase == ScanPhase.ERROR,
                        errorMessage = if (progress.phase == ScanPhase.ERROR) progress.message else null
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = ScanUiState(
                        phase = ScanPhase.ERROR,
                        isError = true,
                        errorMessage = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun cancel() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isComplete = true,
            message = "Scan cancelled"
        )
    }
}
