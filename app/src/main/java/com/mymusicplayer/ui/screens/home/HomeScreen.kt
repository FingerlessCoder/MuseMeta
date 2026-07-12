package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.io.File
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.ui.components.AlphabetIndexBar
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSortSheet by remember { mutableStateOf(false) }

    val favoriteArt = state.favoriteTracks.firstOrNull()?.album?.artPath
    val playlistArt = state.tracks.firstOrNull()?.album?.artPath
    val recentArt = state.recentlyPlayed.firstOrNull()?.album?.artPath

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                val tracks = state.filteredTracks

                val groupedTracks = remember(tracks, state.sortMode) {
                    if (state.sortMode != "name") return@remember null
                    val groups = mutableMapOf<String, MutableList<Track>>()
                    for (track in tracks) {
                        val c = track.title.firstOrNull()?.uppercase() ?: "#"
                        val letter = if (c.length == 1 && c[0] in 'A'..'Z') c else "#"
                        groups.getOrPut(letter) { mutableListOf() }.add(track)
                    }
                    val sorted = linkedMapOf<String, List<Track>>()
                    groups.keys.filter { it != "#" }.sorted().forEach { sorted[it] = groups[it]!! }
                    if ("#" in groups) sorted["#"] = groups["#"]!!
                    sorted
                }

                data class BarLayoutInfo(val topOffsetPx: Int, val heightPx: Int)

                val canShowBar = state.selectedTab == HomeTab.Tracks && state.sortMode == "name" && tracks.isNotEmpty()

                val bottomPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }.toInt()

                val barLayout by remember(listState, bottomPaddingPx) {
                    derivedStateOf {
                        if (listState.firstVisibleItemIndex < 1) return@derivedStateOf null
                        val info = listState.layoutInfo
                        if (info.visibleItemsInfo.none { it.index >= 2 }) return@derivedStateOf null
                        val viewportH = info.viewportEndOffset - info.viewportStartOffset
                        if (viewportH <= 0) return@derivedStateOf null
                        val topPx = info.visibleItemsInfo
                            .firstOrNull { it.index == 1 }?.size ?: 0
                        val heightPx = (viewportH - topPx - bottomPaddingPx).coerceAtLeast(0)
                        BarLayoutInfo(topPx, heightPx)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = 16.dp
                    )
                ) {
                    // Mini cards — scroll away on scroll
                    item(key = "mini_cards") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactMiniCard(
                                label = "Favorites", count = state.favoriteCount,
                                artPath = favoriteArt,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMiniCard(
                                label = "My Playlists", count = state.playlistCount,
                                artPath = playlistArt,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMiniCard(
                                label = "Recently Played", count = state.recentlyPlayedCount,
                                artPath = recentArt,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Sticky header: tabs + filter controls pinned on scroll
                    stickyHeader(key = "tab_bar") {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            tonalElevation = 0.dp
                        ) {
                            Column {
                                // tab chips
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HomeTab.entries.forEach { tab ->
                                        val selected = state.selectedTab == tab
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.selectTab(tab) },
                                            label = {
                                                Text(tab.name,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 4.dp))
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                // filter controls
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { viewModel.playRandom() }) {
                                        Icon(Icons.Default.Shuffle, contentDescription = null,
                                            modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Random Play", style = MaterialTheme.typography.labelLarge)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(onClick = { showSortSheet = true },
                                            modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.ImportExport,
                                                contentDescription = "Sort",
                                                modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { viewModel.toggleMultiSelect() },
                                            modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                if (state.multiSelectEnabled) Icons.Default.CheckBox
                                                else Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = "Multi-select",
                                                tint = if (state.multiSelectEnabled) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Content based on selected tab
                    when (state.selectedTab) {
                        HomeTab.Tracks -> {
                            if (tracks.isEmpty()) {
                                item(key = "empty_tracks") {
                                    EmptyPlaceholder("No tracks found")
                                }
                            } else if (groupedTracks != null) {
                                groupedTracks.forEach { (letter, group) ->
                                    item(key = "section_$letter") {
                                        SectionHeaderRow(letter = letter)
                                    }
                                    items(group, key = { it.id }) { track ->
                                        TrackContentRow(track = track,
                                            isMultiSelect = state.multiSelectEnabled,
                                            isSelected = track.id in state.selectedTrackIds,
                                            onPlay = { viewModel.playTrack(track); onNavigateToPlayer() },
                                            onToggleSelect = { viewModel.toggleTrackSelection(track.id) },
                                            showIndexBar = barLayout != null,
                                            context = context)
                                    }
                                }
                            } else {
                                items(tracks, key = { it.id }) { track ->
                                    TrackContentRow(track = track,
                                        isMultiSelect = state.multiSelectEnabled,
                                        isSelected = track.id in state.selectedTrackIds,
                                        onPlay = { viewModel.playTrack(track); onNavigateToPlayer() },
                                        onToggleSelect = { viewModel.toggleTrackSelection(track.id) },
                                        showIndexBar = barLayout != null,
                                        context = context)
                                }
                            }
                        }
                        HomeTab.Albums -> {
                            val albums = state.filteredAlbums
                            if (albums.isEmpty()) {
                                item(key = "empty_albums") {
                                    EmptyPlaceholder("No albums found")
                                }
                            } else {
                                albums.chunked(2).forEachIndexed { i, row ->
                                    item(key = "album_row_$i") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            row.forEach { album ->
                                                AlbumGridItem(album = album,
                                                    onClick = { viewModel.playAlbum(album); onNavigateToPlayer() },
                                                    modifier = Modifier.weight(1f), context = context)
                                            }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        HomeTab.Artists -> {
                            val artists = state.filteredArtists
                            if (artists.isEmpty()) {
                                item(key = "empty_artists") {
                                    EmptyPlaceholder("No artists found")
                                }
                            } else {
                                artists.chunked(4).forEachIndexed { i, row ->
                                    item(key = "artist_row_$i") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            row.forEach { artist ->
                                                ArtistGridItem(artist = artist,
                                                    onClick = { viewModel.playArtistTracks(artist); onNavigateToPlayer() },
                                                    modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canShowBar) {
                    val layout = barLayout
                    if (layout != null) {
                        val density = LocalDensity.current
                        val topDp = with(density) { layout.topOffsetPx.toDp() }
                        val heightDp = with(density) { layout.heightPx.toDp() }
                        val letters = groupedTracks?.keys?.toList() ?: emptyList()
                        val sectionIndices = remember(groupedTracks) {
                            if (groupedTracks == null) return@remember emptyList()
                            val indices = mutableListOf<Int>()
                            var cumIdx = 2
                            for ((_, group) in groupedTracks) {
                                indices.add(cumIdx)
                                cumIdx += 1 + group.size
                            }
                            indices
                        }

                        var activeLetter by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(listState, letters, sectionIndices) {
                            snapshotFlow {
                                val items = listState.layoutInfo.visibleItemsInfo
                                    .filter { it.index > 1 }
                                // Use ~1 content row height as threshold so the highlight
                                // doesn't change when a row is barely peeking at the top.
                                val threshold = items.firstOrNull()?.size ?: 0
                                items.firstOrNull { it.offset >= threshold }?.index ?: -1
                            }.collect { firstIdx ->
                                if (letters.isEmpty()) return@collect
                                var bestIdx = -1
                                for (i in sectionIndices.indices) {
                                    if (sectionIndices[i] <= firstIdx) bestIdx = i
                                }
                                activeLetter = if (bestIdx >= 0) letters[bestIdx] else null
                            }
                        }

                        var draggedLetter by remember { mutableStateOf<String?>(null) }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = topDp)
                                .height(heightDp)
                                .width(26.dp)
                        ) {
                            AlphabetIndexBar(
                                letters = letters,
                                sectionIndices = sectionIndices,
                                listState = listState,
                                activeLetter = draggedLetter ?: activeLetter,
                                onDragLetterChanged = { draggedLetter = it },
                                modifier = Modifier.fillMaxSize()
                            )

                            draggedLetter?.let { letter ->
                                val letterIdx = letters.indexOf(letter)
                                if (letterIdx >= 0) {
                                    val itemHeight = heightDp / letters.size
                                    val bubbleY = itemHeight * (letterIdx + 0.5f) - 20.dp
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (-48).dp, y = bubbleY)
                                            .size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        shadowElevation = 6.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = letter,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSortSheet) {
            SortBottomSheet(
                currentSort = state.sortMode,
                onSelect = { sort ->
                    viewModel.setSortMode(sort)
                    showSortSheet = false
                },
                onDismiss = { showSortSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentSort: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sortOptions = listOf(
        Triple("By Name", "name", "Sort tracks alphabetically"),
        Triple("Date Added", "date_added", "Sort by when tracks were added"),
        Triple("Play Frequency", "play_count", "Sort by most played")
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                "Sort by",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            sortOptions.forEach { (label, value, description) ->
                val isSelected = currentSort == value
                Surface(
                    onClick = { onSelect(value) },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMiniCard(label: String, count: Int, artPath: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artPath != null) {
                val ctx = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(File(artPath)).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (artPath != null) Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    ) else Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        )
                    )
                )
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyPlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeaderRow(letter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TrackContentRow(track: Track, isMultiSelect: Boolean, isSelected: Boolean,
                            onPlay: () -> Unit, onToggleSelect: () -> Unit,
                            showIndexBar: Boolean = false, context: android.content.Context) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { if (isMultiSelect) onToggleSelect() else onPlay() }
            .padding(start = 12.dp, top = 8.dp, end = if (showIndexBar) 48.dp else 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelect) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.padding(end = 8.dp))
        }
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (track.album?.artPath != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(File(track.album.artPath)).crossfade(true).build(),
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
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = buildString {
                    append(track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" })
                    if (track.album != null) {
                        append(" | ${track.album.title}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isMultiSelect) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = if (isMultiSelect) 72.dp else 68.dp, end = 12.dp))
}

@Composable
private fun AlbumGridItem(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, context: android.content.Context) {
    Card(onClick = onClick, modifier = modifier.padding(bottom = 10.dp), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                if (album.artPath != null) {
                    AsyncImage(model = ImageRequest.Builder(context).data(File(album.artPath)).crossfade(true).build(),
                        contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(album.albumArtist ?: "Unknown Artist", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ArtistGridItem(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(4.dp))
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
    }
}
