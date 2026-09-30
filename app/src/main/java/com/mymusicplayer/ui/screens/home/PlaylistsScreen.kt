package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.data.db.entity.PlaylistEntity
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
private fun SortDirectionRow(desc: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SortByAlpha,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Name",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (desc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onPlaylistClick: (Long, String) -> Unit
) {
    val playlistDao: PlaylistDao = koinInject()
    val rawPlaylists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var sortDesc by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var showDeleteConfirm by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    var multiSelectEnabled by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf(setOf<Long>()) }

    fun clearSelection() {
        selectedPlaylistIds = emptySet()
        multiSelectEnabled = false
    }

    val playlists = remember(rawPlaylists, sortDesc) {
        val base = rawPlaylists.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
        if (sortDesc) base.reversed() else base
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Playlists") },
                navigationIcon = {
                    IconButton(onClick = if (multiSelectEnabled) {{ clearSelection() }} else onBack) {
                        Icon(
                            if (multiSelectEnabled) Icons.Default.CheckBoxOutlineBlank
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (multiSelectEnabled && selectedPlaylistIds.isNotEmpty()) {
                        IconButton(onClick = {
                            val ids = selectedPlaylistIds.toList()
                            scope.launch {
                                ids.forEach { playlistDao.deletePlaylistById(it) }
                            }
                            clearSelection()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (!multiSelectEnabled && playlists.isNotEmpty()) {
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(Icons.Default.SortByAlpha, contentDescription = "Sort by name")
                        }
                        IconButton(onClick = { sortDesc = !sortDesc }) {
                            Icon(
                                if (sortDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = if (sortDesc) "Sort descending" else "Sort ascending"
                            )
                        }
                    }
                    if (playlists.isNotEmpty()) {
                        IconButton(onClick = { multiSelectEnabled = !multiSelectEnabled }) {
                            Icon(
                                if (multiSelectEnabled) Icons.Default.CheckBox
                                else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = if (multiSelectEnabled) "Exit multiselect" else "Multiselect",
                                tint = if (multiSelectEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (!multiSelectEnabled) {
                LargeFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                }
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No playlists yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Playlist")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    if (multiSelectEnabled) {
                        PlaylistRowSelectable(
                            playlist = playlist,
                            isSelected = playlist.id in selectedPlaylistIds,
                            onClick = {
                                selectedPlaylistIds = if (playlist.id in selectedPlaylistIds) {
                                    selectedPlaylistIds - playlist.id
                                } else {
                                    selectedPlaylistIds + playlist.id
                                }
                            }
                        )
                    } else {
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id, playlist.name) },
                            onRename = {
                                renameText = playlist.name
                                showRenameDialog = playlist
                            },
                            onDelete = { showDeleteConfirm = playlist }
                        )
                    }
                }
            }
        }
    }

    // ── Sort Bottom Sheet ──
    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Sort playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                SortDirectionRow(
                    desc = sortDesc,
                    onToggle = { sortDesc = !sortDesc; showSortSheet = false }
                )
            }
        }
    }

    // ── Create Playlist Dialog ──
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newPlaylistName = ""
            },
            title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotBlank()) {
                            scope.launch { playlistDao.createPlaylist(PlaylistEntity(name = name)) }
                        }
                        showCreateDialog = false
                        newPlaylistName = ""
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newPlaylistName = ""
                }) { Text("Cancel") }
            }
        )
    }

    // ── Delete Confirmation ──
    showDeleteConfirm?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Playlist", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${playlist.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { playlistDao.deletePlaylistById(playlist.id) }
                    showDeleteConfirm = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    // ── Rename Dialog ──
    showRenameDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = null
                renameText = ""
            },
            title = { Text("Rename Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameText.trim()
                        if (newName.isNotBlank()) {
                            scope.launch { playlistDao.updatePlaylist(playlist.copy(name = newName)) }
                        }
                        showRenameDialog = null
                        renameText = ""
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = null
                    renameText = ""
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.description != null) {
                    Text(
                        playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = DpOffset(0.dp, 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRowSelectable(
    playlist: PlaylistEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 8.dp)
            )
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
