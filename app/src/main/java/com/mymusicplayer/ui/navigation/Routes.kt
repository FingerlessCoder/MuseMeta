package com.mymusicplayer.ui.navigation

sealed class Screen(val route: String, val label: String, val icon: String) {
    data object Tracks : Screen("tracks", "Tracks", "music_note")
    data object Albums : Screen("albums", "Albums", "album")
    data object Artists : Screen("artists", "Artists", "people")
    data object Playlists : Screen("playlists", "Playlists", "queue_music")
    data object Settings : Screen("settings", "Settings", "settings")

    data object AlbumDetail : Screen("album/{albumId}", "Album", "album") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistId}", "Artist", "person") {
        fun createRoute(artistId: Long) = "artist/$artistId"
    }
    data object Player : Screen("player", "Player", "play_circle")
    data object PlaylistDetail : Screen("playlist/{playlistId}", "Playlist", "list") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
    data object SmartPlaylistEditor : Screen("smart_playlist_editor", "Smart Rule", "auto_awesome")
    data object Scan : Screen("scan", "Scan", "refresh")
    data object DirectoryPicker : Screen("directory_picker", "Directories", "folder")

    companion object {
        val bottomNavItems = listOf(Tracks, Albums, Artists, Playlists, Settings)
    }
}
