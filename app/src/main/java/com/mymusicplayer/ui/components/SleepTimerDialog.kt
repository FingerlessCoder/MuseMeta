package com.mymusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun SleepTimerDialog(
    currentMinutes: Int,
    onSet: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val allOptions = listOf(0) + (10..90 step 5).toList()
    val itemCount = allOptions.size

    val looped = remember {
        buildList { repeat(3) { addAll(allOptions) } }
    }

    val midStart = itemCount + allOptions.indexOf(currentMinutes).coerceAtLeast(0)
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

    val selectedMinutes by remember { derivedStateOf { allOptions[realIndex] } }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val idx = listState.firstVisibleItemIndex
        when {
            idx < itemCount -> listState.scrollToItem(idx + itemCount)
            idx >= itemCount * 2 -> listState.scrollToItem(idx - itemCount)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
        title = { Text("Sleep Timer") },
        text = {
            Box(
                modifier = Modifier
                    .height(220.dp)
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(180.dp)
                        .height(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
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
                        val alpha = (1f - distance * 0.5f).coerceIn(0.25f, 1f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (minutes == 0) "Off" else "${minutes}",
                                fontSize = if (isCenterItem) 20.sp else 16.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isCenterItem) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCenterItem) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(selectedMinutes) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
