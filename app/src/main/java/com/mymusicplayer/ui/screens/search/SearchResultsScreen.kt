package com.mymusicplayer.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mymusicplayer.ui.components.SearchAlbumRow
import com.mymusicplayer.ui.components.SearchArtistRow
import com.mymusicplayer.ui.components.SearchTopBar
import com.mymusicplayer.ui.components.SearchTrackRow
import com.mymusicplayer.ui.components.SectionHeader
import kotlinx.coroutines.delay

@Composable
fun SearchResultsScreen(
    query: String,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SearchResultsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf(state.currentQuery.ifBlank { query }) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.currentQuery) {
        if (state.currentQuery.isNotBlank() && state.currentQuery != searchQuery) {
            searchQuery = state.currentQuery
        }
    }
    LaunchedEffect(searchQuery) {
        delay(300)
        if (searchQuery.isNotBlank() && searchQuery != state.currentQuery) {
            viewModel.updateQuery(searchQuery.trim())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchTopBar(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    viewModel.updateQuery(searchQuery.trim())
                }
            },
            placeholder = "Search...",
            focusRequester = focusRequester,
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        ScrollableTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 12.dp,
            divider = {}
        ) {
            SearchTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            tab.name,
                            fontWeight = if (state.selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        if (state.isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (!state.hasResults) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error ?: "No results found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            when (state.selectedTab) {
                SearchTab.ALL -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (state.tracks.isNotEmpty()) {
                            item { SectionHeader("Tracks", state.tracks.size) }
                            items(state.tracks, key = { it.id }) { track ->
                                SearchTrackRow(track = track, query = state.currentQuery, onClick = {
                                    viewModel.playTrack(track); onNavigateToPlayer()
                                })
                            }
                        }
                        if (state.albums.isNotEmpty()) {
                            item { SectionHeader("Albums", state.albums.size) }
                            items(state.albums, key = { it.id }) { album ->
                                SearchAlbumRow(album = album, query = state.currentQuery, onClick = {
                                    onNavigateToAlbum(album.id)
                                })
                            }
                        }
                        if (state.artists.isNotEmpty()) {
                            item { SectionHeader("Artists", state.artists.size) }
                            items(state.artists, key = { it.id }) { artist ->
                                SearchArtistRow(artist = artist, query = state.currentQuery, onClick = {
                                    onNavigateToArtist(artist.id)
                                })
                            }
                        }
                    }
                }
                SearchTab.TRACKS -> {
                    if (state.tracks.isEmpty()) {
                        EmptyResults("No tracks found")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(state.tracks, key = { it.id }) { track ->
                                SearchTrackRow(track = track, query = state.currentQuery, onClick = {
                                    viewModel.playTrack(track); onNavigateToPlayer()
                                })
                            }
                        }
                    }
                }
                SearchTab.ALBUMS -> {
                    if (state.albums.isEmpty()) {
                        EmptyResults("No albums found")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(state.albums, key = { it.id }) { album ->
                                SearchAlbumRow(album = album, query = state.currentQuery, onClick = {
                                    onNavigateToAlbum(album.id)
                                })
                            }
                        }
                    }
                }
                SearchTab.ARTISTS -> {
                    if (state.artists.isEmpty()) {
                        EmptyResults("No artists found")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(state.artists, key = { it.id }) { artist ->
                                SearchArtistRow(artist = artist, query = state.currentQuery, onClick = {
                                    onNavigateToArtist(artist.id)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyResults(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
