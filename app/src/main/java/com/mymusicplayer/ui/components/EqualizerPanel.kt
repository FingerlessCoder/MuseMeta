package com.mymusicplayer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymusicplayer.data.preferences.SettingsDataStore
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val EQ_FREQUENCIES = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
private val EQ_PRESETS = listOf("Flat", "Rock", "Pop", "Bass Boost", "Classical", "Jazz", "Vocal")
private const val EQ_MAX_MB = 1500

private fun presetBands(preset: String): List<Int> = when (preset) {
    "Rock" -> listOf(400, 200, -200, 300, 500)
    "Pop" -> listOf(300, 100, 0, 100, 300)
    "Bass Boost" -> listOf(700, 500, 100, 0, 0)
    "Classical" -> listOf(300, 200, 0, 200, 300)
    "Jazz" -> listOf(300, 200, -100, 200, 350)
    "Vocal" -> listOf(0, 300, 600, 400, 0)
    else -> listOf(0, 0, 0, 0, 0)
}

private fun parseBands(raw: String): List<Int> {
    val list = raw.split(";").mapNotNull { it.toIntOrNull() }
    return if (list.size == 5) list else listOf(0, 0, 0, 0, 0)
}

@Composable
fun EqualizerPanel(
    settingsDataStore: SettingsDataStore,
    onDismiss: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val enabled by settingsDataStore.equalizerEnabled.collectAsState(initial = false)
    val bandsRaw by settingsDataStore.equalizerBands.collectAsState(initial = "0;0;0;0;0")
    val preset by settingsDataStore.equalizerPreset.collectAsState(initial = "Flat")

    var bands by remember(bandsRaw) { mutableStateOf(parseBands(bandsRaw)) }

    fun saveBands(next: List<Int>) {
        bands = next
        scope.launch {
            // Dragging a band implies the user wants the EQ on.
            if (!enabled) settingsDataStore.setEqualizerEnabled(true)
            settingsDataStore.setEqualizerBands(next.joinToString(";"))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Equalizer", style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = {
                scope.launch { settingsDataStore.setEqualizerEnabled(it) }
            })
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            bands.forEachIndexed { index, mb ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text("${mb / 100}", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                    VerticalBandSlider(
                        value = mb,
                        onValueChange = { newMb -> saveBands(bands.toMutableList().also { it[index] = newMb }) }
                    )
                    Text(
                        EQ_FREQUENCIES[index],
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EQ_PRESETS.forEach { p ->
                TextButton(onClick = {
                    saveBands(presetBands(p))
                    scope.launch { settingsDataStore.setEqualizerPreset(p) }
                }) {
                    Text(p, color = if (p == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun VerticalBandSlider(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val density = LocalDensity.current
    val heightPx = with(density) { 140.dp.toPx() }
    Box(
        modifier = Modifier.width(48.dp).height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(-EQ_MAX_MB, EQ_MAX_MB)) },
            valueRange = -EQ_MAX_MB.toFloat()..EQ_MAX_MB.toFloat(),
            modifier = Modifier.width(with(density) { heightPx.toDp() }).rotate(-90f)
        )
    }
}
