package com.mymusicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 7.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentDescriptionText: String = "Seek"
) {
    var dragging by remember { mutableStateOf(false) }
    var thumbScale by remember { mutableFloatStateOf(1f) }
    thumbScale = if (dragging) 1.35f else 1f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 24.dp)
            .semantics {
                contentDescription = contentDescriptionText
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    value.coerceIn(0f, 1f), 0f..1f
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    onValueChange(fractionFor(down.position.x))
                    drag(down.id) { change ->
                        onValueChange(fractionFor(change.position.x))
                        change.consume()
                    }
                    dragging = false
                    onValueChangeFinished()
                }
            }
    ) {
        if (size.width <= 0f) return@Canvas
        val fraction = value.coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val barH = trackHeight.toPx()
        val radius = thumbRadius.toPx() * thumbScale
        drawRoundBar(0f, size.width, centerY, barH, inactiveColor)
        val progressX = (fraction * size.width).coerceIn(radius, size.width - radius.coerceAtMost(size.width / 2f))
        if (fraction > 0f) {
            drawRoundBar(radius, (progressX).coerceAtLeast(radius * 2f), centerY, barH, activeColor)
        }
        drawCircle(
            color = activeColor,
            radius = radius,
            center = Offset(progressX, centerY)
        )
    }
}

private fun DrawScope.drawRoundBar(
    startX: Float,
    endX: Float,
    centerY: Float,
    barHeight: Float,
    color: Color
) {
    if (endX <= startX) return
    drawRoundRect(
        color = color,
        topLeft = Offset(startX, centerY - barHeight / 2f),
        size = androidx.compose.ui.geometry.Size(endX - startX, barHeight),
        cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
    )
}

private fun androidx.compose.ui.input.pointer.PointerInputScope.fractionFor(x: Float): Float {
    val w = size.width.toFloat()
    if (w <= 0f) return 0f
    return (x / w).coerceIn(0f, 1f)
}
