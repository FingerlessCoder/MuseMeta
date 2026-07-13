package com.mymusicplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import com.mymusicplayer.data.audio.PlaybackMode
import com.mymusicplayer.domain.model.Track
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showEllipsisSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }

    val bgArtPath = state.currentTrack?.album?.artPath

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Blurred background ──
        if (bgArtPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(bgArtPath)).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(50.dp),
                contentScale = ContentScale.Crop
            )
        }

        // ── Gradient overlay ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // ── Foreground: top bar above content (no overlap) ──
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar row — thinner than Scaffold TopAppBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { showEllipsisSheet = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Main content fills remaining space ──
            Box(modifier = Modifier.weight(1f)) {
                when {
                    showLyricsView -> {
                        LyricsFullView(
                            lyricsText = state.lyricsText,
                            artPath = bgArtPath,
                            currentTrack = state.currentTrack,
                            onTap = { showLyricsView = false }
                        )
                    }
                    else -> {
                        PlayerContent(
                            state = state,
                            viewModel = viewModel,
                            context = context,
                            onTapCover = { showLyricsView = true },
                            onOpenPlaylist = { showPlaylistSheet = true }
                        )
                    }
                }
            }
        }
    }

    if (showEllipsisSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEllipsisSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            EllipsisSheetContent(
                state = state,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onOpenSleepTimer = { showEllipsisSheet = false; showSleepTimerSheet = true },
                onShare = {
                    showEllipsisSheet = false
                    shareTrack(context, state.currentTrack)
                },
                onRemoveFromQueue = {
                    showEllipsisSheet = false
                    viewModel.removeCurrentTrackFromQueue()
                },
                onDismiss = { showEllipsisSheet = false }
            )
        }
    }

    // ── Sleep Timer Sheet ──
    if (showSleepTimerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            SleepTimerSheetContent(
                currentMinutes = state.sleepTimerMinutes,
                remainingSeconds = state.sleepTimerRemainingSeconds,
                onStart = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    showSleepTimerSheet = false
                },
                onCancel = {
                    viewModel.cancelSleepTimer()
                    showSleepTimerSheet = false
                }
            )
        }
    }

    // ── Playlist / Queue Sheet ──
    if (showPlaylistSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlaylistSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            PlaylistSheetContent(
                queueTracks = state.queueTracks,
                queueIndex = state.queueIndex,
                playbackMode = state.playbackMode,
                onClose = { showPlaylistSheet = false },
                onClearQueue = { viewModel.clearQueue() },
                onRemoveTrack = { index -> viewModel.removeTrackFromQueue(index) },
                onCycleMode = { viewModel.cyclePlaybackMode() }
            )
        }
    }
}

// ═══════════════════════════════════════
//  MAIN CONTENT (cover view)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    context: android.content.Context,
    onTapCover: () -> Unit,
    onOpenPlaylist: () -> Unit
) {
    // ── Smooth progress animation to prevent flicker ──
    val targetFraction = if (state.duration > 0) state.currentPosition.toFloat() / state.duration else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 300),
        label = "sliderProgress"
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ═══════════════════════════════════════
        //  TOP: Cover + Info (no scroll, fills space)
        // ═══════════════════════════════════════
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Album Cover (takes all available width) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onTapCover() },
                contentAlignment = Alignment.Center
            ) {
                if (state.currentTrack?.album?.artPath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(state.currentTrack.album.artPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = state.currentTrack?.album?.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Track Info Row: Title+Artist | Favorite ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.currentTrack?.title ?: "No track selected",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    if (state.currentTrack != null) {
                        Text(
                            text = state.currentTrack.artists.joinToString(" · ") { it.name },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (state.isFavorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Lyrics Preview Row + Add Capsule ──
            if (state.lyricsText != null || state.currentTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.lyricsText?.take(80) ?: "No lyrics available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { /* TODO: add lyrics */ }
                    ) {
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ═══════════════════════════════════════
        //  BOTTOM: Fixed controls (never clipped)
        // ═══════════════════════════════════════
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Progress Bar (animated to prevent flicker) ──
            Slider(
                value = animatedFraction,
                onValueChange = { fraction ->
                    viewModel.seekTo((fraction * state.duration).toLong())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(state.currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDuration(state.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Playback Controls ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback Mode (shuffle / list / single)
                IconButton(onClick = { viewModel.cyclePlaybackMode() }) {
                    val modeIcon = when (state.playbackMode) {
                        PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                        PlaybackMode.LIST -> Icons.Default.Repeat
                        PlaybackMode.SINGLE -> Icons.Default.RepeatOne
                    }
                    val modeLabel = when (state.playbackMode) {
                        PlaybackMode.SHUFFLE -> "Shuffle"
                        PlaybackMode.LIST -> "Repeat list"
                        PlaybackMode.SINGLE -> "Repeat one"
                    }
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = modeLabel,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Previous
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Play/Pause (large circle)
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Playlist
                IconButton(onClick = onOpenPlaylist) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Playlist",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ═══════════════════════════════════════
//  LYRICS FULL VIEW
// ═══════════════════════════════════════

@Composable
private fun LyricsFullView(
    lyricsText: String?,
    artPath: String?,
    currentTrack: com.mymusicplayer.domain.model.Track?,
    onTap: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() }
    ) {
        // Background: blurred album art
        if (artPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(artPath)).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(60.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Gradient overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Lyrics content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Track info header
            currentTrack?.let { track ->
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artists.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    modifier = Modifier.width(80.dp)
                )
                Spacer(Modifier.height(24.dp))
            }

            // Lyrics text
            if (lyricsText != null) {
                Text(
                    text = lyricsText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No lyrics available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Tap to return to player",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Hint at bottom
        Text(
            text = "Tap anywhere to return",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

// ═══════════════════════════════════════
//  ELLIPSIS BOTTOM SHEET
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EllipsisSheetContent(
    state: PlayerUiState,
    onToggleFavorite: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onShare: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Sheet handle spacer
        Spacer(Modifier.height(8.dp))

        // Current cover preview
        val context = LocalContext.current
        if (state.currentTrack?.album?.artPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(state.currentTrack.album.artPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // Track info header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentTrack?.title ?: "No track",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.currentTrack?.artists?.joinToString(", ") { it.name } ?: "Unknown artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(4.dp))

        // Menu items
        SheetMenuItem(
            icon = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            title = if (state.isFavorite) "Remove from Favorites" else "Favorite",
            tint = if (state.isFavorite) MaterialTheme.colorScheme.error else null,
            onClick = { onToggleFavorite(); onDismiss() }
        )
        SheetMenuItem(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            title = "Add to Playlist",
            onClick = { /* TODO: show playlist selector */ onDismiss() }
        )
        SheetMenuItem(
            icon = Icons.Default.Schedule,
            title = "Sleep Timer",
            onClick = { onOpenSleepTimer() }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        SheetMenuItem(
            icon = Icons.Default.Share,
            title = "Share",
            onClick = { onShare() }
        )
        SheetMenuItem(
            icon = Icons.Default.RemoveCircleOutline,
            title = "Remove from Current Playlist",
            onClick = { onRemoveFromQueue() },
            enabled = state.queueSize > 0
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color? = null,
    enabled: Boolean = true
) {
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        color = Color.Transparent
    ) {
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
                tint = tint ?: if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// ═══════════════════════════════════════
//  SLEEP TIMER SHEET (Apple-style Picker)
// ═══════════════════════════════════════

@Composable
private fun SleepTimerSheetContent(
    currentMinutes: Int,
    remainingSeconds: Int,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val isActive = remainingSeconds > 0
    val options = (10..90 step 5).toList()
    val itemCount = options.size

    val looped = remember { buildList { repeat(3) { addAll(options) } } }
    val midStart = itemCount + options.indexOfFirst { it >= currentMinutes }.coerceAtLeast(0)
    val listState = rememberLazyListState(midStart, 0)
    val snapBehavior = rememberSnapFlingBehavior(listState)

    val viewportCenter by remember {
        derivedStateOf { listState.layoutInfo.viewportEndOffset / 2 }
    }

    val realIndex by remember {
        derivedStateOf {
            val items = listState.layoutInfo.visibleItemsInfo
            if (items.isEmpty()) return@derivedStateOf 0
            items.minByOrNull {
                abs(it.offset + it.size / 2 - viewportCenter)
            }?.let { it.index % itemCount } ?: 0
        }
    }

    val selectedMinutes by remember { derivedStateOf { options[realIndex] } }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val idx = listState.firstVisibleItemIndex
        when {
            idx < itemCount -> listState.scrollToItem(idx + itemCount)
            idx >= itemCount * 2 -> listState.scrollToItem(idx - itemCount)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Sleep Timer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isActive) "Timer is running"
                else "Music will pause after the selected time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        if (isActive) {
            // ── Countdown display ──
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            Text(
                text = "%d:%02d".format(mins, secs),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancel Timer", modifier = Modifier.padding(vertical = 6.dp))
            }
        } else {
            // ── Picker wheel ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )

                LazyColumn(
                    state = listState,
                    flingBehavior = snapBehavior,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(looped, key = { i, _ -> "item_$i" }) { virtualIdx, minutes ->
                        val isCenterItem = realIndex == virtualIdx % itemCount
                        val distance by remember(virtualIdx, viewportCenter) {
                            derivedStateOf {
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .find { it.index == virtualIdx }
                                if (info == null) return@derivedStateOf 1f
                                val center = info.offset + info.size / 2
                                abs(center - viewportCenter).toFloat() / info.size.toFloat()
                            }
                        }
                        val scale = (1f - distance * 0.35f).coerceIn(0.5f, 1f)
                        val alpha = (1f - distance * 0.5f).coerceIn(0.2f, 1f)

                        Text(
                            text = "$minutes",
                            fontSize = if (isCenterItem) 28.sp else 20.sp,
                            fontWeight = if (isCenterItem) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCenterItem) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(52.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .wrapContentSize(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "${selectedMinutes} min",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", modifier = Modifier.padding(vertical = 6.dp))
                }
                Button(
                    onClick = { onStart(selectedMinutes) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Start", modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════
//  PLAYLIST / QUEUE BOTTOM SHEET
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheetContent(
    queueTracks: List<Track>,
    queueIndex: Int,
    playbackMode: PlaybackMode,
    onClose: () -> Unit,
    onClearQueue: () -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onCycleMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Playmode controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Playback Mode
            val modeIcon = when (playbackMode) {
                PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                PlaybackMode.LIST -> Icons.Default.Repeat
                PlaybackMode.SINGLE -> Icons.Default.RepeatOne
            }
            val modeLabel = when (playbackMode) {
                PlaybackMode.SHUFFLE -> "Shuffle"
                PlaybackMode.LIST -> "Repeat List"
                PlaybackMode.SINGLE -> "Repeat One"
            }
            AssistChip(
                onClick = onCycleMode,
                label = { Text(modeLabel) },
                leadingIcon = {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )

            // Clear
            AssistChip(
                onClick = onClearQueue,
                label = { Text("Clear") },
                leadingIcon = {
                    Icon(
                        Icons.Default.ClearAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )

        // Queue list
        if (queueTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(queueTracks) { index, track ->
                    val isCurrent = index == queueIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Track number or playing indicator
                        if (isCurrent) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.width(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = track.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveTrack(index) }) {
                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Add tracks button at bottom
        Surface(
            onClick = { /* TODO: open multi-select track picker */ },
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add Tracks to Queue",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ═══════════════════════════════════════
//  UTILITIES
// ═══════════════════════════════════════

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun shareTrack(context: android.content.Context, track: com.mymusicplayer.domain.model.Track?) {
    if (track == null) return
    val text = "Listening to ${track.title} by ${track.artists.joinToString(", ") { it.name }} on MuseMeta"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share track"))
}
