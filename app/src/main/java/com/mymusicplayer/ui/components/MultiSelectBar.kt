package com.mymusicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MultiSelectBar(
    selectedCount: Int,
    onPlaylistActions: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount selected",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
            Spacer(Modifier.weight(1f))

            MultiSelectIcon(
                icon = Icons.Default.LibraryMusic,
                contentDescription = "Playlist actions",
                onClick = onPlaylistActions
            )
            MultiSelectIcon(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "Add to playlist",
                onClick = onAddToPlaylist
            )
            MultiSelectIcon(
                icon = Icons.Default.Delete,
                contentDescription = "Delete",
                onClick = onDelete
            )
            MultiSelectIcon(
                icon = Icons.Default.MoreVert,
                contentDescription = "More",
                onClick = onMore
            )
        }
    }
}

@Composable
private fun MultiSelectIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
