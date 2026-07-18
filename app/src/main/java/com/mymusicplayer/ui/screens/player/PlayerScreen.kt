package com.mymusicplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
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
import com.mymusicplayer.data.lyrics.LyricLine
import com.mymusicplayer.data.preferences.SettingsDataStore
import com.mymusicplayer.domain.model.Track
import com.mymusicplayer.ui.components.EqualizerPanel
import com.mymusicplayer.ui.components.MarqueeText
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    viewModel: PlayerViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val settingsDataStore: SettingsDataStore = koinInject()
    val playerTheme by settingsDataStore.playerTheme.collectAsState(initial = 0)

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onWritePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(state.pendingWriteIntent) {
        state.pendingWriteIntent?.let { intentSender ->
            val request = IntentSenderRequest.Builder(intentSender).build()
            writePermissionLauncher.launch(request)
        }
    }

    LaunchedEffect(state.writeError) {
        state.writeError?.let { error ->
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearWriteError()
        }
    }

    var showEllipsisSheet by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showEditMetadata by remember { mutableStateOf(false) }
    var showEditMetadataConfirm by remember { mutableStateOf(false) }

    if (showEditMetadataConfirm) {
        AlertDialog(
            onDismissRequest = { showEditMetadataConfirm = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved metadata changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(onClick = { 
                    showEditMetadataConfirm = false
                    showEditMetadata = false 
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMetadataConfirm = false }) {
                    Text("Continue Editing")
                }
            }
        )
    }

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
                            syncedLyrics = state.syncedLyrics,
                            currentLyricIndex = state.currentLyricIndex,
                            lyricsText = state.lyricsText,
                            lyricsLoading = state.lyricsLoading,
                            lyricsError = state.lyricsError,
                            artPath = bgArtPath,
                            currentTrack = state.currentTrack,
                            onTap = { showLyricsView = false },
                            onRetry = { viewModel.triggerLyricsFetch() },
                            onSeekTo = { viewModel.seekTo(it) }
                        )
                    }
                    playerTheme == 1 && !showLyricsView -> {
                        PlayerThemeFull(
                            state = state,
                            viewModel = viewModel,
                            context = context,
                            onTapCover = { showLyricsView = true }
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
                onAddToPlaylist = {
                    showEllipsisSheet = false
                    showAddToPlaylistSheet = true
                },
                onShare = {
                    showEllipsisSheet = false
                    shareTrack(context, state.currentTrack)
                },
                onRemoveFromQueue = {
                    showEllipsisSheet = false
                    viewModel.removeCurrentTrackFromQueue()
                },
                onViewAlbum = {
                    showEllipsisSheet = false
                    state.currentTrack?.album?.id?.let { onNavigateToAlbum(it) }
                },
                onViewArtist = {
                    val artists = state.currentTrack?.artists ?: emptyList()
                    showEllipsisSheet = false
                    when {
                        artists.isEmpty() -> { /* nothing to view */ }
                        artists.size == 1 -> onNavigateToArtist(artists.first().id)
                        else -> showArtistPicker = true
                    }
                },
                onEditMetadata = {
                    showEllipsisSheet = false
                    showEditMetadata = true
                },
                onOpenEqualizer = {
                    showEllipsisSheet = false
                    showEqualizerPanel = true
                },
                onDismiss = { showEllipsisSheet = false }
            )
        }
    }

    // ── Equalizer Panel Sheet ──
    if (showEqualizerPanel) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEqualizerPanel = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            EqualizerPanel(
                settingsDataStore = settingsDataStore,
                onDismiss = { showEqualizerPanel = false }
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Artist Picker Sheet (multi-artist) ──
    if (showArtistPicker) {
        val artists = state.currentTrack?.artists ?: emptyList()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showArtistPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            ArtistPickerSheetContent(
                artists = artists,
                onPick = { artistId ->
                    showArtistPicker = false
                    onNavigateToArtist(artistId)
                },
                onDismiss = { showArtistPicker = false }
            )
        }
    }

    // ── Edit Metadata Sheet ──
    val editTrack = state.currentTrack
    if (showEditMetadata && editTrack != null) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { 
                // Blocks internal dismissal (swipe down)
                if (it == SheetValue.Hidden) {
                    showEditMetadataConfirm = true
                    false
                } else true
            }
        )
        
        // Intercept back press while sheet is visible
        BackHandler(enabled = showEditMetadata) {
            showEditMetadataConfirm = true
        }
        
        // Custom dismissal logic to prevent swipe-to-close if there are changes
        // Since we can't easily detect changes here without lifting state, 
        // we use the onDismiss callback provided to the content.
        
        ModalBottomSheet(
            onDismissRequest = { 
                // Handled via onDismiss in Content or confirmation dialog
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = false
            )
        ) {
            // Use a local state to track changes within the sheet's scope
            // This is needed because ModalBottomSheet can still be dismissed via swipe
            // if we don't handle it carefully. 
            // However, since we can't easily block the internal swipe-to-dismiss of 
            // ModalBottomSheet without 'confirmValueChange' in sheetState, 
            // we will add that to the sheetState.
            EditMetadataSheetContent(
                track = editTrack,
                currentArtPath = editTrack.album?.artPath,
                onDismiss = { showEditMetadata = false },
                onShowConfirm = { showEditMetadataConfirm = true },
                onSave = { title, artists, albumTitle, year, trackNumber, genre, applyToAll ->
                    viewModel.editTrackMetadata(
                        trackId = editTrack.id,
                        title = title,
                        artists = artists,
                        albumTitle = albumTitle,
                        year = year,
                        trackNumber = trackNumber,
                        genre = genre,
                        applyToAll = applyToAll
                    )
                    showEditMetadata = false
                },
                onSaveArtwork = { bytes, applyToAll ->
                    viewModel.updateAlbumArt(bytes, applyToAll)
                    // Note: updateAlbumArt handles WriteResult, 
                    // which might require permission dialog.
                    // We don't close the sheet immediately to let user see feedback?
                    // Actually, if we want to follow the pattern, we could close it.
                    showEditMetadata = false
                }
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

    // ── Add to Playlist Sheet ──
    val currentTrackId = state.currentTrack?.id
    if (showAddToPlaylistSheet && currentTrackId != null) {
        com.mymusicplayer.ui.components.PlaylistSelectorSheet(
            trackIds = listOf(currentTrackId),
            onDismiss = { showAddToPlaylistSheet = false },
            onAdded = { showAddToPlaylistSheet = false }
        )
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

    // Local drag state for instant slider thumb response during seek
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Sync animated progress into drag state when NOT dragging, so the thumb
    // starts from the current position when user begins a new drag gesture.
    LaunchedEffect(animatedFraction) {
        if (!isDragging) {
            dragFraction = animatedFraction
        }
    }

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

            // ── Album Cover (takes all available width, swipeable) ──
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            val swipeThreshold = with(LocalDensity.current) { 100.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onTapCover() })
                    }
                    .graphicsLayer { translationX = dragOffsetX }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffsetX < -swipeThreshold) {
                                    viewModel.skipToNext()
                                } else if (dragOffsetX > swipeThreshold) {
                                    viewModel.skipToPrevious()
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = { dragOffsetX = 0f }
                        ) { _, dragAmount ->
                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(
                                -swipeThreshold * 2,
                                swipeThreshold * 2
                            )
                        }
                    },
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
                    MarqueeText(
                        text = state.currentTrack?.title ?: "No track selected",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    if (state.currentTrack != null) {
                        MarqueeText(
                            text = state.currentTrack?.artists?.joinToString(" · ") { it.name } ?: "Unknown Artist",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
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

            if (state.currentTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewText = when {
                        state.lyricsLoading -> "Loading lyrics..."
                        state.syncedLyrics != null -> {
                            val currentLine = state.currentLyricIndex
                                .takeIf { it >= 0 && it < state.syncedLyrics.size }
                                ?.let { state.syncedLyrics[it] }
                            currentLine?.text?.take(80) ?: "Lyrics available"
                        }
                        state.lyricsText != null -> state.lyricsText.take(80)
                        state.lyricsError != null -> state.lyricsError
                        else -> null
                    }
                    if (previewText != null) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    val buttonLabel = when {
                        state.lyricsLoading -> null
                        state.syncedLyrics != null || state.lyricsText != null -> "Lyrics"
                        state.lyricsError != null -> "Retry"
                        else -> "Add"
                    }
                    if (buttonLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                if (state.syncedLyrics != null || state.lyricsText != null) {
                                    onTapCover()
                                } else {
                                    viewModel.triggerLyricsFetch()
                                }
                            }
                        ) {
                            Text(
                                text = buttonLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
            // ── Progress Bar (local drag for instant response, animated when playing) ──
            Slider(
                value = if (isDragging) dragFraction else animatedFraction,
                onValueChange = { fraction ->
                    dragFraction = fraction
                    isDragging = true
                },
                onValueChangeFinished = {
                    isDragging = false
                    viewModel.seekTo((dragFraction * state.duration).toLong())
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
    syncedLyrics: List<LyricLine>?,
    currentLyricIndex: Int,
    lyricsText: String?,
    lyricsLoading: Boolean,
    lyricsError: String?,
    artPath: String?,
    currentTrack: Track?,
    onTap: () -> Unit,
    onRetry: () -> Unit,
    onSeekTo: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (syncedLyrics != null && currentLyricIndex >= 0) {
            listState.scrollToItem((currentLyricIndex - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex >= 0 && syncedLyrics != null) {
            listState.animateScrollToItem((currentLyricIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() }
    ) {
        if (artPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(artPath)).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(60.dp),
                contentScale = ContentScale.Crop
            )
        }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            when {
                lyricsLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Loading lyrics...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                syncedLyrics != null -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(syncedLyrics) { index, line ->
                            val isCurrent = index == currentLyricIndex
                            val alpha = when {
                                isCurrent -> 1f
                                index < currentLyricIndex -> 0.4f
                                else -> 0.6f
                            }
                            Text(
                                text = line.text,
                                style = if (isCurrent) {
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 8.dp)
                                    .clickable { onSeekTo(line.timestampMs) }
                            )
                        }
                    }
                }
                lyricsText != null -> {
                    Text(
                        text = lyricsText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    )
                }
                lyricsError != null -> {
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
                                text = lyricsError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onRetry) {
                                Text("Retry")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap to return to player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Tap to return to player",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
            }
        }

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
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onViewAlbum: () -> Unit,
    onViewArtist: () -> Unit,
    onEditMetadata: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onDismiss: () -> Unit
) {
    val settingsDataStore = koinInject<SettingsDataStore>()
    val scope = rememberCoroutineScope()
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
            onClick = { onAddToPlaylist() }
        )
        if (state.currentTrack?.album != null) {
            SheetMenuItem(
                icon = Icons.Default.Album,
                title = "View Album",
                onClick = { onViewAlbum() }
            )
        }
        if (state.currentTrack?.artists?.isNotEmpty() == true) {
            val artistLabel = if (state.currentTrack?.artists?.size == 1) "View Artist"
            else "View Artist…"
            SheetMenuItem(
                icon = Icons.Default.Person,
                title = artistLabel,
                onClick = { onViewArtist() }
            )
        }
        SheetMenuItem(
            icon = Icons.Default.Edit,
            title = "Edit Metadata",
            onClick = { onEditMetadata() }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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
        SheetMenuItem(
            icon = Icons.Default.GraphicEq,
            title = "Equalizer",
            onClick = { onOpenEqualizer() }
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
//  ARTIST PICKER SHEET (multi-artist)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPickerSheetContent(
    artists: List<com.mymusicplayer.domain.model.Artist>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "View Artist",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(4.dp))
        artists.forEach { artist ->
            SheetMenuItem(
                icon = Icons.Default.Person,
                title = artist.name,
                onClick = { onPick(artist.id) }
            )
        }
    }
}

// ═══════════════════════════════════════
//  EDIT METADATA SHEET
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMetadataSheetContent(
    track: Track,
    currentArtPath: String?,
    onDismiss: () -> Unit,
    onShowConfirm: () -> Unit,
    onSave: (
        title: String?,
        artists: List<String>?,
        albumTitle: String?,
        year: Int?,
        trackNumber: Int?,
        genre: String?,
        applyToAll: Boolean
    ) -> Unit,
    onSaveArtwork: (ByteArray, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artistsText by remember { mutableStateOf(track.artists.joinToString(", ") { it.name }) }
    var albumText by remember { mutableStateOf(track.album?.title ?: "") }
    var yearText by remember { mutableStateOf(track.year?.toString() ?: "") }
    var trackNumberText by remember { mutableStateOf(track.trackNumber?.toString() ?: "") }
    var genreText by remember { mutableStateOf(track.genre ?: "") }
    var selectedArtBytes by remember { mutableStateOf<ByteArray?>(null) }
    var applyToAll by remember { mutableStateOf(false) }

    val hasChanges = remember(
        title, artistsText, albumText, yearText, trackNumberText, genreText, selectedArtBytes
    ) {
        title != track.title ||
        artistsText != track.artists.joinToString(", ") { it.name } ||
        albumText != (track.album?.title ?: "") ||
        yearText != (track.year?.toString() ?: "") ||
        trackNumberText != (track.trackNumber?.toString() ?: "") ||
        genreText != (track.genre ?: "") ||
        selectedArtBytes != null
    }

    val context = LocalContext.current
    val previewArtFile = remember { File(context.cacheDir, "cover_preview_${track.id}.jpg") }
    LaunchedEffect(selectedArtBytes) {
        selectedArtBytes?.let { previewArtFile.writeBytes(it) }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val bytes = context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.readBytes()
                }
                if (bytes != null) selectedArtBytes = bytes
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Edit Metadata",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(12.dp))

        // ── Cover preview + picker ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                selectedArtBytes != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewArtFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                currentArtPath != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(currentArtPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) {
                Text("Change cover")
            }
            if (selectedArtBytes != null) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = { onSaveArtwork(selectedArtBytes!!, applyToAll) }) {
                    Text("Save cover")
                }
            }
        }
        // ── Apply-to-all toggle ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
            Text(
                text = "Apply cover to all tracks in this album",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(12.dp))

        EditField(label = "Title", value = title, onValueChange = { title = it })
        EditField(label = "Artists (comma separated)", value = artistsText, onValueChange = { artistsText = it })
        EditField(label = "Album", value = albumText, onValueChange = { albumText = it })
        EditField(label = "Year", value = yearText, onValueChange = { yearText = it }, singleLine = true)
        EditField(label = "Track Number", value = trackNumberText, onValueChange = { trackNumberText = it }, singleLine = true)
        EditField(label = "Genre", value = genreText, onValueChange = { genreText = it })

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (hasChanges) {
                        onShowConfirm()
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val originalArtists = track.artists.joinToString(", ") { it.name }
                    val newArtists = if (artistsText.trim() != originalArtists) {
                        splitArtists(artistsText).takeIf { it.isNotEmpty() }
                    } else null

                    onSave(
                        title.trim().ifBlank { null }?.takeIf { it != track.title },
                        newArtists,
                        albumText.trim().ifBlank { null }?.takeIf { it != track.album?.title },
                        yearText.trim().toIntOrNull()?.takeIf { it != track.year },
                        trackNumberText.trim().toIntOrNull()?.takeIf { it != track.trackNumber },
                        genreText.trim().ifBlank { null }?.takeIf { it != track.genre },
                        applyToAll
                    )
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

private fun splitArtists(input: String): List<String> {
    val delimiters = listOf(
        " feat. ", " ft. ", " featuring ",
        " & ", " and ",
        " / ", " \\ ", "/",
        ", ", "; ", ";"
    )
    var remaining = input.trim()
    val result = mutableListOf<String>()
    while (remaining.isNotBlank()) {
        var best: Pair<Int, String>? = null
        for (d in delimiters) {
            val idx = remaining.indexOf(d, ignoreCase = true)
            if (idx >= 0 && (best == null || idx < best.first)) best = idx to d
        }
        if (best != null) {
            val name = remaining.substring(0, best.first).trim()
            if (name.isNotBlank()) result.add(name)
            remaining = remaining.substring(best.first + best.second.length).trim()
        } else {
            val name = remaining.trim()
            if (name.isNotBlank()) result.add(name)
            remaining = ""
        }
    }
    return result.distinct().ifEmpty { listOf(input.trim()) }
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

private fun shareTrack(context: android.content.Context, track: Track?) {
    if (track == null) return
    val file = File(track.filePath)
    if (!file.exists()) return
    val authority = "${context.packageName}.fileprovider"
    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share track"))
}
