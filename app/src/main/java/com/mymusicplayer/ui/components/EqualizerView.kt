package com.mymusicplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class EqualizerBand(
    val frequency: String,
    val gain: Float
)

@Composable
fun EqualizerView(
    bands: List<EqualizerBand>,
    selectedPreset: String,
    isEnabled: Boolean,
    presets: List<String>,
    onPresetSelected: (String) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresets by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Equalizer",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { showPresets = true }) {
                    Text(selectedPreset, color = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(
                    expanded = showPresets,
                    onDismissRequest = { showPresets = false }
                ) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                onPresetSelected(preset)
                                showPresets = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = { onEnabledChanged(!isEnabled) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(if (isEnabled) "ON" else "OFF", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bands.forEachIndexed { index, band ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = band.frequency,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Full graphic EQ with slider controls will be implemented using Android AudioFX API.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "AudioFX availability is device-dependent. EQ will be hidden on unsupported devices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
