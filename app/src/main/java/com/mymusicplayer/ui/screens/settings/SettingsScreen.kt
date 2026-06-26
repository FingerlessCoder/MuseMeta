package com.mymusicplayer.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScan: () -> Unit = {},
    onNavigateToDirectoryPicker: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader("Library")
            SettingCard {
                SettingRow(Icons.Default.Refresh, "Rescan Library",
                    if (state.scanDirectoryPath.isNotBlank()) "Custom directory" else "Scan for new and removed files",
                    onClick = onNavigateToScan)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ScanDirectorySetting(state.scanDirectoryPath, { viewModel.setScanDirectoryPath(it) },
                    { viewModel.clearScanDirectoryPath() }, onNavigateToDirectoryPicker)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ScanFilterSetting(state.scanMinFileSizeKb, state.scanMinDurationSec,
                    { viewModel.setScanMinFileSize(it) }, { viewModel.setScanMinDuration(it) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(Icons.AutoMirrored.Filled.Sort, "Default Sort",
                    state.sortMode.replace("_", " ").replaceFirstChar { it.uppercase() },
                    onClick = { showSortMenu = true })
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf("name", "date_added", "play_count", "rating", "duration").forEach { mode ->
                        DropdownMenuItem(text = { Text(mode.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                            onClick = { viewModel.setSortMode(mode); showSortMenu = false })
                    }
                }
            }

            SectionHeader("Audio")
            SettingCard {
                SettingRow(Icons.Default.Tune, "Equalizer",
                    if (state.equalizerEnabled) "Enabled (${state.equalizerPreset})" else "Disabled",
                    trailing = {
                        Switch(checked = state.equalizerEnabled, onCheckedChange = { viewModel.setEqualizerEnabled(it) })
                    })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(Icons.AutoMirrored.Filled.VolumeUp, "Volume Normalization",
                    if (state.volumeNormalization) "On" else "Off",
                    trailing = {
                        Switch(checked = state.volumeNormalization, onCheckedChange = { viewModel.setVolumeNormalization(it) })
                    })
            }

            SectionHeader("Playback")
            SettingCard {
                SleepTimerSetting(state.sleepTimerMinutes, { viewModel.setSleepTimer(it) })
            }

            SectionHeader("About")
            SettingCard {
                SettingRow(Icons.Default.Info, "MuseMeta", "Version 1.0.0")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ScanDirectorySetting(path: String, onPathChange: (String) -> Unit, onClear: () -> Unit, onBrowse: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scan Directory", style = MaterialTheme.typography.bodyLarge)
                val dirCount = path.split("|").count { it.isNotBlank() }
                Text(if (dirCount > 0) "$dirCount director${if (dirCount > 1) "ies" else "y"} selected"
                    else "All MediaStore audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dirCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onBrowse, shape = RoundedCornerShape(10.dp)) { Text("Browse") }
        }
        if (path.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable { onClear() })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && path.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Text("Grant file access")
            }
        }
    }
}

@Composable
private fun ScanFilterSetting(minSizeKb: Long, minDurationSec: Long, onSetSize: (Long) -> Unit, onSetDuration: (Long) -> Unit) {
    var sliderSize by remember(minSizeKb) { mutableFloatStateOf(minSizeKb.toFloat()) }
    var sliderDuration by remember(minDurationSec) { mutableFloatStateOf(minDurationSec.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Scan Filters", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text("Skip files below these thresholds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("Min file size: ${sliderSize.toInt()} KB", style = MaterialTheme.typography.labelMedium)
        Slider(value = sliderSize, onValueChange = { sliderSize = it },
            onValueChangeFinished = { onSetSize(sliderSize.toLong()) }, valueRange = 0f..2048f)
        Spacer(Modifier.height(8.dp))
        Text("Min duration: ${formatDurationSec(sliderDuration.toLong())}", style = MaterialTheme.typography.labelMedium)
        Slider(value = sliderDuration, onValueChange = { sliderDuration = it },
            onValueChangeFinished = { onSetDuration(sliderDuration.toLong()) }, valueRange = 0f..300f)
    }
}

@Composable
private fun SleepTimerSetting(currentMinutes: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour")
    val label = options.find { it.first == currentMinutes }?.second ?: "Off"
    SettingRow(Icons.Default.Schedule, "Sleep Timer", label, onClick = { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { (minutes, label) ->
            DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(minutes); expanded = false })
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { content() }
}

@Composable
private fun SettingRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

private fun formatDurationSec(sec: Long): String {
    val m = sec / 60; val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
