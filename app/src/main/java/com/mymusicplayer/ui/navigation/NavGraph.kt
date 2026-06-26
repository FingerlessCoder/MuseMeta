package com.mymusicplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mymusicplayer.ui.screens.home.HomeScreen
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
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
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
