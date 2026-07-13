package com.mymusicplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    val shouldAnimate = overflowPx > 0f && containerWidthPx > 0f

    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(shouldAnimate, text, overflowPx) {
        if (!shouldAnimate) {
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        // Scroll speed: ~50px/s, with 2s pause at each end
        val scrollTimeMs = (overflowPx / 50f * 1000).toInt().coerceIn(1000, 8000)
        while (true) {
            delay(2000)
            offsetX.animateTo(
                targetValue = -overflowPx,
                animationSpec = tween(
                    durationMillis = scrollTimeMs,
                    easing = LinearEasing
                )
            )
            delay(2000)
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = scrollTimeMs,
                    easing = LinearEasing
                )
            )
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
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false
        )
    }
}
