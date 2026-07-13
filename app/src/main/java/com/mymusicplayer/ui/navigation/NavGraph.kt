package com.mymusicplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mymusicplayer.ui.screens.home.FavoritesScreen
import com.mymusicplayer.ui.screens.home.HomeScreen
import com.mymusicplayer.ui.screens.home.PlaylistDetailScreen
import com.mymusicplayer.ui.screens.home.PlaylistsScreen
import com.mymusicplayer.ui.screens.home.RecentlyPlayedScreen
import com.mymusicplayer.ui.screens.player.PlayerScreen
import com.mymusicplayer.ui.screens.search.SearchScreen
import com.mymusicplayer.ui.screens.settings.SettingsScreen
import com.mymusicplayer.ui.screens.scan.ScanScreen
import com.mymusicplayer.ui.screens.directory_picker.DirectoryPickerScreen

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPlayer = { navController.navigate(Screen.NowPlaying.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToFavorites = { navController.navigate(Screen.Favorites.route) },
                onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },
                onNavigateToRecentlyPlayed = { navController.navigate(Screen.RecentlyPlayed.route) }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToPlayer = { navController.navigate(Screen.NowPlaying.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NowPlaying.route) {
            PlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(Screen.NowPlaying.route) }
            )
        }

        composable(Screen.RecentlyPlayed.route) {
            RecentlyPlayedScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = { navController.navigate(Screen.NowPlaying.route) }
            )
        }

        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                onBack = { navController.popBackStack() },
                onPlaylistClick = { playlistId, playlistName ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                playlistName = "",
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = {
                    navController.navigate(Screen.NowPlaying.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                onNavigateToDirectoryPicker = { navController.navigate(Screen.DirectoryPicker.route) }
            )
        }

        composable(Screen.Scan.route) {
            ScanScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.DirectoryPicker.route) {
            DirectoryPickerScreen(
                onBack = { navController.popBackStack() },
                onStartScan = {
                    navController.popBackStack(Screen.Settings.route, false)
                    navController.navigate(Screen.Scan.route)
                }
            )
        }
    }
}
