package com.mymusicplayer.ui.screens.artists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: Long,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    viewModel: ArtistDetailViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setArtistImage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.artist?.name ?: "Artist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            var selectedTab by remember { mutableIntStateOf(0) }
            val groupedTracks = remember(state.tracks) {
                state.tracks.groupBy { it.album?.id ?: 0 }
                    .entries.sortedByDescending { (_, tracks) -> tracks.size }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item(key = "header") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val artistArtPath = state.artistArtPath
                            if (artistArtPath != null) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(artistArtPath))
                                        .setParameter(
                                            "art_mtime",
                                            File(artistArtPath).lastModified(),
                                            File(artistArtPath).lastModified().toString()
                                        )
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = state.artist?.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = {
                                        Icon(
                                            Icons.Default.Person, contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person, contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }

                            if (state.imageFetchState is ArtistImageFetchState.Loading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.retryFetchArtistImage() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                            ) {
                                Icon(
                                    Icons.Default.Refresh, contentDescription = "Refresh image",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                            ) {
                                Icon(
                                    Icons.Default.Edit, contentDescription = "Choose from gallery",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.artist?.name ?: "Unknown Artist",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    renameText = state.artist?.name ?: ""
                                    showRenameDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit, contentDescription = "Rename artist",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val trackLabel = "${state.tracks.size} track${if (state.tracks.size != 1) "s" else ""}"
                        val durationMinutes = state.totalDuration / 60000
                        Text(
                            text = "$trackLabel · ${durationMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.playAll(); onNavigateToPlayer() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play All", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = { viewModel.shuffleAll(); onNavigateToPlayer() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item(key = "tabs") {
                    val tabTitles = listOf("All", "By Album")
                    TabRow(selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) }
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    // ── All tab: flat track list ──
                    itemsIndexed(state.tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        TrackRow(
                            index = index + 1,
                            title = track.title,
                            album = track.album?.title,
                            duration = track.duration,
                            isLast = index == state.tracks.lastIndex,
                            onClick = {
                                viewModel.playTrack(track)
                                onNavigateToPlayer()
                            }
                        )
                    }
                } else {
                    // ── By Album tab: group tracks under album headers ──
                    groupedTracks.forEach { (_, tracks) ->
                        val album = tracks.first().album
                        item(key = "album_header_${album?.id ?: 0}") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { album?.let { onNavigateToAlbum(it.id) } }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (album?.artPath != null) {
                                            SubcomposeAsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(File(album.artPath))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = album.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = {
                                                    Icon(Icons.Default.Album, contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                                }
                                            )
                                        } else {
                                            Icon(Icons.Default.Album, contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(album?.title ?: "Unknown Album",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Go to album",
                                        modifier = Modifier.size(20.dp).rotate(180f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                        }

                        itemsIndexed(tracks, key = { index, track -> "${track.id}_${album?.id}_$index" }) { index, track ->
                            TrackRow(
                                index = track.trackNumber ?: (index + 1),
                                title = track.title,
                                album = null,
                                duration = track.duration,
                                isLast = index == tracks.lastIndex,
                                onClick = {
                                    viewModel.playTrack(track)
                                    onNavigateToPlayer()
                                }
                            )
                        }

                        item(key = "album_spacer_${album?.id ?: 0}") {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(32.dp))
                }
            }

            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename Artist") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            label = { Text("Artist name") }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (renameText.isNotBlank()) {
                                    viewModel.renameArtist(renameText.trim())
                                    showRenameDialog = false
                                }
                            }
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    title: String,
    album: String?,
    duration: Long,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (album != null) {
                    Text(text = album, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text = formatDuration(duration), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(start = 44.dp, end = 16.dp))
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
