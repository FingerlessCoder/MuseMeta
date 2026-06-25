package com.mymusicplayer.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mymusicplayer.ui.screens.albums.AlbumListScreen
import com.mymusicplayer.ui.screens.artists.ArtistListScreen
import com.mymusicplayer.ui.screens.playlists.PlaylistListScreen
import com.mymusicplayer.ui.screens.tracks.TrackListScreen

private data class LibraryTab(
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        LibraryTab("Tracks", Icons.Default.MusicNote),
        LibraryTab("Albums", Icons.Default.Album),
        LibraryTab("Artists", Icons.Default.People),
        LibraryTab("Playlists", Icons.AutoMirrored.Filled.QueueMusic)
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Library")
                            Text(
                                "Browse, search, and shape your collection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            text = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> TrackListScreen(
                    onNavigateToPlayer = onNavigateToPlayer,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist
                )
                1 -> AlbumListScreen(onNavigateToAlbum = onNavigateToAlbum)
                2 -> ArtistListScreen(onNavigateToArtist = onNavigateToArtist)
                3 -> PlaylistListScreen(onNavigateToPlaylist = onNavigateToPlaylist)
            }
        }
    }
}
