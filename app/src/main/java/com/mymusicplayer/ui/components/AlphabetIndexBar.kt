package com.mymusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch

fun computeIndexLetters(items: List<String>): List<String> {
    val letters = items.map { item ->
        val c = item.firstOrNull()?.uppercaseChar() ?: '#'
        if (c in 'A'..'Z') c.toString() else "#"
    }
    return letters.distinct().sorted().let { sorted ->
        val hash = sorted.filter { it == "#" }
        val alpha = sorted.filter { it != "#" }
        alpha + hash
    }
}

fun computeSectionIndices(items: List<String>, letters: List<String>): List<Int> {
    return letters.map { letter ->
        items.indexOfFirst { item ->
            val c = item.firstOrNull()?.uppercaseChar() ?: '#'
            val normalized = if (c in 'A'..'Z') c.toString() else "#"
            normalized == letter
        }.coerceAtLeast(0)
    }
}

@Composable
fun AlphabetIndexBar(
    letters: List<String>,
    sectionIndices: List<Int>,
    listState: LazyListState,
    activeLetter: String? = null,
    highlightedLetter: String? = null,
    onDragLetterChanged: ((String?) -> Unit)? = null,
    bubbleOffsetX: Dp = (-52).dp,
    modifier: Modifier = Modifier
) {
    if (letters.size <= 1) return

    val coroutineScope = rememberCoroutineScope()

    val capsuleShape = RoundedCornerShape(12.dp)

    val showBubble = activeLetter != null

    Box(modifier = modifier.width(26.dp)) {
        if (showBubble) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = bubbleOffsetX)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeLetter,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .clip(capsuleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = if (activeLetter != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = capsuleShape
                )
                .padding(vertical = 8.dp, horizontal = 2.dp)
                .pointerInput(letters, sectionIndices) {
                    fun stickyOffset() = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == 1 }?.size ?: 0

                    detectTapGestures { offset ->
                        val idx = (offset.y / size.height * letters.size)
                            .toInt().coerceIn(0, letters.size - 1)
                        val targetIndex = sectionIndices[idx]
                        onDragLetterChanged?.invoke(letters[idx])
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex, scrollOffset = -stickyOffset())
                            withFrameNanos { }
                            onDragLetterChanged?.invoke(null)
                        }
                    }
                }
                .pointerInput(letters, sectionIndices) {
                    fun stickyOffset() = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == 1 }?.size ?: 0

                    var lastLetter: String? = null
                    detectDragGestures(
                        onDragStart = { offset ->
                            lastLetter = null
                            val idx = (offset.y / size.height * letters.size)
                                .toInt().coerceIn(0, letters.size - 1)
                            lastLetter = letters[idx]
                            onDragLetterChanged?.invoke(lastLetter)
                            coroutineScope.launch {
                                listState.scrollToItem(sectionIndices[idx], scrollOffset = -stickyOffset())
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val idx = (change.position.y / size.height * letters.size)
                                .toInt().coerceIn(0, letters.size - 1)
                            val letter = letters[idx]
                            if (letter != lastLetter) {
                                lastLetter = letter
                                onDragLetterChanged?.invoke(letter)
                            }
                            coroutineScope.launch {
                                listState.scrollToItem(sectionIndices[idx], scrollOffset = -stickyOffset())
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                withFrameNanos { }
                                lastLetter = null
                                onDragLetterChanged?.invoke(null)
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                withFrameNanos { }
                                lastLetter = null
                                onDragLetterChanged?.invoke(null)
                            }
                        }
                    )
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val highlightLetter = highlightedLetter ?: activeLetter
            letters.forEach { letter ->
                val isActive = letter == highlightLetter
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
