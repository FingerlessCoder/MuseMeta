package com.mymusicplayer.ui.screens.home

import com.mymusicplayer.domain.model.Album
import com.mymusicplayer.domain.model.Artist

/**
 * Which sort modes each Home tab understands, and which field the alphabet index
 * bar must bucket by for it.
 *
 * This lives apart from the Home UI so the ViewModel and the screen cannot drift
 * apart: the list order, the index-bar letters and the index-bar jump targets are
 * all derived from the same mode string. Two derivations of "what does this tab
 * sort by" is how the bar ended up bucketing albums by title while the list was
 * ordered by album artist.
 */
internal fun legalSortModes(tab: HomeTab): List<String> = when (tab) {
    HomeTab.Tracks -> listOf("name", "date_added", "play_count", "year", "genre", "artist", "album")
    HomeTab.Albums -> listOf("title", "album_artist", "year", "track_count")
    HomeTab.Artists -> listOf("name")
}

/** The mode a tab falls back to when the persisted default is not one of its own. */
internal fun fallbackSortMode(tab: HomeTab): String = legalSortModes(tab).first()

/**
 * The name the index bar buckets an album by, for the current [mode].
 *
 * Only the alphabetical modes (the ones that show a bar) need a meaningful
 * answer; for the numeric modes the bar is not displayed.
 */
internal fun Album.indexKey(mode: String): String = when (mode) {
    "album_artist" -> albumArtist?.takeIf { it.isNotBlank() } ?: title
    else -> title
}

/** The name the index bar buckets an artist by. */
internal fun Artist.indexKey(@Suppress("UNUSED_PARAMETER") mode: String): String = name
