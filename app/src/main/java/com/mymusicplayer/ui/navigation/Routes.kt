package com.mymusicplayer.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings")
    data object Scan : Screen("scan")
    data object DirectoryPicker : Screen("directory_picker")
    data object Favorites : Screen("favorites")
    data object RecentlyPlayed : Screen("recently_played")
    data object Playlists : Screen("playlists")
    data object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist_detail/$playlistId"
    }
    data object AlbumDetail : Screen("album_detail/{albumId}") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }
    data object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }
    data object MultiSelect : Screen("multi_select") {
        fun createRoute() = "multi_select"
    }
}
