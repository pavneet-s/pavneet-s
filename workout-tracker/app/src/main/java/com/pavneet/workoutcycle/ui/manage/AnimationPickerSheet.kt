package com.pavneet.workoutcycle.ui.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.AnimationSetting
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.MuscleGroup
import com.pavneet.workoutcycle.ui.Haptic
import com.pavneet.workoutcycle.ui.cable.CableMachineAnimation
import com.pavneet.workoutcycle.ui.cable.CableScene
import com.pavneet.workoutcycle.ui.cable.labelRes
import com.pavneet.workoutcycle.ui.rememberHaptics
import kotlinx.coroutines.launch

private const val PREVIEW_ASPECT_RATIO = CableScene.VIEW_WIDTH / CableScene.VIEW_HEIGHT

/**
 * Bottom sheet of live previews for choosing the cable exercise a muscle group shows, grouped by
 * muscle with [group]'s own exercises first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationPickerSheet(
    exerciseName: String,
    group: MuscleGroup?,
    selected: CableMovement?,
    onSelect: (AnimationSetting) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val groups = remember(group) { MuscleGroup.entries.sortedBy { if (it == group) 0 else 1 } }

    fun choose(setting: AnimationSetting) {
        haptics.perform(Haptic.SELECT)
        onSelect(setting)
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.animation_picker_title, exerciseName),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (muscleGroup in groups) {
                item(key = "header-$muscleGroup", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(muscleGroup.labelRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { heading() },
                    )
                }
                items(muscleGroup.movements, key = { it.name }) { movement ->
                    PickerTile(
                        title = stringResource(movement.labelRes),
                        subtitle = stringResource(movement.pulley.labelRes),
                        isSelected = movement == selected,
                        onClick = { choose(AnimationSetting.Fixed(movement)) },
                    ) { containerColor ->
                        CableMachineAnimation(
                            movement = movement,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(PREVIEW_ASPECT_RATIO),
                            containerColor = containerColor,
                        )
                    }
                }
            }
            item(key = "off") {
                PickerTile(
                    title = stringResource(R.string.animation_off),
                    subtitle = null,
                    isSelected = selected == null,
                    onClick = { choose(AnimationSetting.Off) },
                ) { containerColor ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(PREVIEW_ASPECT_RATIO)
                            .background(containerColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTile(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    preview: @Composable (containerColor: Color) -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.semantics { selected = isSelected },
    ) {
        Column {
            preview(containerColor)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = if (subtitle == null) 12.dp else 0.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}
