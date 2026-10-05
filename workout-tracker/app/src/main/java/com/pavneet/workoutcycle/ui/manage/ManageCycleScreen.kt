package com.pavneet.workoutcycle.ui.manage

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.AnimationSetting
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.WorkoutCycle
import com.pavneet.workoutcycle.ui.cable.labelRes
import com.pavneet.workoutcycle.ui.theme.WorkoutCycleTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ManageCycleRoute(
    onBack: () -> Unit,
    viewModel: ManageCycleViewModel = viewModel(factory = ManageCycleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ManageCycleScreen(
        uiState = uiState,
        onBack = onBack,
        onAddExercise = viewModel::addExercise,
        onRemoveExercise = viewModel::removeExercise,
        onExerciseActiveChange = viewModel::setExerciseActive,
        onReorder = viewModel::reorder,
        onExerciseAnimationChange = viewModel::setExerciseAnimation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCycleScreen(
    uiState: ManageCycleUiState,
    onBack: () -> Unit,
    onAddExercise: (name: String) -> Unit,
    onRemoveExercise: (exerciseId: Long) -> Unit,
    onExerciseActiveChange: (exerciseId: Long, active: Boolean) -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    onExerciseAnimationChange: (exerciseId: Long, animation: AnimationSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    var animationPickerFor by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_cycle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        // Only the top inset is applied here; the list scrolls behind the gesture bar instead.
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AddExerciseField(
                onAdd = onAddExercise,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.manage_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                ReorderableExerciseList(
                    exercises = uiState.exercises,
                    currentExerciseId = uiState.currentExerciseId,
                    onRemove = onRemoveExercise,
                    onActiveChange = onExerciseActiveChange,
                    onReorder = onReorder,
                    onAnimationClick = { exerciseId -> animationPickerFor = exerciseId },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                )
            }
        }
    }

    uiState.exercises.firstOrNull { it.id == animationPickerFor }?.let { exercise ->
        AnimationPickerSheet(
            exerciseName = exercise.name,
            selected = exercise.movement,
            onSelect = { animation -> onExerciseAnimationChange(exercise.id, animation) },
            onDismiss = { animationPickerFor = null },
        )
    }
}

@Composable
private fun AddExerciseField(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }

    // The keyboard stays open after adding, so several exercises can be entered in a row.
    fun submit() {
        if (name.isBlank()) return
        onAdd(name)
        name = ""
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.new_exercise)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        FilledIconButton(
            onClick = { submit() },
            enabled = name.isNotBlank(),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_exercise))
        }
    }
}

@Composable
private fun ReorderableExerciseList(
    exercises: List<Exercise>,
    currentExerciseId: Long?,
    onRemove: (exerciseId: Long) -> Unit,
    onActiveChange: (exerciseId: Long, active: Boolean) -> Unit,
    onReorder: (orderedIds: List<Long>) -> Unit,
    onAnimationClick: (exerciseId: Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // Local copy so rows follow the finger with no database round trip; the new order is
    // saved once, on drop. It is one long-lived state object (not remember(exercises)) because
    // the library keeps the drag callbacks from when the gesture began, and those must still
    // read the latest list.
    var items by remember { mutableStateOf(exercises) }
    val currentOnReorder by rememberUpdatedState(onReorder)
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThresholdPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    // Adopt each list the database emits, but never pull rows out from under an active drag.
    LaunchedEffect(exercises) {
        if (!reorderState.isAnyItemDragging) items = exercises
    }

    // Drag-free reordering for TalkBack and Switch Access users.
    fun move(from: Int, to: Int): Boolean {
        if (to !in items.indices) return false
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        currentOnReorder(items.map { it.id })
        return true
    }

    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, exercise -> exercise.id }) { index, exercise ->
            ReorderableItem(reorderState, key = exercise.id) { isDragging ->
                ExerciseRow(
                    exercise = exercise,
                    isCurrent = exercise.id == currentExerciseId,
                    isDragging = isDragging,
                    dragHandleModifier = Modifier.draggableHandle(
                        onDragStarted = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        },
                        onDragStopped = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            currentOnReorder(items.map { it.id })
                        },
                    ),
                    onActiveChange = { active -> onActiveChange(exercise.id, active) },
                    onRemove = { onRemove(exercise.id) },
                    onAnimationClick = { onAnimationClick(exercise.id) },
                    // Merged so TalkBack focuses the row as one item and offers the move actions.
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        customActions = listOf(
                            CustomAccessibilityAction(moveUpLabel) { move(index, index - 1) },
                            CustomAccessibilityAction(moveDownLabel) { move(index, index + 1) },
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    isCurrent: Boolean,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onActiveChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onAnimationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decorative for screen readers, which reorder through the row's custom actions.
            Box(dragHandleModifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_drag_handle), contentDescription = null)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 2.dp)
                    .alpha(if (exercise.isActive) 1f else 0.5f),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    isCurrent -> Text(
                        text = stringResource(R.string.status_current),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    !exercise.isActive -> Text(
                        text = stringResource(R.string.status_skipped),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                val animationLabel = exercise.movement?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.animation_off)
                val chipDescription = stringResource(R.string.animation_chip_description, exercise.name, animationLabel)
                AssistChip(
                    onClick = onAnimationClick,
                    label = { Text(animationLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = chipDescription },
                )
            }
            val includeDescription = stringResource(R.string.include_in_rotation, exercise.name)
            Switch(
                checked = exercise.isActive,
                onCheckedChange = onActiveChange,
                modifier = Modifier.semantics { contentDescription = includeDescription },
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_exercise, exercise.name))
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ManageCycleScreenPreview() {
    val exercises = WorkoutCycle.DEFAULT_EXERCISES.mapIndexed { index, name ->
        Exercise(id = index + 1L, name = name, isActive = name != "Triceps")
    }
    WorkoutCycleTheme(dynamicColor = false) {
        ManageCycleScreen(
            uiState = ManageCycleUiState(exercises = exercises, currentExerciseId = 2, isLoading = false),
            onBack = {},
            onAddExercise = {},
            onRemoveExercise = {},
            onExerciseActiveChange = { _, _ -> },
            onReorder = {},
            onExerciseAnimationChange = { _, _ -> },
        )
    }
}
