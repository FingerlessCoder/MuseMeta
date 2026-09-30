package com.mymusicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymusicplayer.data.preferences.SettingsDataStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val EQ_FREQUENCIES = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
private val EQ_PRESETS = listOf("Flat", "Rock", "Pop", "Bass Boost", "Classical", "Jazz", "Vocal")
private const val EQ_MAX_MB = 1500

/** Track height bounds — the sliders stretch to fill whatever the sheet can spare. */
private val EQ_TRACK_MIN = 200.dp
private val EQ_TRACK_MAX = 320.dp

/** Column width of a single vertical slider (its post-rotation thickness + hit area). */
private val EQ_COLUMN_WIDTH = 52.dp

/** Fixed heights so the 0 dB guide line and the scale labels line up with the track centre. */
private val EQ_LABEL_HEIGHT = 16.dp

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

/** "+4.5" / "-2" / "0" — millibels rendered as decibels with a true minus sign. */
internal fun formatDb(mb: Int): String {
    val db = mb / 100f
    val magnitude = abs(db)
    val body = if (magnitude == floor(magnitude)) {
        magnitude.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", magnitude)
    }
    return when {
        db > 0f -> "+$body"
        db < 0f -> "\u2212$body"
        else -> "0"
    }
}

@Composable
fun EqualizerPanel(
    settingsDataStore: SettingsDataStore,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val enabled by settingsDataStore.equalizerEnabled.collectAsState(initial = false)
    val bandsRaw by settingsDataStore.equalizerBands.collectAsState(initial = "0;0;0;0;0")
    val preset by settingsDataStore.equalizerPreset.collectAsState(initial = "Flat")

    var bands by remember(bandsRaw) { mutableStateOf(parseBands(bandsRaw)) }

    // Persisted only on drag release — a DataStore write per pixel of travel is wasted IO
    // and makes the thumb lag on cheap storage.
    fun persist(next: List<Int>) {
        bands = next
        scope.launch {
            // Dragging a band implies the user wants the EQ on.
            if (!enabled) settingsDataStore.setEqualizerEnabled(true)
            settingsDataStore.setEqualizerBands(next.joinToString(";"))
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val trackHeight = remember(maxHeight) {
            if (maxHeight == Dp.Infinity || maxHeight <= 0.dp) {
                EQ_TRACK_MIN
            } else {
                (maxHeight * 0.34f).coerceIn(EQ_TRACK_MIN, EQ_TRACK_MAX)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = (if (enabled) "On" else "Off") + " \u00b7 " + preset,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { scope.launch { settingsDataStore.setEqualizerEnabled(it) } }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DbScaleLabels(
                    maxDb = EQ_MAX_MB / 100,
                    height = trackHeight
                )
                Box(Modifier.weight(1f)) {
                    // 0 dB guide behind the sliders. The value/frequency labels are the same
                    // height each, so the row centre is exactly the track centre.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        bands.forEachIndexed { index, mb ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.height(EQ_LABEL_HEIGHT),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = formatDb(mb),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                VerticalBandSlider(
                                    value = mb,
                                    height = trackHeight,
                                    onValueChange = { newMb ->
                                        bands = bands.toMutableList().also { it[index] = newMb }
                                    },
                                    onValueChangeFinished = { persist(bands) }
                                )
                                Box(
                                    modifier = Modifier.height(EQ_LABEL_HEIGHT),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = EQ_FREQUENCIES[index],
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EQ_PRESETS.forEach { p ->
                    val selected = p == preset
                    TextButton(onClick = {
                        persist(presetBands(p))
                        scope.launch { settingsDataStore.setEqualizerPreset(p) }
                    }) {
                        Text(
                            p,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** "+15 / 0 / -15" scale aligned to the slider tracks. */
@Composable
private fun DbScaleLabels(maxDb: Int, height: Dp) {
    Column(
        modifier = Modifier
            .width(30.dp)
            .padding(top = EQ_LABEL_HEIGHT, bottom = EQ_LABEL_HEIGHT)
            .height(height),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "+$maxDb",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            "0",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            "\u2212$maxDb",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * A vertical slider built by rotating Material3's [Slider].
 *
 * The long axis has to be the slider's *layout* width, so it is set with [requiredWidth] —
 * plain `width` would be coerced down to the narrow container and leave a ~28dp track.
 * Rotation is a draw transform, so the 52dp x [height] box ends up exactly covering the
 * rotated 48dp x [height] slider.
 */
@Composable
private fun VerticalBandSlider(
    value: Int,
    height: Dp,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Box(
        modifier = Modifier.width(EQ_COLUMN_WIDTH).height(height),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(-EQ_MAX_MB, EQ_MAX_MB)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = -EQ_MAX_MB.toFloat()..EQ_MAX_MB.toFloat(),
            modifier = Modifier.requiredWidth(height).rotate(-90f)
        )
    }
}
