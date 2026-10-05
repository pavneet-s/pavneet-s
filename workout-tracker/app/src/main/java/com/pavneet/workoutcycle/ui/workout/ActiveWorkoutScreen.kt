package com.pavneet.workoutcycle.ui.workout

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.WorkoutCycle
import com.pavneet.workoutcycle.ui.cable.CableMachineAnimation
import com.pavneet.workoutcycle.ui.cable.labelRes
import com.pavneet.workoutcycle.ui.theme.WorkoutCycleTheme
import kotlinx.coroutines.flow.collectLatest

/** Stateful entry point: wires the ViewModel to the stateless [ActiveWorkoutScreen]. */
@Composable
fun ActiveWorkoutRoute(
    onManageCycle: () -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(factory = ActiveWorkoutViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel, snackbarHostState) {
        // collectLatest: a newer completion replaces the visible snackbar instead of queueing behind it.
        viewModel.completedSets.collectLatest { set ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.set_completed, set.exerciseName),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
        }
    }

    KeepScreenOn()

    ActiveWorkoutScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSetCompleted = viewModel::onSetCompleted,
        onRestart = viewModel::onRestart,
        onManageCycle = onManageCycle,
    )
}

@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    snackbarHostState: SnackbarHostState,
    onSetCompleted: (exerciseId: Long) -> Unit,
    onRestart: () -> Unit,
    onManageCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WorkoutTopBar(
                canRestart = uiState is ActiveWorkoutUiState.Active,
                onRestart = onRestart,
                onManageCycle = onManageCycle,
            )
        },
        // The button lives in the bottom bar, in thumb reach, so the undo snackbar appears
        // above it instead of covering it.
        bottomBar = {
            if (uiState is ActiveWorkoutUiState.Active) {
                CompleteSetButton(
                    exercise = uiState.current,
                    onClick = onSetCompleted,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (uiState) {
            ActiveWorkoutUiState.Loading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ActiveWorkoutUiState.Empty -> EmptyCycle(onManageCycle = onManageCycle, modifier = contentModifier)
            is ActiveWorkoutUiState.Active -> ActiveWorkoutContent(state = uiState, modifier = contentModifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTopBar(canRestart: Boolean, onRestart: () -> Unit, onManageCycle: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onManageCycle) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.manage_cycle))
            }
            // Restart wipes the counters, so it sits behind a menu rather than one tap away.
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.restart_cycle)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        enabled = canRestart,
                        onClick = {
                            menuExpanded = false
                            onRestart()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ActiveWorkoutContent(state: ActiveWorkoutUiState.Active, modifier: Modifier = Modifier) {
    val movement = state.current.movement
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundProgress(state)
        // The demo takes the free space; without one the name sits in the middle instead.
        if (movement != null) {
            Spacer(Modifier.height(16.dp))
            CableDemo(
                movement = movement,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = stringResource(R.string.now).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        CurrentExerciseName(state)
        Spacer(if (movement != null) Modifier.height(16.dp) else Modifier.weight(1f))
        UpNextCard(state.upNext)
    }
}

/** The looping cable-machine demo plus which movement it is and where to set the pulley. */
@Composable
private fun CableDemo(movement: CableMovement, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(
            targetState = movement,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "cableDemo",
        ) { shown ->
            CableMachineAnimation(
                movement = shown,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.cable_caption,
                stringResource(movement.labelRes),
                stringResource(movement.pulley.labelRes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoundProgress(state: ActiveWorkoutUiState.Active, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.round_number, state.roundsCompleted + 1),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.position_in_round, state.roundPosition, state.rotation.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        // One segment per exercise: done this round, current, still to come.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.rotation.forEachIndexed { index, exercise ->
                key(exercise.id) {
                    val color by animateColorAsState(
                        targetValue = when {
                            index < state.roundPosition - 1 -> MaterialTheme.colorScheme.primary
                            index == state.roundPosition - 1 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        label = "segmentColor",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = pluralStringResource(R.plurals.sets_completed, state.setsCompleted, state.setsCompleted),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentExerciseName(state: ActiveWorkoutUiState.Active, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        // Keyed on the set count too, so a one-exercise rotation still animates on each set.
        contentKey = { it.current.id to it.setsCompleted },
        transitionSpec = {
            // Slide up when advancing, down when undoing or restarting.
            val direction = if (targetState.setsCompleted >= initialState.setsCompleted) 1 else -1
            (slideInVertically { height -> direction * height / 2 } + fadeIn()) togetherWith
                (slideOutVertically { height -> -direction * height / 2 } + fadeOut())
        },
        label = "currentExercise",
        modifier = modifier,
    ) { target ->
        Text(
            text = target.current.name,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            // TalkBack announces the new movement after each tap.
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun UpNextCard(upNext: Exercise, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.up_next).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = upNext.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompleteSetButton(exercise: Exercise, onClick: (exerciseId: Long) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Button(
        // Sends the id that is on screen; a second tap before the UI updates is ignored downstream.
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick(exercise.id)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.mark_done),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyCycle(onManageCycle: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.empty_cycle_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_cycle_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onManageCycle) {
            Text(stringResource(R.string.manage_cycle))
        }
    }
}

/** Keeps the display awake while this screen is shown; the phone is usually on the floor mid-set. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private val previewRotation = WorkoutCycle.DEFAULT_EXERCISES.mapIndexed { index, name -> Exercise(index + 1L, name) }

@Preview(name = "Mid-round", showSystemUi = true)
@Preview(name = "Mid-round, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActiveWorkoutScreenPreview() {
    WorkoutCycleTheme(dynamicColor = false) {
        ActiveWorkoutScreen(
            uiState = ActiveWorkoutUiState.Active(
                current = previewRotation[2],
                upNext = previewRotation[3],
                rotation = previewRotation,
                roundPosition = 3,
                setsCompleted = 7,
                roundsCompleted = 1,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSetCompleted = {},
            onRestart = {},
            onManageCycle = {},
        )
    }
}

@Preview(name = "Empty", showSystemUi = true)
@Composable
private fun EmptyCyclePreview() {
    WorkoutCycleTheme(dynamicColor = false) {
        ActiveWorkoutScreen(
            uiState = ActiveWorkoutUiState.Empty,
            snackbarHostState = remember { SnackbarHostState() },
            onSetCompleted = {},
            onRestart = {},
            onManageCycle = {},
        )
    }
}
