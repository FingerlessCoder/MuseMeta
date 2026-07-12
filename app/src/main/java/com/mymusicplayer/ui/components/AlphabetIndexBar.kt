package com.mymusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun computeIndexLetters(items: List<String>): List<String> {
    val letters = items.map { item ->
        val c = item.firstOrNull()?.uppercase() ?: "#"
        if (c.length == 1 && c[0] in 'A'..'Z') c else "#"
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
            val c = item.firstOrNull()?.uppercase() ?: "#"
            val normalized = if (c.length == 1 && c[0] in 'A'..'Z') c else "#"
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
    onDragLetterChanged: ((String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (letters.size <= 1) return

    val coroutineScope = rememberCoroutineScope()

    val capsuleShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .width(26.dp)
            .padding(vertical = 4.dp)
            .clip(capsuleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = capsuleShape
            )
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val idx = (offset.y / size.height * letters.size)
                            .toInt().coerceIn(0, letters.size - 1)
                        val letter = letters[idx]
                        val targetIndex = sectionIndices[idx]
                        onDragLetterChanged?.invoke(letter)
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val idx = (change.position.y / size.height * letters.size)
                            .toInt().coerceIn(0, letters.size - 1)
                        val letter = letters[idx]
                        val targetIndex = sectionIndices[idx]
                        onDragLetterChanged?.invoke(letter)
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    onDragEnd = {
                        onDragLetterChanged?.invoke(null)
                    },
                    onDragCancel = {
                        onDragLetterChanged?.invoke(null)
                    }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            val isActive = letter == activeLetter
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
