package com.mymusicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * A horizontally-scrolling text composable for long titles.
 * Automatically animates left/right when text overflows its container.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified
) {
    var textWidthPx by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val overflowPx = textWidthPx - containerWidthPx
    val shouldAnimate = overflowPx > 0 && containerWidthPx > 0

    // Animate from 0 → -overflow → 0 → repeat
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(
            durationMillis = 250,
            easing = LinearEasing
        ),
        label = "marqueeOffset"
    )

    LaunchedEffect(shouldAnimate, text, overflowPx) {
        if (!shouldAnimate) {
            offsetX = 0f
            return@LaunchedEffect
        }
        // Scroll speed: ~50px/s, with 2s pause at each end
        val scrollTime = (overflowPx / 50f * 1000).toInt().coerceIn(1000, 8000)
        while (true) {
            delay(2000)
            offsetX = -overflowPx
            delay(scrollTime.toLong() + 250) // +250 for animation spec
            delay(2000)
            offsetX = 0f
            delay(scrollTime.toLong() + 250)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            modifier = Modifier
                .onSizeChanged { textWidthPx = it.width.toFloat() }
                .graphicsLayer { translationX = animatedOffsetX },
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false
        )
    }
}
