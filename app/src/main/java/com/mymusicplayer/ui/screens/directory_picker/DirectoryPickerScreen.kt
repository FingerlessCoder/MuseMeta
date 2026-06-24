package com.mymusicplayer.ui.screens.directory_picker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryPickerScreen(
    onBack: () -> Unit = {},
    onStartScan: () -> Unit = {},
    viewModel: DirectoryPickerViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val requiredPermission: String = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var permissionGranted: Boolean by remember { mutableStateOf(false) }
    var permissionChecked: Boolean by remember { mutableStateOf(false) }
    var loadTrigger: Int by remember { mutableIntStateOf(0) }

    fun checkHasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            val hasManage = Environment.isExternalStorageManager()
            val hasRead = ContextCompat.checkSelfPermission(context, requiredPermission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            return hasManage || hasRead
        } else {
            return ContextCompat.checkSelfPermission(context, requiredPermission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) {
        permissionGranted = checkHasPermission()
        permissionChecked = true
        if (permissionGranted) {
            loadTrigger = 1
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            permissionGranted = true
            loadTrigger = loadTrigger + 1
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionGranted = checkHasPermission()
        if (permissionGranted) {
            loadTrigger = loadTrigger + 1
        }
    }

    LaunchedEffect(loadTrigger) {
        if (loadTrigger > 0) {
            viewModel.loadDirectories()
        }
    }

    val topBar = @Composable {
        TopAppBar(
            title = { Text("Select Directories") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                val showSelectAll = !state.isLoading &&
                        state.entries.isNotEmpty() &&
                        state.errorMessage == null
                if (showSelectAll) {
                    val selectAllText: String = if (state.selectedCount < state.totalCount) {
                        "Select All"
                    } else {
                        "Deselect All"
                    }
                    TextButton(onClick = { viewModel.toggleSelectAll() }) {
                        Text(selectAllText)
                    }
                }
            }
        )
    }

    val bottomBar = @Composable {
        val showBottom = permissionGranted &&
                !state.isLoading &&
                state.entries.isNotEmpty() &&
                state.errorMessage == null
        if (showBottom) {
            val countText: String = state.selectedCount.toString() +
                    " of " +
                    state.totalCount.toString() +
                    " directories selected"
            val buttonText: String = "Scan Selected (" +
                    state.selectedCount.toString() +
                    ")"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveSelected()
                        onStartScan()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
                }
            }
        }
    }

    Scaffold(topBar = topBar, bottomBar = bottomBar) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!permissionChecked) {
                PermissionCheckContent()
            } else if (!permissionGranted) {
                PermissionDeniedContent(
                    onGrant = { permissionLauncher.launch(requiredPermission) },
                    onSettings = {
                        val uri = android.net.Uri.fromParts("package", context.packageName, null)
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                        settingsLauncher.launch(intent)
                    }
                )
            } else if (state.errorMessage != null) {
                val errMsg: String = state.errorMessage ?: "Unknown error"
                ErrorContent(
                    message = errMsg,
                    onSettings = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val uri = android.net.Uri.parse("package:" + context.packageName)
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                            ).apply { data = uri }
                            settingsLauncher.launch(intent)
                        } else {
                            permissionLauncher.launch(requiredPermission)
                        }
                    },
                    onRetry = { loadTrigger = loadTrigger + 1 }
                )
            } else if (state.isLoading) {
                LoadingContent("Scanning storage for audio files...")
            } else if (state.entries.isEmpty()) {
                EmptyContent()
            } else {
                DirectoryListContent(
                    entries = state.entries,
                    onToggle = { path -> viewModel.toggleSelection(path) }
                )
            }
        }
    }
}

@Composable
private fun PermissionCheckContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Checking permissions...")
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onGrant: () -> Unit,
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Storage Access Needed",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Browse directories needs access to your storage to find audio files.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permission")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSettings) {
                Text("Open App Settings")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onSettings: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Settings & Grant Access")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LoadingContent(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(text)
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No directories with audio found",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add audio files to your device and try again",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DirectoryListContent(
    entries: List<DirectoryEntry>,
    onToggle: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { entry -> entry.path }) { entry ->
            DirectoryAudioItem(
                name = entry.name,
                path = entry.path,
                audioCount = entry.audioCount,
                isSelected = entry.isSelected,
                onToggle = { onToggle(entry.path) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun DirectoryAudioItem(
    name: String,
    path: String,
    audioCount: Int,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                val countStr: String = "(" + audioCount.toString() + ")"
                val countColor = if (audioCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = countStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = countColor
                )
            }
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
