package com.mymusicplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mymusicplayer.ui.screens.albums.AlbumDetailScreen
import com.mymusicplayer.ui.screens.albums.AlbumListScreen
import com.mymusicplayer.ui.screens.artists.ArtistDetailScreen
import com.mymusicplayer.ui.screens.artists.ArtistListScreen
import com.mymusicplayer.ui.screens.player.PlayerScreen
import com.mymusicplayer.ui.screens.playlists.PlaylistDetailScreen
import com.mymusicplayer.ui.screens.playlists.PlaylistListScreen
import com.mymusicplayer.ui.screens.settings.SettingsScreen
import com.mymusicplayer.ui.screens.tracks.TrackListScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Tracks.route,
        modifier = modifier
    ) {
        composable(Screen.Tracks.route) {
            TrackListScreen(
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                }
            )
        }

        composable(Screen.Albums.route) {
            AlbumListScreen(
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToArtist = { artistId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Artists.route) {
            ArtistListScreen(
                onNavigateToArtist = { artistId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                }
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("artistId") ?: return@composable
            ArtistDetailScreen(
                artistId = artistId,
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Playlists.route) {
            PlaylistListScreen(
                onNavigateToPlaylist = { playlistId ->
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
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.Player.route) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArtist = { artistId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                }
            )
        }
    }
}
