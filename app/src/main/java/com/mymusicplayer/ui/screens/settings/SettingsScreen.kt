package com.mymusicplayer.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Library section
            SectionHeader("Library")
            SettingCard {
                SettingRow(
                    title = "Rescan Library",
                    subtitle = state.scanMessage.ifBlank { "Scan for new and removed files" },
                    trailing = {
                        if (state.isScanning) {
                            Column {
                                LinearProgressIndicator(
                                    progress = { state.scanProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "${(state.scanProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Scan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = { viewModel.rescanLibrary() }
                )
                HorizontalDivider()
                SettingRow(
                    title = "Default Sort",
                    subtitle = state.sortMode.replace("_", " ").replaceFirstChar { it.uppercase() },
                    onClick = { showSortMenu = true }
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    listOf("name", "date_added", "play_count", "rating", "duration").forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                viewModel.setSortMode(mode)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Audio section
            SectionHeader("Audio")
            SettingCard {
                SettingRow(
                    title = "Equalizer",
                    subtitle = if (state.equalizerEnabled) "Enabled (${state.equalizerPreset})" else "Disabled",
                    trailing = {
                        Switch(
                            checked = state.equalizerEnabled,
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                        )
                    },
                    onClick = {}
                )
                HorizontalDivider()
                SettingRow(
                    title = "Volume Normalization",
                    subtitle = if (state.volumeNormalization) "On" else "Off",
                    trailing = {
                        Switch(
                            checked = state.volumeNormalization,
                            onCheckedChange = { viewModel.setVolumeNormalization(it) }
                        )
                    },
                    onClick = {}
                )
            }

            Spacer(Modifier.height(16.dp))

            // Playback section
            SectionHeader("Playback")
            SettingCard {
                SleepTimerSetting(
                    currentMinutes = state.sleepTimerMinutes,
                    onSelect = { viewModel.setSleepTimer(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // About section
            SectionHeader("About")
            SettingCard {
                SettingRow(
                    title = "MuseMeta",
                    subtitle = "Version 1.0.0",
                    onClick = {}
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SleepTimerSetting(
    currentMinutes: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        0 to "Off",
        15 to "15 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "1 hour"
    )

    val label = options.find { it.first == currentMinutes }?.second ?: "Off"

    SettingRow(
        title = "Sleep Timer",
        subtitle = label,
        onClick = { expanded = true }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { (minutes, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = { onSelect(minutes); expanded = false }
            )
        }
    }
}
