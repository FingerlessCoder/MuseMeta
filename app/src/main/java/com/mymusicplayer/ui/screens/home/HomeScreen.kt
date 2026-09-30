package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.io.File
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.data.audio.MusicPlayerController
import com.mymusicplayer.domain.repository.MusicRepository
import com.mymusicplayer.ui.components.AlphabetIndexBar
import com.mymusicplayer.ui.components.DragScrollbar
import com.mymusicplayer.ui.components.PlaylistSelectorSheet
import com.mymusicplayer.ui.components.SearchTopBarFake
import com.mymusicplayer.ui.components.TrackActionsSheet
import com.mymusicplayer.ui.components.ScrollbarMath
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToRecentlyPlayed: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToMultiSelect: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicPlayerController: MusicPlayerController = koinInject()
    val musicRepository: MusicRepository = koinInject()
    var showSortMenu by remember { mutableStateOf(false) }
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTrack by remember { mutableStateOf<Track?>(null) }

    val favoriteArt = state.favoriteTracks.firstOrNull()?.album?.artPath
    val playlistArt = state.tracks.firstOrNull()?.album?.artPath
    val recentArt = state.recentlyPlayed.firstOrNull()?.album?.artPath

    val darkModeBackground = MaterialTheme.colorScheme.background
    Column(modifier = Modifier.fillMaxSize().background(darkModeBackground)) {


        SearchTopBarFake(
            onClick = onNavigateToSearch,
            leading = {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Open settings menu")
                }
            }
        )

        if (state.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                val tracks = state.filteredTracks

                val groupedTracks = remember(tracks, state.sortMode, state.sortDir) {
                    when (state.sortMode) {
                        "name" -> {
                            val groups = mutableMapOf<String, MutableList<Track>>()
                            for (track in tracks) {
                                val c = track.title.firstOrNull()?.uppercaseChar() ?: '#'
                                val letter = if (c in 'A'..'Z') c.toString() else "#"
                                groups.getOrPut(letter) { mutableListOf() }.add(track)
                            }
                            // Descending groups come out as [Z..A, #] with items
                            // in desc order. # stays pinned at the end in both
                            // directions — stranding it on top in desc made its
                            // jump target feel disconnected from the list tail.
                            val sorted = linkedMapOf<String, List<Track>>()
                            if (state.sortDir == "desc") {
                                groups.keys.filter { it != "#" }.sortedDescending()
                                    .forEach { sorted[it] = groups[it]!! }
                                if ("#" in groups) sorted["#"] = groups["#"]!!
                            } else {
                                groups.keys.filter { it != "#" }.sorted()
                                    .forEach { sorted[it] = groups[it]!! }
                                if ("#" in groups) sorted["#"] = groups["#"]!!
                            }
                            sorted
                        }
                        "year" -> {
                            val groups = mutableMapOf<String, MutableList<Track>>()
                            for (track in tracks) {
                                val year = track.year?.toString() ?: "Unknown"
                                groups.getOrPut(year) { mutableListOf() }.add(track)
                            }
                            val sorted = linkedMapOf<String, List<Track>>()
                            groups.keys.filter { it != "Unknown" }.sortedDescending().forEach { sorted[it] = groups[it]!! }
                            if ("Unknown" in groups) sorted["Unknown"] = groups["Unknown"]!!
                            sorted
                        }
                        "genre" -> {
                            val groups = mutableMapOf<String, MutableList<Track>>()
                            for (track in tracks) {
                                val genre = track.genre?.takeIf { it.isNotBlank() } ?: "Unknown"
                                groups.getOrPut(genre) { mutableListOf() }.add(track)
                            }
                            val sorted = linkedMapOf<String, List<Track>>()
                            groups.keys.filter { it != "Unknown" }.sorted().forEach { sorted[it] = groups[it]!! }
                            if ("Unknown" in groups) sorted["Unknown"] = groups["Unknown"]!!
                            sorted
                        }
                        else -> null
                    }
                }

                val albums = state.filteredAlbums
                val artists = state.filteredArtists

                val isDesc = state.sortDir == "desc"

                // Tracks groups by its own `groupedTracks` (section header per
                // letter), so its list is grouped the same way the bar is drawn.
                //
                // Grid tabs (albums 2-col, artists 4-col) are ordered by the
                // ViewModel through ScrollbarMath.indexLetterComparator, which
                // emits A..Z followed by one trailing `#` bucket and keeps that
                // bucket last in BOTH directions. So the displayed list is already
                // grouped exactly the way the bar is drawn, and letters plus jump
                // rows are plain first-appearance — no ascending/descending
                // remapping, which is what used to put `(g)i-dle` at the top of
                // Artists while the bar's single `#` sat at the bottom.
                val (barLetters, barSections) = remember(
                    state.selectedTab, state.sortMode, state.sortDir,
                    tracks, albums, artists, groupedTracks
                ) {
                    when (state.selectedTab) {
                        HomeTab.Tracks -> {
                            val letters = groupedTracks?.keys?.toList() ?: emptyList()
                            // Each track group is preceded by its own section
                            // header item, on top of the mini-cards + tab-bar rows.
                            val indices = mutableListOf<Int>()
                            var cumIdx = 2
                            groupedTracks?.forEach { (_, group) ->
                                indices.add(cumIdx)
                                cumIdx += 1 + group.size
                            }
                            letters to indices
                        }
                        HomeTab.Albums -> ScrollbarMath.sectionsForGroupedList(
                            names = albums.map { it.indexKey(state.sortMode) },
                            span = 2
                        )
                        HomeTab.Artists -> ScrollbarMath.sectionsForGroupedList(
                            names = artists.map { it.indexKey(state.sortMode) },
                            span = 4
                        )
                    }
                }

                val canShowBar = when (state.selectedTab) {
                    HomeTab.Tracks -> tracks.isNotEmpty()
                    HomeTab.Albums -> albums.isNotEmpty()
                    HomeTab.Artists -> artists.isNotEmpty()
                }
                // Only the alphabetical modes actually list their items by name,
                // so only they can carry a name-keyed index. Tracks additionally
                // needs "name" because its year/genre grouping keys are years and
                // genre strings, which no letter bar can address.
                val useIndexBar = canShowBar && barLetters.size > 1 && when (state.selectedTab) {
                    HomeTab.Tracks -> state.sortMode == "name"
                    HomeTab.Albums -> state.sortMode == "title" || state.sortMode == "album_artist"
                    HomeTab.Artists -> true
                }

                data class BarLayoutInfo(val topOffsetPx: Int, val heightPx: Int)

                val bottomPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }.toInt()
                val density = LocalDensity.current

                // The bar spans the same band the alphabet bar does: from just
                // under the pinned sticky header down to the mini player.
                //
                // The header height comes from the header's own laid-out size, and
                // the bar hides while the header is still scrolling up past the
                // mini cards. Two reasons: measuring the header through
                // `onSizeChanged` disagreed with the real list geometry (the chip
                // row and the sort/multi-select row live in one Surface), which
                // is what let the bar's top overflow into those controls; and at
                // that scroll position the header is not pinned yet, so a bar
                // drawn at the header's final height would float over the mini
                // cards with nothing under it.
                val barLayout by remember(canShowBar, listState, bottomPaddingPx) {
                    derivedStateOf {
                        if (!canShowBar) return@derivedStateOf null
                        val info = listState.layoutInfo
                        val viewportH = info.viewportEndOffset - info.viewportStartOffset
                        if (viewportH <= 0) return@derivedStateOf null
                        val header = info.visibleItemsInfo.firstOrNull { it.index == 1 }
                            ?: return@derivedStateOf null
                        // Pinned means the header is flush with the viewport top.
                        if (header.offset > info.viewportStartOffset + 1) return@derivedStateOf null
                        val topPx = header.size
                        val heightPx = (viewportH - topPx - bottomPaddingPx).coerceAtLeast(0)
                        if (heightPx <= 0) return@derivedStateOf null
                        BarLayoutInfo(topPx, heightPx)
                    }
                }

                val activeLetter = remember(canShowBar, listState, barLetters, barSections) {
                    derivedStateOf {
                        if (!canShowBar) return@derivedStateOf null
                        val items = listState.layoutInfo.visibleItemsInfo
                            .filter { it.index > 1 }
                        val threshold = items.firstOrNull()?.size ?: 0
                        val firstIdx = items.firstOrNull { it.offset >= threshold }?.index ?: -1
                        if (firstIdx < 0 || barLetters.isEmpty()) return@derivedStateOf null
                        var bestIdx = -1
                        for (i in barSections.indices) {
                            if (barSections[i] <= firstIdx) bestIdx = i
                        }
                        if (bestIdx >= 0) barLetters[bestIdx] else null
                    }
                }

                var draggedLetter by remember { mutableStateOf<String?>(null) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item(key = "mini_cards") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactMiniCard(
                                label = "Favorites", count = state.favoriteCount,
                                artPath = favoriteArt,
                                onClick = onNavigateToFavorites,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMiniCard(
                                label = "My Playlists", count = state.playlistCount,
                                artPath = playlistArt,
                                onClick = onNavigateToPlaylists,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMiniCard(
                                label = "Recently Played", count = state.recentlyPlayedCount,
                                artPath = recentArt,
                                onClick = onNavigateToRecentlyPlayed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    stickyHeader(key = "tab_bar") {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            tonalElevation = 0.dp
                        ) {
                            Column {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val (label, icon) = when (state.selectedTab) {
                                            HomeTab.Tracks -> "Track" to Icons.Default.Shuffle
                                            HomeTab.Albums -> "Album" to Icons.Default.Album
                                            HomeTab.Artists -> "Artist" to Icons.Default.Person
                                        }
                                        AssistChip(
                                            onClick = { viewModel.playRandomForCurrentTab(onNavigateToPlayer) },
                                            label = { Text("Random $label", style = MaterialTheme.typography.labelSmall) },
                                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                     Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Box {
                                            IconButton(
                                                onClick = { showSortMenu = !showSortMenu },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    sortCriterionIcon(state.selectedTab, state.sortMode),
                                                    contentDescription = "Sort by",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            SortDropdownMenu(
                                                expanded = showSortMenu,
                                                selectedTab = state.selectedTab,
                                                currentSort = state.sortMode,
                                                onSelect = { sort ->
                                                    viewModel.setSortMode(sort)
                                                    showSortMenu = false
                                                },
                                                onDismiss = { showSortMenu = false }
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.setSortDir(if (isDesc) "asc" else "desc") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                if (isDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = if (isDesc) "Sort descending" else "Sort ascending",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(onClick = { onNavigateToMultiSelect() },
                                            modifier = Modifier.size(36.dp)) {
                                            Icon(
                                                Icons.Default.CheckBoxOutlineBlank,
                                                contentDescription = "Multi-select",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                                        TrackContentRow(
                                            track = track,
                                            onPlay = { viewModel.playTrack(track); onNavigateToPlayer() },
                                            onMore = { moreTrack = track },
                                            context = context,
                                            showIndexBar = barLayout != null
                                        )
                                    }
                                }
                            } else {
                                items(tracks, key = { it.id }) { track ->
                                    TrackContentRow(
                                        track = track,
                                        onPlay = { viewModel.playTrack(track); onNavigateToPlayer() },
                                        onMore = { moreTrack = track },
                                        context = context,
                                        showIndexBar = barLayout != null
                                    )
                                }
                            }
                        }
                        HomeTab.Albums -> {
                            if (albums.isEmpty()) {
                                item(key = "empty_albums") {
                                    EmptyPlaceholder("No albums found")
                                }
                            }
                            albums.chunked(2).forEachIndexed { i, row ->
                                item(key = "album_row_$i") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(start = 12.dp, end = if (canShowBar) 38.dp else 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        row.forEach { album ->
                                            AlbumGridItem(album = album,
                                                onClick = { onNavigateToAlbum(album.id) },
                                                modifier = Modifier.weight(1f), context = context)
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        HomeTab.Artists -> {
                            if (artists.isEmpty()) {
                                item(key = "empty_artists") {
                                    EmptyPlaceholder("No artists found")
                                }
                            }
                            artists.chunked(4).forEachIndexed { i, row ->
                                item(key = "artist_row_$i") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(start = 12.dp, end = if (canShowBar) 38.dp else 12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        row.forEach { artist ->
                                            ArtistGridItem(
                                                artist = artist,
                                                artPath = state.artistArtMap[artist.id],
                                                onClick = { onNavigateToArtist(artist.id) },
                                                modifier = Modifier.weight(1f))
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
                        val letters = barLetters
                        val sectionIndices = barSections

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = topDp)
                                .height(heightDp)
                                .width(26.dp)
                        ) {
                            if (useIndexBar) {
                                AlphabetIndexBar(
                                    letters = letters,
                                    sectionIndices = sectionIndices,
                                    listState = listState,
                                    activeLetter = null,
                                    highlightedLetter = activeLetter.value,
                                    onDragLetterChanged = { draggedLetter = it },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // headerItems = 2: the mini-cards row and the
                                // pinned tab/sort bar sit above the scrollable
                                // content and are excluded from the item-size
                                // measurement, which would otherwise be skewed by
                                // their very different heights.
                                DragScrollbar(
                                    listState = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    headerItems = 2
                                )
                            }

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

        moreTrack?.let { target ->
            TrackActionsSheet(
                track = target,
                isFavorite = target.rating >= 4,
                onDismiss = { moreTrack = null },
                onPlayNext = { musicPlayerController.playNext(listOf(target)) },
                onAddToQueue = { musicPlayerController.addToQueue(listOf(target)) },
                onToggleFavorite = {
                    scope.launch {
                        musicRepository.updateTrackRating(target.id, if (target.rating >= 4) 0 else 5)
                    }
                },
                onAddToPlaylist = { playlistTrack = target },
                onDelete = {
                    scope.launch { musicRepository.deleteTrackById(target.id) }
                }
            )
        }

        playlistTrack?.let { target ->
            PlaylistSelectorSheet(
                trackIds = listOf(target.id),
                onDismiss = { playlistTrack = null },
                onAdded = { playlistTrack = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    selectedTab: HomeTab,
    currentSort: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(220.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        sortCriteria(selectedTab).forEach { option ->
            val isSelected = currentSort == option.sort
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelect(option.sort) },
                leadingIcon = {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
                colors = MenuDefaults.itemColors(
                    textColor = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

private data class SortCriterion(val label: String, val sort: String, val icon: ImageVector)

private fun sortCriteria(tab: HomeTab): List<SortCriterion> = when (tab) {
    HomeTab.Tracks -> listOf(
        SortCriterion("Name", "name", Icons.Default.SortByAlpha),
        SortCriterion("Date Added", "date_added", Icons.Default.Schedule),
        SortCriterion("Play Count", "play_count", Icons.Default.TrendingUp),
        SortCriterion("Year", "year", Icons.Default.DateRange),
        SortCriterion("Genre", "genre", Icons.Default.Category),
        SortCriterion("Artist", "artist", Icons.Default.Person),
        SortCriterion("Album", "album", Icons.Default.Album)
    )
    HomeTab.Albums -> listOf(
        SortCriterion("Title", "title", Icons.Default.SortByAlpha),
        SortCriterion("Album Artist", "album_artist", Icons.Default.Person),
        SortCriterion("Year", "year", Icons.Default.DateRange),
        SortCriterion("Track Count", "track_count", Icons.Default.FormatListNumbered)
    )
    HomeTab.Artists -> listOf(
        SortCriterion("Name", "name", Icons.Default.SortByAlpha)
    )
}

private fun sortCriterionIcon(tab: HomeTab, sort: String): ImageVector =
    sortCriteria(tab).firstOrNull { it.sort == sort }?.icon ?: Icons.Default.SortByAlpha

@Composable
private fun CompactMiniCard(label: String, count: Int, artPath: String?, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
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
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun SectionHeaderRow(letter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TrackContentRow(
    track: Track,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    context: android.content.Context,
    showIndexBar: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 12.dp, top = 8.dp, end = if (showIndexBar) 44.dp else 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            if (track.album?.artPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(track.album.artPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = "Music track icon",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val subtitle = remember(track.artists, track.album?.title) {
                buildString {
                    append(track.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" })
                    if (track.album != null) {
                        append(" | ${track.album.title}")
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert, contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 12.dp))
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
                        contentDescription = album.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Album, contentDescription = "Album cover", modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                Text(album.albumArtist ?: "Unknown Artist", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ArtistGridItem(artist: Artist, artPath: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            if (artPath != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(artPath))
                        .setParameter(
                            "art_mtime",
                            File(artPath).lastModified(),
                            File(artPath).lastModified().toString()
                        )
                        .crossfade(true).build(),
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(Icons.Default.Person, contentDescription = "Artist profile", modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = "Artist profile", modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
    }
}
