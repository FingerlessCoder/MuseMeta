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
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.ui.components.EqualizerPanel
import com.mymusicplayer.ui.theme.AccentPalettes
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val sortFields = listOf("name", "date_added", "play_count", "year", "genre", "artist", "album")

/** Min file size slider snaps to multiples of this, in KB. */
private const val FILE_SIZE_STEP_KB = 64f
private const val FILE_SIZE_MAX_KB = 2048f

private fun sortLabel(raw: String): String {
    val (field, dir) = if (raw.endsWith("_desc")) raw.removeSuffix("_desc") to "↓" else raw to "↑"
    return "${field.replace("_", " ")} $dir".replaceFirstChar { it.uppercase() }
}

private fun isSortDesc(raw: String): Boolean = raw.endsWith("_desc")

private fun sortFieldOf(raw: String): String =
    if (raw.endsWith("_desc")) raw.removeSuffix("_desc") else raw

private fun withSortDir(field: String, desc: Boolean): String =
    if (desc) "${field}_desc" else field

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScan: () -> Unit = {},
    onNavigateToDirectoryPicker: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val settingsDataStore = koinInject<SettingsDataStore>()
    var showEqualizer by remember { mutableStateOf(false) }

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
                DefaultSortSetting(
                    currentSort = state.sortMode,
                    onSelect = { viewModel.setSortMode(it) }
                )
            }

            SectionHeader("Playback")
            SettingCard {
                SleepTimerSetting(
                    remainingSeconds = state.sleepTimerRemainingSeconds,
                    onSelect = { viewModel.setSleepTimer(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Equalizer",
                    subtitle = (if (state.equalizerEnabled) "On" else "Off") + " \u00b7 " + state.equalizerPreset,
                    trailing = {
                        Switch(
                            checked = state.equalizerEnabled,
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                        )
                    },
                    onClick = { showEqualizer = true }
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
            }

            SectionHeader("About")
            SettingCard {
                SettingRow(Icons.Default.Person, "FingerlessCoder", "Developer")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val ctx = LocalContext.current
                val versionName = remember {
                    try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                    } catch (_: Exception) { "?" }
                }
                SettingRow(Icons.Default.Info, "MuseMeta", "Version $versionName")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "GitHub",
                    "github.com/FingerlessCoder",
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/FingerlessCoder"))
                        ctx.startActivity(intent)
                    })
            }

            Spacer(Modifier.height(32.dp))
        }

        if (showEqualizer) {
            ModalBottomSheet(
                onDismissRequest = { showEqualizer = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                EqualizerPanel(settingsDataStore = settingsDataStore)
                Spacer(Modifier.height(24.dp))
            }
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
    var sliderSize by remember(minSizeKb) {
        mutableFloatStateOf((minSizeKb / FILE_SIZE_STEP_KB.toLong()) * FILE_SIZE_STEP_KB.toLong().toFloat())
    }
    var sliderDuration by remember(minDurationSec) { mutableFloatStateOf(minDurationSec.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Scan Filters", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text("Skip files below these thresholds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("Min file size: ${sliderSize.toInt()} KB", style = MaterialTheme.typography.labelMedium)
        Slider(value = sliderSize, onValueChange = { sliderSize = (it / FILE_SIZE_STEP_KB) * FILE_SIZE_STEP_KB },
            onValueChangeFinished = { onSetSize(sliderSize.toLong()) },
            valueRange = 0f..FILE_SIZE_MAX_KB,
            steps = (FILE_SIZE_MAX_KB / FILE_SIZE_STEP_KB).toInt() - 1)
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
private fun DefaultSortSetting(currentSort: String, onSelect: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val desc = isSortDesc(currentSort)
    val field = sortFieldOf(currentSort)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Default Sort", style = MaterialTheme.typography.bodyLarge)
            Text(sortLabel(currentSort), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(sortFieldIcon(field), contentDescription = "Sort criterion",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                sortFields.forEach { f ->
                    val selected = f == field
                    DropdownMenuItem(
                        text = { Text(f.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                        onClick = { onSelect(withSortDir(f, desc)); showMenu = false },
                        leadingIcon = {
                            Icon(sortFieldIcon(f), contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = if (selected) ({
                            Icon(Icons.Default.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }) else null
                    )
                }
            }
        }
        IconButton(
            onClick = { onSelect(withSortDir(field, !desc)) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (desc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = if (desc) "Sort descending" else "Sort ascending",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun sortFieldIcon(field: String): ImageVector = when (field) {
    "date_added" -> Icons.Default.Schedule
    "play_count" -> Icons.Default.TrendingUp
    "year" -> Icons.Default.DateRange
    "genre" -> Icons.Default.Category
    "artist", "album_artist" -> Icons.Default.Person
    "album", "title" -> Icons.Default.Album
    "track_count" -> Icons.Default.FormatListNumbered
    else -> Icons.Default.SortByAlpha
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

private fun formatDurationSec(sec: Long): String {
    val m = sec / 60; val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
