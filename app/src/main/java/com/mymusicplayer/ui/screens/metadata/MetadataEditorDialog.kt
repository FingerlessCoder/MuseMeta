package com.mymusicplayer.ui.screens.metadata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MetadataEditorDialog(
    viewModel: MetadataEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (!state.isVisible) return

    AlertDialog(
        onDismissRequest = { viewModel.dismiss() },
        title = { Text("Edit Metadata") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.artists,
                    onValueChange = { viewModel.updateArtists(it) },
                    label = { Text("Artists (separate with /)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.albumTitle,
                    onValueChange = { viewModel.updateAlbumTitle(it) },
                    label = { Text("Album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.year,
                    onValueChange = { viewModel.updateYear(it) },
                    label = { Text("Year") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.trackNumber,
                    onValueChange = { viewModel.updateTrackNumber(it) },
                    label = { Text("Track #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.genre,
                    onValueChange = { viewModel.updateGenre(it) },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.comment,
                    onValueChange = { viewModel.updateComment(it) },
                    label = { Text("Comment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismiss() }) {
                Text("Cancel")
            }
        }
    )
}
