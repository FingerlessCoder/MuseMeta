package com.mymusicplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(
    val screen: Screen,
    val icon: ImageVector
)

val navItems = listOf(
    NavItem(Screen.Home, Icons.Default.Home),
    NavItem(Screen.Library, Icons.Default.LibraryMusic),
    NavItem(Screen.NowPlaying, Icons.Default.PlayCircle),
    NavItem(Screen.Organize, Icons.Default.AutoAwesome)
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar {
        navItems.forEach { item ->
            val selected = when (item.screen) {
                Screen.Library -> currentRoute in listOf(
                    Screen.Library.route,
                    Screen.Tracks.route,
                    Screen.Albums.route,
                    Screen.Artists.route,
                    Screen.Playlists.route
                )
                Screen.NowPlaying -> currentRoute in listOf(Screen.NowPlaying.route, Screen.Player.route)
                Screen.Organize -> currentRoute in listOf(
                    Screen.Organize.route,
                    Screen.Scan.route,
                    Screen.DirectoryPicker.route,
                    Screen.Settings.route
                )
                else -> currentRoute?.startsWith(item.screen.route) == true
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen) },
                icon = { Icon(item.icon, contentDescription = item.screen.label) },
                label = { Text(item.screen.label) }
            )
        }
    }
}
