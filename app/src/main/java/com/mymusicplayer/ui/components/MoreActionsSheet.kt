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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreActionsSheet(
    trackCount: Int,
    hasFavorites: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$trackCount track${if (trackCount != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            MoreActionItem(
                icon = if (hasFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                title = if (hasFavorites) "Remove from Favorites" else "Add to Favorites",
                tint = if (hasFavorites) MaterialTheme.colorScheme.error else null,
                onClick = { onToggleFavorite(); onDismiss() }
            )
            MoreActionItem(
                icon = Icons.Default.Share,
                title = "Share",
                onClick = { onShare(); onDismiss() }
            )
        }
    }
}

@Composable
private fun MoreActionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint ?: MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
