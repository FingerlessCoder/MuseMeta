package com.mymusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A proportional (drag + tap) scrollbar for a [LazyListState].
 *
 * Three things this deliberately does *not* do:
 *
 *  - It does not size itself from `listState.layoutInfo`'s viewport. The bar is
 *    hosted in a box that is shorter than the list viewport (it starts under the
 *    sticky header and stops above the mini player), so viewport-derived geometry
 *    made the thumb spill out of that box — reaching down over the mini player
 *    and never lining up with the real scroll extremes. Geometry comes from this
 *    composable's own measured height instead.
 *
 *  - It does not keep a "last good measurement" in state. Writing a smoothed
 *    average during composition invalidates the composable that wrote it, and on
 *    a grid (tall rows) that turned every scroll frame into an extra
 *    recomposition — the bar felt laggy. The average is now a pure function of
 *    the current frame, and the only early return left is on conditions that
 *    cannot change while the composable is alive, so nothing can blink out for a
 *    single frame.
 *
 *  - It does not map the thumb to "the current window". The thumb size and
 *    travel both come from the global content height, so the bar represents the
 *    whole list and reaches both the very top and the very bottom.
 *
 * @param headerItems LazyColumn items above the scrollable content (mini cards +
 * sticky header on Home). They are excluded from the item-size measurement, which
 * is measured from content rows only.
 */
@Composable
fun DragScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    minThumbHeight: Dp = 40.dp,
    enabled: Boolean = true,
    headerItems: Int = 0
) {
    // Read the scroll position explicitly. This read is what subscribes the
    // composable to scroll changes; relying on layoutInfo alone left the thumb
    // frozen while the list moved underneath it.
    @Suppress("UNUSED_EXPRESSION")
    listState.firstVisibleItemIndex

    val info = listState.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo

    // Own height, so the thumb can never escape the box it lives in.
    var trackHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(26.dp)
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        // The single early return: both conditions are stable for the lifetime of
        // this composition, so it can never make the bar flicker for one frame.
        if (!enabled || trackHeightPx <= 0) return@Box

        val trackH = trackHeightPx.toFloat()
        val listViewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()

        val contentItems = if (headerItems > 0) {
            visible.filter { it.index >= headerItems }
        } else {
            visible
        }
        val firstContent = contentItems.firstOrNull()
        val lastContent = contentItems.lastOrNull()

        // Representative item size, measured from the *positions* of the content
        // rows on screen rather than from their sizes: the distance spanned by
        // `lastIndex - firstIndex` rows divided by that count is a true average
        // for that stretch of the list, so a partially clipped row at either edge
        // cannot swing it. Smooth, and stable enough to keep the thumb length
        // fixed while scrolling.
        val avgItemPx = if (firstContent != null && lastContent != null) {
            ScrollbarMath.averageItemSizePx(
                firstItemTopPx = firstContent.offset,
                firstItemIndex = firstContent.index,
                lastItemTopPx = lastContent.offset,
                lastItemIndex = lastContent.index,
                fallbackSizes = contentItems.map { it.size }
            )
        } else {
            0f
        }

        val contentH = ScrollbarMath.contentHeightPx(avgItemPx, total)
        val scrollRangePx = ScrollbarMath.scrollRangePx(contentH, listViewportH)

        val minThumbPx = with(density) { minThumbHeight.toPx() }
        val thumbHpx = ScrollbarMath.thumbHeightPx(trackH, listViewportH, contentH, minThumbPx)
        val thumbTravelPx = (trackH - thumbHpx).coerceAtLeast(0f)

        // Position from what is LEFT to scroll, not from `firstVisibleIndex *
        // avg`: the leading items are tall outliers, so that product jumps
        // backwards as the viewport crosses them. See ScrollbarMath.scrolledPx.
        val fraction = if (scrollRangePx > 0f && lastContent != null) {
            val lastBottomPx = (lastContent.offset + lastContent.size - info.viewportStartOffset)
                .toFloat()
            val scrolledPx = ScrollbarMath.scrolledPx(
                scrollRangePx = scrollRangePx,
                remainingBelowPx = ScrollbarMath.remainingBelowPx(
                    avgItemPx, total, lastContent.index
                ),
                bottomGapPx = ScrollbarMath.bottomGapPx(listViewportH, lastBottomPx)
            )
            ScrollbarMath.fraction(scrolledPx, scrollRangePx)
        } else {
            0f
        }
        val thumbOffsetPx = fraction * thumbTravelPx

        // Keep the drag handler on the latest geometry: it runs outside
        // recomposition, so it must read through remembered holders.
        val totalState = rememberUpdatedState(total)
        val rangeState = rememberUpdatedState(scrollRangePx)
        val avgItemState = rememberUpdatedState(avgItemPx)
        val thumbHState = rememberUpdatedState(thumbHpx)
        val travelState = rememberUpdatedState(thumbTravelPx)
        val viewportHState = rememberUpdatedState(listViewportH.toInt())
        // Pinned height of whatever is stuck to the top, so a jump lands with the
        // content just below the sticky header instead of under it.
        val stickyHeaderPx = rememberUpdatedState(
            visible.firstOrNull { it.index == headerItems - 1 }?.size ?: 0
        )

        fun scrollToFraction(f: Float) {
            val items = totalState.value
            if (items <= 0) return
            val index = ScrollbarMath.targetIndexForFraction(
                fraction = f,
                totalItems = items,
                scrollRangePx = rangeState.value,
                avgItemPx = avgItemState.value
            )
            when {
                index <= 0 ->
                    listState.requestScrollToItem(0, -stickyHeaderPx.value)
                // A large negative offset is deliberate: LazyColumn clamps the
                // scroll to the real scroll range, so this lands exactly on the
                // last item with the list fully scrolled to the bottom. Without
                // it the thumb could only ever reach the tail of the track.
                index >= items - 1 ->
                    listState.requestScrollToItem(items - 1, -viewportHState.value)
                else ->
                    listState.requestScrollToItem(index, -stickyHeaderPx.value)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .pointerInput(Unit) {
                    fun fractionFor(y: Float): Float {
                        val travel = travelState.value
                        if (travel <= 0f) return 0f
                        return ((y - thumbHState.value / 2f) / travel).coerceIn(0f, 1f)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        try {
                            scrollToFraction(fractionFor(down.position.y))
                            drag(down.id) { change ->
                                if (change.positionChanged()) {
                                    scrollToFraction(fractionFor(change.position.y))
                                    change.consume()
                                }
                            }
                        } finally {
                            dragging = false
                        }
                    }
                }
        ) {
            // Global track: spans the whole bar height, represents the entire
            // list length. Always visible, like a desktop scrollbar track.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    )
            )
            // Viewport thumb: size is viewport/content proportion, position is
            // the current scroll window. Brighter so it reads against the track.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = with(density) { thumbOffsetPx.toDp() })
                    .width(6.dp)
                    .height(with(density) { thumbHpx.toDp() })
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    )
            )
        }
    }
}
