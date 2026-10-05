package com.pavneet.workoutcycle.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.Attachment
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.MachineSetup
import com.pavneet.workoutcycle.ui.attachmentLabelRes
import kotlinx.coroutines.launch

/** Rename an exercise and save how the cable machine is set up for it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorSheet(
    exercise: Exercise,
    onSave: (name: String, setup: MachineSetup) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(exercise.name) }
    var attachment by rememberSaveable { mutableStateOf(exercise.setup.attachment) }
    var pulleyPosition by rememberSaveable { mutableStateOf(exercise.setup.pulleyPosition) }
    var note by rememberSaveable { mutableStateOf(exercise.setup.note) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.edit_exercise_title, exercise.name),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.exercise_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.attachment_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Attachment.entries.forEach { option ->
                        FilterChip(
                            selected = attachment == option,
                            // Tapping the selected chip again clears it.
                            onClick = { attachment = if (attachment == option) null else option },
                            label = { Text(stringResource(option.attachmentLabelRes)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = pulleyPosition,
                onValueChange = { pulleyPosition = it },
                label = { Text(stringResource(R.string.pulley_position_label)) },
                placeholder = { Text(stringResource(R.string.pulley_position_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.setup_note_label)) },
                placeholder = { Text(stringResource(R.string.setup_note_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { close() }) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(name, MachineSetup(attachment, pulleyPosition, note))
                        close()
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
