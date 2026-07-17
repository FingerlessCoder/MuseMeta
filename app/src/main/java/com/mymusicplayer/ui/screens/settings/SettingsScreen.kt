package com.mymusicplayer.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mymusicplayer.ui.theme.AccentPalettes
import org.koin.androidx.compose.koinViewModel

private val sortFields = listOf("name", "date_added", "play_count", "year", "genre", "artist", "album")

private val sortOptions: List<Pair<String, String>> =
    sortFields.flatMap { field ->
        listOf(
            "$field" to "${field.replace("_", " ")} ↑",
            "${field}_desc" to "${field.replace("_", " ")} ↓"
        )
    }

private fun sortLabel(raw: String): String {
    val (field, dir) = if (raw.endsWith("_desc")) raw.removeSuffix("_desc") to "↓" else raw to "↑"
    return "${field.replace("_", " ")} $dir".replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScan: () -> Unit = {},
    onNavigateToDirectoryPicker: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showEqMenu by remember { mutableStateOf(false) }

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
                    sortLabel(state.sortMode),
                    onClick = { showSortMenu = true })
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    sortOptions.forEach { (field, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { viewModel.setSortMode(field); showSortMenu = false },
                            leadingIcon = if (state.sortMode == field) ({
                                Icon(Icons.Default.Check, contentDescription = null)
                            }) else null
                        )
                    }
                }
            }

            SectionHeader("Audio")
            SettingCard {
                SettingRow(Icons.Default.Tune, "Equalizer",
                    if (state.equalizerEnabled) "Enabled (${state.equalizerPreset})" else "Disabled",
                    trailing = {
                        Switch(checked = state.equalizerEnabled, onCheckedChange = { viewModel.setEqualizerEnabled(it) })
                    },
                    onClick = { if (state.equalizerEnabled) showEqMenu = true })
                DropdownMenu(expanded = showEqMenu, onDismissRequest = { showEqMenu = false }) {
                    listOf("Normal", "Flat", "Rock", "Pop", "Bass Boost", "Classical", "Jazz", "Vocal").forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset) },
                            onClick = { viewModel.setEqualizerPreset(preset); showEqMenu = false },
                            leadingIcon = if (state.equalizerPreset == preset) ({
                                Icon(Icons.Default.Check, contentDescription = null)
                            }) else null
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            SectionHeader("Playback")
            SettingCard {
                SleepTimerSetting(
                    remainingSeconds = state.sleepTimerRemainingSeconds,
                    onSelect = { viewModel.setSleepTimer(it) }
                )
            }

            SectionHeader("Appearance")
            SettingCard {
                SettingRow(Icons.Default.DarkMode, "AMOLED Black Theme",
                    if (state.amoledBlackTheme) "Pure black for AMOLED screens" else "Dark gray theme",
                    trailing = {
                        Switch(checked = state.amoledBlackTheme, onCheckedChange = { viewModel.setAmoledBlackTheme(it) })
                    })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                AccentColorPicker(
                    selectedIndex = state.accentColorIndex,
                    onSelect = { viewModel.setAccentColorIndex(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                PlayerThemePicker(
                    selectedTheme = state.playerTheme,
                    onSelect = { viewModel.setPlayerTheme(it) }
                )
            }

            SectionHeader("About")
            SettingCard {
                SettingRow(Icons.Default.Person, "FingerlessCoder", "Developer")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val ctx = LocalContext.current
                SettingRow(Icons.Default.Info, "MuseMeta", "Version 1.0.0")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(Icons.Default.OpenInNew, "GitHub",
                    "github.com/FingerlessCoder",
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/FingerlessCoder"))
                        ctx.startActivity(intent)
                    })
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ScanDirectorySetting(path: String, onPathChange: (String) -> Unit, onClear: () -> Unit, onBrowse: () -> Unit) {
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
private fun SleepTimerSetting(
    remainingSeconds: Int,
    onSelect: (Int) -> Unit
) {
    val isActive = remainingSeconds > 0
    var sliderValue by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Sleep Timer", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (isActive) {
                        val mins = remainingSeconds / 60
                        val secs = remainingSeconds % 60
                        "%d:%02d remaining".format(mins, secs)
                    } else {
                        "Off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                TextButton(onClick = { onSelect(0) }) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Switch(checked = false, onCheckedChange = { onSelect(sliderValue.toInt()) })
            }
        }

        AnimatedVisibility(visible = isActive) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${sliderValue.toInt()} min",
                        style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSelect(sliderValue.toInt()) },
                    valueRange = 1f..90f,
                    steps = 0
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1m", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("90m", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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

@Composable
private fun AccentColorPicker(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Palette, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text("Accent Color", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccentPalettes.forEachIndexed { index, palette ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.primary)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = "Selected",
                            tint = palette.onPrimary,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerThemePicker(selectedTheme: Int, onSelect: (Int) -> Unit) {
    val themes = listOf("Normal" to "Centered art with controls below", "Full Art" to "Full-screen art with overlaid controls")
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Image, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text("Player Theme", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        themes.forEachIndexed { index, (name, desc) ->
            val isSelected = index == selectedTheme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = { onSelect(index) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    Text(desc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatDurationSec(sec: Long): String {
    val m = sec / 60; val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
