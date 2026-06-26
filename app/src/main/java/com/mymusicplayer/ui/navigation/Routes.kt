package com.mymusicplayer.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings")
    data object Scan : Screen("scan")
    data object DirectoryPicker : Screen("directory_picker")
}
