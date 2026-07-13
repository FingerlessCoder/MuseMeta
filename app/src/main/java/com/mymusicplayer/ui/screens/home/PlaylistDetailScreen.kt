package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
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
import com.mymusicplayer.data.db.entity.PlaylistEntryEntity
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.domain.repository.MusicRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

private enum class TrackSort(val label: String) {
    POSITION("Manual (position)"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST("Artist"),
    DURATION("Duration")
}

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
    val rawTracks by repository.getTracksInPlaylist(playlistId).collectAsState(initial = emptyList())

    var sortMode by remember { mutableStateOf(TrackSort.POSITION) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showAddTrackSheet by remember { mutableStateOf(false) }

    var multiSelectEnabled by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(setOf<Long>()) }
    var reorderMode by remember { mutableStateOf(false) }

    fun clearSelection() {
        selectedTrackIds = emptySet()
        multiSelectEnabled = false
        reorderMode = false
    }

    // Sort tracks in-memory
    val tracks = remember(rawTracks, sortMode) {
        when (sortMode) {
            TrackSort.POSITION -> rawTracks // already by position ASC from DAO
            TrackSort.TITLE_ASC -> rawTracks.sortedBy { it.title.lowercase() }
            TrackSort.TITLE_DESC -> rawTracks.sortedByDescending { it.title.lowercase() }
            TrackSort.ARTIST -> rawTracks.sortedBy {
                it.artists.firstOrNull()?.name?.lowercase() ?: ""
            }
            TrackSort.DURATION -> rawTracks.sortedBy { it.duration }
        }
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
                    IconButton(onClick = if (multiSelectEnabled || reorderMode) {{ clearSelection() }} else onBack) {
                        Icon(
                            if (multiSelectEnabled || reorderMode) Icons.Default.CheckBoxOutlineBlank
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (multiSelectEnabled && selectedTrackIds.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                selectedTrackIds.forEach { tid ->
                                    playlistDao.removeTrackFromPlaylist(playlistId, tid)
                                }
                                clearSelection()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove selected",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (tracks.isNotEmpty()) {
                        if (!multiSelectEnabled && !reorderMode) {
                            // Sort button
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(Icons.Default.ImportExport, contentDescription = "Sort")
                            }
                        }
                        // Toggle multiselect
                        if (!reorderMode) {
                            IconButton(onClick = { multiSelectEnabled = !multiSelectEnabled }) {
                                Icon(
                                    if (multiSelectEnabled) Icons.Default.CheckBox
                                    else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = "Multiselect",
                                    tint = if (multiSelectEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Reorder toggle
                        if (!multiSelectEnabled) {
                            IconButton(onClick = {
                                reorderMode = !reorderMode
                                sortMode = TrackSort.POSITION
                            }) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = "Reorder",
                                    tint = if (reorderMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // Add track button
                    if (!multiSelectEnabled && !reorderMode) {
                        IconButton(onClick = { showAddTrackSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add track")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            when {
                multiSelectEnabled && selectedTrackIds.isNotEmpty() -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedTrackIds.size} selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        selectedTrackIds.forEach { tid ->
                                            playlistDao.removeTrackFromPlaylist(playlistId, tid)
                                        }
                                        clearSelection()
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove")
                            }
                        }
                    }
                }
                reorderMode && tracks.isNotEmpty() -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Use ▲▼ to reorder tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "This playlist is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { showAddTrackSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Tracks")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    if (reorderMode) {
                        ReorderTrackRow(
                            track = track,
                            index = index,
                            totalCount = tracks.size,
                            onMoveUp = {
                                if (index > 0) {
                                    scope.launch {
                                        val prevId = tracks[index - 1].id
                                        playlistDao.reorderTrack(playlistId, track.id, index - 1)
                                        playlistDao.reorderTrack(playlistId, prevId, index)
                                    }
                                }
                            },
                            onMoveDown = {
                                if (index < tracks.size - 1) {
                                    scope.launch {
                                        val nextId = tracks[index + 1].id
                                        playlistDao.reorderTrack(playlistId, track.id, index + 1)
                                        playlistDao.reorderTrack(playlistId, nextId, index)
                                    }
                                }
                            }
                        )
                    } else {
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

    // ── Sort Bottom Sheet ──
    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Sort tracks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                TrackSort.entries.forEach { mode ->
                    val selected = sortMode == mode
                    Surface(
                        onClick = {
                            sortMode = mode
                            reorderMode = false
                            showSortSheet = false
                        },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                mode.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add Track Picker Sheet ──
    if (showAddTrackSheet) {
        AddTrackToPlaylistSheet(
            playlistId = playlistId,
            existingTrackIds = rawTracks.map { it.id }.toSet(),
            onDismiss = { showAddTrackSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTrackToPlaylistSheet(
    playlistId: Long,
    existingTrackIds: Set<Long>,
    onDismiss: () -> Unit
) {
    val repository: MusicRepository = koinInject()
    val playlistDao: PlaylistDao = koinInject()
    val scope = rememberCoroutineScope()
    val allTracks by repository.getAllTracks("name").collectAsState(initial = emptyList())
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var searchQuery by remember { mutableStateOf("") }

    // Only show tracks not already in the playlist
    val availableTracks = remember(allTracks, existingTrackIds) {
        allTracks.filter { it.id !in existingTrackIds }
    }

    // Filter by search query
    val filteredTracks = remember(availableTracks, searchQuery) {
        if (searchQuery.isBlank()) availableTracks
        else availableTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artists.any { a -> a.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Add Tracks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selectedIds.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                val nextPos = playlistDao.getNextPosition(playlistId)
                                val entries = selectedIds.toList().mapIndexed { i, trackId ->
                                    PlaylistEntryEntity(
                                        playlistId = playlistId,
                                        trackId = trackId,
                                        position = nextPos + i
                                    )
                                }
                                playlistDao.addTracksToPlaylist(entries)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add ${selectedIds.size}")
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search to add...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Track count / filter result info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (searchQuery.isBlank()) "${availableTracks.size} available"
                    else "Found ${filteredTracks.size} of ${availableTracks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.weight(1f))
                if (filteredTracks.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val filteredIds = filteredTracks.map { it.id }.toSet()
                            selectedIds = if (selectedIds.containsAll(filteredIds)) {
                                selectedIds - filteredIds
                            } else {
                                selectedIds + filteredIds
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        val allFilteredSelected = selectedIds.containsAll(filteredTracks.map { it.id })
                        Text(if (allFilteredSelected) "Deselect All" else "Select All", 
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Track list
            if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No tracks match \"$searchQuery\""
                        else "All tracks already in this playlist",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val checked = track.id in selectedIds
                        Surface(
                            onClick = {
                                selectedIds = if (checked) selectedIds - track.id
                                    else selectedIds + track.id
                            },
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val context = LocalContext.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedIds = if (checked) selectedIds - track.id
                                            else selectedIds + track.id
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
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
                                    Text(track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown" },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
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
                Checkbox(checked = isSelected, onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp))
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

@Composable
private fun ReorderTrackRow(
    track: Track,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Move up
        IconButton(
            onClick = onMoveUp,
            enabled = index > 0,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up",
                tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
        // Move down
        IconButton(
            onClick = onMoveDown,
            enabled = index < totalCount - 1,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down",
                tint = if (index < totalCount - 1) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }

        // Track info
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
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
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown" },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 96.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}
