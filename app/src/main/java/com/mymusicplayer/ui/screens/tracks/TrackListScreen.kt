package com.mymusicplayer.ui.screens.tracks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mymusicplayer.domain.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    viewModel: TrackListViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filteredTracks = viewModel.getFilteredTracks()
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search tracks...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Tracks")
                    }
                },
                actions = {
                    if (state.isMultiSelectMode) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text("Select All")
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = { showSearchBar = !showSearchBar }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        val hasActiveFilter = state.minSize != null || state.maxSize != null ||
                            state.minDuration != null || state.maxDuration != null
                        IconButton(onClick = { viewModel.toggleFilter() }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name") },
                                    onClick = { viewModel.setSortMode("name"); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Added") },
                                    onClick = { viewModel.setSortMode("date_added"); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Play Count") },
                                    onClick = { viewModel.setSortMode("play_count"); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rating") },
                                    onClick = { viewModel.setSortMode("rating"); showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duration") },
                                    onClick = { viewModel.setSortMode("duration"); showSortMenu = false }
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.toggleMultiSelect()
                        }) {
                            Icon(Icons.Default.MusicNote, contentDescription = "Select")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.isShowingFilter) {
                FilterBar(
                    minSize = state.minSize,
                    maxSize = state.maxSize,
                    minDuration = state.minDuration,
                    maxDuration = state.maxDuration,
                    onApplySize = { min, max -> viewModel.setSizeFilter(min, max) },
                    onApplyDuration = { min, max -> viewModel.setDurationFilter(min, max) },
                    onDismiss = { viewModel.toggleFilter() }
                )
            }

            val hasFilter = state.minSize != null || state.maxSize != null ||
                state.minDuration != null || state.maxDuration != null
            if (hasFilter && !state.isShowingFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildFilterLabel(state.minSize, state.maxSize, state.minDuration, state.maxDuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable {
                            viewModel.setSizeFilter(null, null)
                            viewModel.setDurationFilter(null, null)
                        }
                    )
                }
            }

            if (filteredTracks.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tracks found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val listState = rememberLazyListState()
                val letters = remember(filteredTracks) {
                    filteredTracks.map { it.title.firstOrNull()?.uppercase() ?: "#" }
                        .distinct().sorted()
                }
                val sectionIndices = remember(filteredTracks) {
                    letters.map { letter ->
                        filteredTracks.indexOfFirst { track ->
                            (track.title.firstOrNull()?.uppercase() ?: "#") == letter
                        }.coerceAtLeast(0)
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            TrackListItem(
                                track = track,
                                isSelected = track.id in state.selectedTrackIds,
                                isMultiSelectMode = state.isMultiSelectMode,
                                onClick = {
                                    if (state.isMultiSelectMode) {
                                        viewModel.toggleTrackSelection(track.id)
                                    } else {
                                        viewModel.playTrack(track)
                                        onNavigateToPlayer()
                                    }
                                },
                                onLongClick = {
                                    if (!state.isMultiSelectMode) {
                                        viewModel.toggleMultiSelect()
                                        viewModel.toggleTrackSelection(track.id)
                                    }
                                },
                                onPlay = {
                                    viewModel.playTrack(track)
                                    onNavigateToPlayer()
                                },
                                onAlbumClick = { track.album?.let { onNavigateToAlbum(it.id) } },
                                onArtistClick = { track.artists.firstOrNull()?.let { onNavigateToArtist(it.id) } }
                            )
                            HorizontalDivider()
                        }
                    }

                    // Alphabet sidebar
                    if (!state.isMultiSelectMode && letters.size > 1) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(24.dp)
                                .padding(vertical = 8.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val idx = (offset.y / size.height * letters.size)
                                            .toInt().coerceIn(0, letters.size - 1)
                                        val targetIndex = sectionIndices[idx]
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                },
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            letters.forEach { letter ->
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackListItem(
    track: Track,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (track.album?.artPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file://${track.album.artPath}")
                        .crossfade(true)
                        .build(),
                    contentDescription = track.album?.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row {
                track.artists.take(3).forEachIndexed { index, artist ->
                    if (index > 0) Text(", ", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
                if (track.artists.size > 3) {
                    Text(
                        text = " +${track.artists.size - 3}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row {
                track.album?.let { album ->
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isMultiSelectMode) {
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FilterBar(
    minSize: Long?,
    maxSize: Long?,
    minDuration: Long?,
    maxDuration: Long?,
    onApplySize: (Long?, Long?) -> Unit,
    onApplyDuration: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var minSizeText by remember(minSize) { mutableStateOf(minSize?.let { (it / 1_000_000).toString() } ?: "") }
    var maxSizeText by remember(maxSize) { mutableStateOf(maxSize?.let { (it / 1_000_000).toString() } ?: "") }
    var minDurText by remember(minDuration) { mutableStateOf(minDuration?.let { (it / 60_000).toString() } ?: "") }
    var maxDurText by remember(maxDuration) { mutableStateOf(maxDuration?.let { (it / 60_000).toString() } ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filter", style = MaterialTheme.typography.titleSmall)
            Row {
                TextButton(onClick = {
                    minSizeText = ""
                    maxSizeText = ""
                    minDurText = ""
                    maxDurText = ""
                    onApplySize(null, null)
                    onApplyDuration(null, null)
                }) {
                    Text("Clear")
                }
                TextButton(onClick = {
                    val minS = minSizeText.toLongOrNull()?.let { it * 1_000_000 }
                    val maxS = maxSizeText.toLongOrNull()?.let { it * 1_000_000 }
                    val minD = minDurText.toLongOrNull()?.let { it * 60_000 }
                    val maxD = maxDurText.toLongOrNull()?.let { it * 60_000 }
                    onApplySize(minS, maxS)
                    onApplyDuration(minD, maxD)
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("File Size (MB)", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = minSizeText,
                onValueChange = { minSizeText = it.filter { c -> c.isDigit() } },
                label = { Text("Min") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = maxSizeText,
                onValueChange = { maxSizeText = it.filter { c -> c.isDigit() } },
                label = { Text("Max") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Duration (minutes)", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = minDurText,
                onValueChange = { minDurText = it.filter { c -> c.isDigit() } },
                label = { Text("Min") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = maxDurText,
                onValueChange = { maxDurText = it.filter { c -> c.isDigit() } },
                label = { Text("Max") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun buildFilterLabel(
    minSize: Long?,
    maxSize: Long?,
    minDuration: Long?,
    maxDuration: Long?
): String {
    val parts = mutableListOf<String>()
    if (minSize != null || maxSize != null) {
        val min = minSize?.let { "${it / 1_000_000}MB" } ?: "0MB"
        val max = maxSize?.let { "${it / 1_000_000}MB" } ?: "∞"
        parts.add("Size: $min-$max")
    }
    if (minDuration != null || maxDuration != null) {
        val min = minDuration?.let { "${it / 60_000}m" } ?: "0m"
        val max = maxDuration?.let { "${it / 60_000}m" } ?: "∞"
        parts.add("Duration: $min-$max")
    }
    return parts.joinToString(" | ")
}
