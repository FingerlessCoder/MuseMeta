package com.mymusicplayer.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    listState: LazyListState
) {
    if (letters.size <= 1) return

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val idx = (offset.y / size.height * letters.size)
                        .toInt().coerceIn(0, letters.size - 1)
                    val targetIndex = sectionIndices[idx]
                    coroutineScope.launch {
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}
