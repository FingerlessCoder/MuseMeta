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
import com.mymusicplayer.ui.screens.home.HomeScreen
import com.mymusicplayer.ui.screens.library.LibraryScreen
import com.mymusicplayer.ui.screens.organize.OrganizeScreen
import com.mymusicplayer.ui.screens.player.PlayerScreen
import com.mymusicplayer.ui.screens.playlists.PlaylistDetailScreen
import com.mymusicplayer.ui.screens.playlists.PlaylistListScreen
import com.mymusicplayer.ui.screens.directory_picker.DirectoryPickerScreen
import com.mymusicplayer.ui.screens.scan.ScanScreen
import com.mymusicplayer.ui.screens.settings.SettingsScreen
import com.mymusicplayer.ui.screens.tracks.TrackListScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToOrganize = { navController.navigate(Screen.Organize.route) },
                onNavigateToTrack = {
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                },
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(Screen.NowPlaying.route) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onNavigateToArtist = { artistId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistId))
                }
            )
        }

        composable(Screen.Organize.route) {
            OrganizeScreen(
                onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                onNavigateToDirectoryPicker = { navController.navigate(Screen.DirectoryPicker.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

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
            SettingsScreen(
                onNavigateToScan = { navController.navigate(Screen.Scan.route) },
                onNavigateToDirectoryPicker = { navController.navigate(Screen.DirectoryPicker.route) }
            )
        }

        composable(Screen.Scan.route) {
            ScanScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DirectoryPicker.route) {
            DirectoryPickerScreen(
                onBack = { navController.popBackStack() },
                onStartScan = {
                    navController.popBackStack(Screen.Organize.route, false)
                    navController.navigate(Screen.Scan.route)
                }
            )
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
