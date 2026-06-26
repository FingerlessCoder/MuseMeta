package com.mymusicplayer.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.ui.components.AlphabetIndexBar
import com.mymusicplayer.ui.components.computeIndexLetters
import com.mymusicplayer.ui.components.computeSectionIndices
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeTab.entries.forEach { tab ->
                    val selected = state.selectedTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        label = {
                            Text(tab.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.playRandom() }) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Random Play", style = MaterialTheme.typography.labelLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = {
                        val sorts = listOf("name", "date_added", "duration")
                        val current = sorts.indexOf(state.sortMode)
                        viewModel.setSortMode(sorts[(current + 1) % sorts.size])
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { viewModel.toggleMultiSelect() },
                        modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (state.multiSelectEnabled) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = "Multi-select",
                            tint = if (state.multiSelectEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                val tracks = state.filteredTracks

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                when (state.selectedTab) {
                HomeTab.Tracks -> {
                    val tracks = state.filteredTracks
                    if (tracks.isEmpty()) {
                        item { EmptyPlaceholder("No tracks found") }
                    } else {
                        items(tracks, key = { it.id }) { track ->
                            TrackContentRow(track = track,
                                isMultiSelect = state.multiSelectEnabled,
                                isSelected = track.id in state.selectedTrackIds,
                                onPlay = { viewModel.playTrack(track); onNavigateToPlayer() },
                                onToggleSelect = { viewModel.toggleTrackSelection(track.id) },
                                context = context)
                        }
                    }
                }
                HomeTab.Albums -> {
                    val albums = state.filteredAlbums
                    if (albums.isEmpty()) {
                        item { EmptyPlaceholder("No albums found") }
                    } else {
                        albums.chunked(2).forEach { row ->
                            item {
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
                        item { EmptyPlaceholder("No artists found") }
                    } else {
                        val chunked = artists.chunked(4)
                        chunked.forEach { row ->
                            item {
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

        if (state.selectedTab == HomeTab.Tracks && state.sortMode == "name" && tracks.isNotEmpty()) {
            val titles = tracks.map { it.title }
            val letters = computeIndexLetters(titles)
            val sectionIndices = computeSectionIndices(titles, letters).map { it + 3 }
            AlphabetIndexBar(
                letters = letters,
                sectionIndices = sectionIndices,
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
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
                    model = ImageRequest.Builder(ctx).data("file://$artPath").crossfade(true).build(),
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
private fun TrackContentRow(track: Track, isMultiSelect: Boolean, isSelected: Boolean,
                            onPlay: () -> Unit, onToggleSelect: () -> Unit, context: android.content.Context) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { if (isMultiSelect) onToggleSelect() else onPlay() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelect) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.padding(end = 8.dp))
        }
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (track.album?.artPath != null) {
                AsyncImage(model = ImageRequest.Builder(context).data("file://${track.album.artPath}").crossfade(true).build(),
                    contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
                    AsyncImage(model = ImageRequest.Builder(context).data("file://${album.artPath}").crossfade(true).build(),
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
