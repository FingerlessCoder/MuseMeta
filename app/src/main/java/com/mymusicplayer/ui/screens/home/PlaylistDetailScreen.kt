package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
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
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.data.db.dao.PlaylistDao
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val repository: MusicRepository = koinInject()
    val playlistDao: PlaylistDao = koinInject()
    val playerController: MusicPlayerController = koinInject()
    val scope = rememberCoroutineScope()
    val tracks by repository.getTracksInPlaylist(playlistId).collectAsState(initial = emptyList())

    var multiSelectEnabled by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(setOf<Long>()) }

    fun clearSelection() {
        selectedTrackIds = emptySet()
        multiSelectEnabled = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (tracks.isNotEmpty()) {
                            Text(
                                "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tracks.isNotEmpty() && multiSelectEnabled) {
                        TextButton(onClick = { clearSelection() }) {
                            Text("Cancel")
                        }
                    }
                    if (tracks.isNotEmpty()) {
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
        bottomBar = {
            if (multiSelectEnabled && selectedTrackIds.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedTrackIds.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        selectedTrackIds.forEach { trackId ->
                                            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
                                        }
                                        clearSelection()
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "This playlist is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    PlaylistTrackRow(
                        track = track,
                        isMultiSelect = multiSelectEnabled,
                        isSelected = track.id in selectedTrackIds,
                        onClick = {
                            if (multiSelectEnabled) {
                                selectedTrackIds = if (track.id in selectedTrackIds) {
                                    selectedTrackIds - track.id
                                } else {
                                    selectedTrackIds + track.id
                                }
                            } else {
                                playerController.initialize()
                                val allTracks = tracks
                                val trackIndex = allTracks.indexOfFirst { it.id == track.id }
                                if (trackIndex >= 0 && allTracks.size > 1) {
                                    playerController.playFromQueue(
                                        trackPaths = allTracks.map { it.filePath },
                                        startIndex = trackIndex,
                                        trackIds = allTracks.map { it.id },
                                        titles = allTracks.map { it.title },
                                        artists = allTracks.map { it.artists.firstOrNull()?.name },
                                        albumArtPaths = allTracks.map { it.album?.artPath }
                                    )
                                } else {
                                    playerController.play(
                                        track.filePath, track.id,
                                        title = track.title,
                                        artist = track.artists.firstOrNull()?.name,
                                        albumArtPath = track.album?.artPath
                                    )
                                }
                                onNavigateToPlayer()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    track: Track,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
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
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = if (isMultiSelect) 88.dp else 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}
