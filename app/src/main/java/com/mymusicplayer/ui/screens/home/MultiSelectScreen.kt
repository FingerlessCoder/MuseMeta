package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.ui.components.MoreActionsSheet
import com.mymusicplayer.ui.components.PlaylistActionsSheet
import com.mymusicplayer.ui.components.PlaylistSelectorSheet
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    viewModel: MultiSelectViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showPlaylistActionsSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    val selectedCount = state.selectedTrackIds.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedCount == 0) "Select tracks"
                        else "$selectedCount selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.allTracks.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleSelectAll() }) {
                            Text(if (state.allSelected) "Deselect All" else "Select All")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 12.dp,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OperationIcon(
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Play next / queue",
                            onClick = { showPlaylistActionsSheet = true }
                        )
                        OperationIcon(
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to playlist",
                            onClick = { showAddToPlaylistSheet = true }
                        )
                        OperationIcon(
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { showDeleteConfirm = true }
                        )
                        OperationIcon(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More",
                            onClick = { showMoreSheet = true }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (state.allTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No tracks found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Search bar
                TextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search tracks", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(state.filteredTracks, key = { it.id }) { track ->
                        MultiSelectTrackRow(
                            track = track,
                            isSelected = track.id in state.selectedTrackIds,
                            onToggleSelect = { viewModel.toggleTrackSelection(track.id) }
                        )
                    }
                }
            }
        }
    }

    // ── Playlist actions (Play Next / Add to Queue) ──
    if (showPlaylistActionsSheet) {
        PlaylistActionsSheet(
            trackCount = selectedCount,
            onDismiss = { showPlaylistActionsSheet = false },
            onPlayNext = {
                viewModel.playNextSelected()
                showPlaylistActionsSheet = false
            },
            onAddToQueue = {
                viewModel.addToQueueSelected()
                showPlaylistActionsSheet = false
            }
        )
    }

    // ── Add to my playlists ──
    if (showAddToPlaylistSheet) {
        PlaylistSelectorSheet(
            trackIds = state.selectedTrackIds.toList(),
            onDismiss = { showAddToPlaylistSheet = false },
            onAdded = {
                showAddToPlaylistSheet = false
                viewModel.clearSelection()
            }
        )
    }

    // ── Delete confirmation ──
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedCount} track${if (selectedCount != 1) "s" else ""}?") },
            text = {
                Text(
                    "This will permanently remove the selected track${if (selectedCount != 1) "s" else ""} " +
                        "from your library. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelectedTracks(onDone = onBack)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── More (favorite / share) ──
    if (showMoreSheet) {
        MoreActionsSheet(
            trackCount = selectedCount,
            hasFavorites = state.allSelectedAreFavorites,
            onDismiss = { showMoreSheet = false },
            onToggleFavorite = {
                viewModel.toggleFavoriteSelected()
                showMoreSheet = false
            },
            onShare = {
                viewModel.shareSelected(context)
                showMoreSheet = false
            }
        )
    }
}

@Composable
private fun OperationIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MultiSelectTrackRow(
    track: Track,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelect)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.padding(end = 8.dp)
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (track.album?.artPath != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(track.album.artPath)).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Icon(Icons.Default.MusicNote, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        }
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = buildString {
                        append(track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" })
                        if (track.album != null) {
                            append(" | ${track.album.title}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 88.dp, end = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}
