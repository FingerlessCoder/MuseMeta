package com.mymusicplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
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
    NavItem(Screen.Tracks, Icons.Default.MusicNote),
    NavItem(Screen.Albums, Icons.Default.Album),
    NavItem(Screen.Artists, Icons.Default.People),
    NavItem(Screen.Playlists, Icons.Default.QueueMusic),
    NavItem(Screen.Settings, Icons.Default.Settings)
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar {
        navItems.forEach { item ->
            val selected = currentRoute?.startsWith(item.screen.route) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen) },
                icon = { Icon(item.icon, contentDescription = item.screen.label) },
                label = { Text(item.screen.label) }
            )
        }
    }
}
