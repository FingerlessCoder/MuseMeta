package com.mymusicplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mymusicplayer.domain.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track?,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    if (track == null) return

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            ActionItem(
                icon = Icons.Default.SkipNext,
                title = "Play Next",
                onClick = { onPlayNext(); onDismiss() },
                contentDescription = "Play this track next"
            )
            ActionItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Add to Queue",
                onClick = { onAddToQueue(); onDismiss() },
                contentDescription = "Add track to playback queue"
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            ActionItem(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                title = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                tint = if (isFavorite) MaterialTheme.colorScheme.error else null,
                onClick = { onToggleFavorite(); onDismiss() },
                contentDescription = if (isFavorite) "Remove track from favorites" else "Add track to favorites"
            )
            ActionItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Add to Playlist",
                onClick = { onAddToPlaylist(); onDismiss() },
                contentDescription = "Add track to a playlist"
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            ActionItem(
                icon = Icons.Default.Delete,
                title = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showDeleteConfirm = true },
                contentDescription = "Delete this track"
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete track?") },
            text = { Text("\"${track.title}\" will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    onDismiss()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color? = null,
    contentDescription: String? = null
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription ?: title,
                modifier = Modifier.size(24.dp),
                tint = tint ?: MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title, style = MaterialTheme.typography.bodyLarge,
                color = tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
