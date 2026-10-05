package com.pavneet.workoutcycle.ui.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.AppSettings
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.LoggedSet
import com.pavneet.workoutcycle.domain.MachineSetup
import com.pavneet.workoutcycle.domain.SetEntry
import com.pavneet.workoutcycle.domain.SetKey
import com.pavneet.workoutcycle.domain.StackLoad
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.domain.WorkoutCycle
import com.pavneet.workoutcycle.domain.formatWeight
import com.pavneet.workoutcycle.ui.Haptic
import com.pavneet.workoutcycle.ui.rememberHaptics
import com.pavneet.workoutcycle.ui.unitLabelRes
import com.pavneet.workoutcycle.ui.relativeDay
import com.pavneet.workoutcycle.ui.setSummary
import com.pavneet.workoutcycle.ui.setupSummary
import com.pavneet.workoutcycle.ui.theme.WorkoutCycleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Stateful entry point: wires the ViewModel to the stateless [ActiveWorkoutScreen]. */
@Composable
fun ActiveWorkoutRoute(
    onManageCycle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ActiveWorkoutViewModel = viewModel(factory = ActiveWorkoutViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = rememberHaptics()

    LaunchedEffect(viewModel, snackbarHostState) {
        // collectLatest: a newer completion replaces the visible snackbar instead of queueing behind it.
        viewModel.completedSets.collectLatest { set ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.set_completed, set.exerciseName),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                haptics.perform(Haptic.UNDO)
                viewModel.onUndo()
            }
        }
    }
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }

    NotificationPermissionPrompt(settings, onResult = viewModel::onNotificationPermissionResult)
    KeepScreenOn()

    ActiveWorkoutScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEntryChange = viewModel::onEntryChange,
        onSetCompleted = viewModel::onSetCompleted,
        onMovementSelected = viewModel::onMovementSelected,
        onSkipRest = viewModel::onSkipRest,
        onExtendRest = viewModel::onExtendRest,
        onRestart = viewModel::onRestart,
        onManageCycle = onManageCycle,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    snackbarHostState: SnackbarHostState,
    onEntryChange: (key: SetKey, entry: SetEntry) -> Unit,
    onSetCompleted: (key: SetKey, entry: SetEntry) -> Unit,
    onMovementSelected: (exerciseId: Long, movement: CableMovement) -> Unit,
    onSkipRest: () -> Unit,
    onExtendRest: () -> Unit,
    onRestart: () -> Unit,
    onManageCycle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WorkoutTopBar(
                canRestart = uiState is ActiveWorkoutUiState.Active,
                onRestart = onRestart,
                onManageCycle = onManageCycle,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
            )
        },
        // The controls live in the bottom bar, in thumb reach, so the undo snackbar appears
        // above them instead of covering them.
        bottomBar = {
            if (uiState is ActiveWorkoutUiState.Active) {
                WorkoutControls(
                    state = uiState,
                    onEntryChange = { entry -> onEntryChange(uiState.key, entry) },
                    // Sends the id that is on screen; a second tap before the UI updates is ignored downstream.
                    onDone = { onSetCompleted(uiState.key, uiState.entry) },
                    onSkipRest = onSkipRest,
                    onExtendRest = onExtendRest,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
            is ActiveWorkoutUiState.Active -> ActiveWorkoutContent(
                state = uiState,
                onMovementSelected = onMovementSelected,
                modifier = contentModifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTopBar(
    canRestart: Boolean,
    onRestart: () -> Unit,
    onManageCycle: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = { haptics.perform(Haptic.NAVIGATE); onOpenHistory() }) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.history))
            }
            IconButton(onClick = { haptics.perform(Haptic.NAVIGATE); onManageCycle() }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.manage_cycle_action))
            }
            // Restart wipes the counters, so it sits behind a menu rather than one tap away.
            Box {
                IconButton(onClick = { haptics.perform(Haptic.NAVIGATE); menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            haptics.perform(Haptic.NAVIGATE)
                            menuExpanded = false
                            onOpenSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.restart_cycle)) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        enabled = canRestart,
                        onClick = {
                            haptics.perform(Haptic.DELETE)
                            menuExpanded = false
                            onRestart()
                        },
                    )
                }
            }
        },
    )
}

/** What the exercise pager shows; kept together so a page fading out keeps its own weights. */
private data class PagerContent(val exercise: Exercise, val entries: Map<CableMovement, SetEntry>, val unit: WeightUnit)

@Composable
private fun ActiveWorkoutContent(
    state: ActiveWorkoutUiState.Active,
    onMovementSelected: (exerciseId: Long, movement: CableMovement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val movement = state.current.movement
    val setup = setupSummary(state.current.setup)
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundProgress(state)
        // The demo takes the free space; without one the name sits in the middle instead.
        if (movement != null) {
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = PagerContent(state.current, state.pageEntries, state.unit),
                // A new muscle group fades in; swiping within one doesn't.
                contentKey = { it.exercise.id },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "exercisePager",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { content ->
                val shown = content.exercise.movement
                if (shown != null) {
                    CableExercisePager(
                        choices = content.exercise.movementChoices,
                        selected = shown,
                        loadFor = { StackLoad.of(content.entries[it]?.weight, content.unit) },
                        onSelect = { onMovementSelected(content.exercise.id, it) },
                        setup = setupSummary(content.exercise.setup),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.weight(1f))
            if (setup != null) SetupLine(setup)
        }
        Text(
            text = stringResource(R.string.now).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        CurrentExerciseName(state)
        UpNextLine(state.upNext)
        Spacer(if (movement != null) Modifier.height(8.dp) else Modifier.weight(1f))
    }
}

@Composable
private fun RoundProgress(state: ActiveWorkoutUiState.Active, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.round_number, state.roundsCompleted + 1) + " · " +
                    pluralStringResource(R.plurals.sets_completed, state.setsCompleted, state.setsCompleted),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.position_in_round, state.roundPosition, state.rotation.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
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
    }
}

@Composable
internal fun SetupLine(setup: String) {
    Text(
        text = setup,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // TalkBack announces the new movement after each tap.
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun UpNextLine(upNext: Exercise) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = stringResource(R.string.up_next).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = upNext.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Weight, reps and Done while working; the rest countdown in between sets. */
@Composable
private fun WorkoutControls(
    state: ActiveWorkoutUiState.Active,
    onEntryChange: (SetEntry) -> Unit,
    onDone: () -> Unit,
    onSkipRest: () -> Unit,
    onExtendRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val restEndsAt = state.restEndsAt
    val now = rememberClock(until = restEndsAt)
    val context = LocalContext.current

    // The rest alarm's notification buzzes when allowed; otherwise buzz from here while the screen is up.
    LaunchedEffect(restEndsAt) {
        if (restEndsAt == null) return@LaunchedEffect
        val wait = restEndsAt - System.currentTimeMillis()
        if (wait <= 0) return@LaunchedEffect
        delay(wait)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) vibrate(context)
    }

    Column(modifier) {
        if (restEndsAt != null && now < restEndsAt) {
            RestPanel(
                remainingMillis = restEndsAt - now,
                totalMillis = state.restSeconds * 1_000L,
                nextName = state.current.name,
                onSkip = onSkipRest,
                onExtend = onExtendRest,
            )
        } else {
            SetPanel(
                entry = state.entry,
                unit = state.unit,
                lastSet = state.lastSet,
                onEntryChange = onEntryChange,
                onDone = onDone,
            )
        }
    }
}

private enum class EditField { WEIGHT, REPS }

@Composable
private fun SetPanel(
    entry: SetEntry,
    unit: WeightUnit,
    lastSet: LoggedSet?,
    onEntryChange: (SetEntry) -> Unit,
    onDone: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<EditField?>(null) }
    val weightLabel = stringResource(R.string.weight_label, stringResource(unit.unitLabelRes))

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stepper(
                label = weightLabel,
                value = entry.weight?.let(::formatWeight),
                onDecrease = { onEntryChange(entry.copy(weight = unit.decrease(entry.weight))) },
                onIncrease = { onEntryChange(entry.copy(weight = unit.increase(entry.weight))) },
                onEdit = { editing = EditField.WEIGHT },
                decreaseDescription = stringResource(R.string.decrease_weight),
                increaseDescription = stringResource(R.string.increase_weight),
                decreaseHaptic = Haptic.WEIGHT_DOWN,
                increaseHaptic = Haptic.WEIGHT_UP,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = stringResource(R.string.reps_label),
                value = entry.reps?.toString(),
                onDecrease = { onEntryChange(entry.decreaseReps()) },
                onIncrease = { onEntryChange(entry.increaseReps()) },
                onEdit = { editing = EditField.REPS },
                decreaseDescription = stringResource(R.string.decrease_reps),
                increaseDescription = stringResource(R.string.increase_reps),
                decreaseHaptic = Haptic.REPS_DOWN,
                increaseHaptic = Haptic.REPS_UP,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = lastSetHint(lastSet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        CompleteSetButton(onClick = onDone)
    }

    when (editing) {
        EditField.WEIGHT -> NumberDialog(
            title = weightLabel,
            initial = entry.weight?.let(::formatWeight).orEmpty(),
            decimal = true,
            onConfirm = { text ->
                onEntryChange(entry.copy(weight = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }))
                editing = null
            },
            onDismiss = { editing = null },
        )
        EditField.REPS -> NumberDialog(
            title = stringResource(R.string.reps_label),
            initial = entry.reps?.toString().orEmpty(),
            decimal = false,
            onConfirm = { text ->
                onEntryChange(entry.copy(reps = text.trim().toIntOrNull()?.takeIf { it > 0 }))
                editing = null
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

@Composable
private fun lastSetHint(lastSet: LoggedSet?): String {
    if (lastSet == null) return stringResource(R.string.first_set_hint)
    val day = relativeDay(lastSet.completedAt)
    val summary = setSummary(lastSet)
    return if (summary != null) stringResource(R.string.last_set, summary, day) else stringResource(R.string.last_done, day)
}

/** `null` [value] means not set: a muted "None", with − disabled since there's nothing to lower. */
@Composable
private fun Stepper(
    label: String,
    value: String?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onEdit: () -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    decreaseHaptic: Haptic,
    increaseHaptic: Haptic,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = {
                    haptics.perform(decreaseHaptic)
                    onDecrease()
                },
                enabled = value != null,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = decreaseDescription)
            }
            Text(
                text = value ?: stringResource(R.string.value_not_set),
                color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.edit), onClick = onEdit)
                    .padding(vertical = 10.dp),
            )
            FilledTonalIconButton(
                onClick = {
                    haptics.perform(increaseHaptic)
                    onIncrease()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = increaseDescription)
            }
        }
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: String,
    decimal: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val haptics = rememberHaptics()
    fun confirm() {
        haptics.perform(Haptic.SAVE)
        onConfirm(text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        },
        confirmButton = { TextButton(onClick = { confirm() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun RestPanel(
    remainingMillis: Long,
    totalMillis: Long,
    nextName: String,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
) {
    val seconds = ((remainingMillis + 999) / 1_000).coerceAtLeast(0)
    val haptics = rememberHaptics()
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.rest_title).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { (remainingMillis.toFloat() / totalMillis.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.rest_get_ready, nextName),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    haptics.perform(Haptic.ADD_REST_TIME)
                    onExtend()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Text(stringResource(R.string.add_rest_time), style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = {
                    haptics.perform(Haptic.SKIP_REST)
                    onSkip()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Text(stringResource(R.string.skip_rest), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CompleteSetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Button(
        onClick = {
            haptics.perform(Haptic.DONE)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(36.dp))
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
            Text(stringResource(R.string.manage_cycle_action))
        }
    }
}

/** The current time, ticking a few times a second until [until], then holding still. */
@Composable
private fun rememberClock(until: Long?): Long {
    val now by produceState(System.currentTimeMillis(), until) {
        while (until != null && value < until) {
            delay(250)
            value = System.currentTimeMillis()
        }
        value = System.currentTimeMillis()
    }
    return now
}

/** Asks once for notification permission, which the rest alert and the watch's Done button need. */
@Composable
private fun NotificationPermissionPrompt(settings: AppSettings?, onResult: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    val shouldAsk = settings != null && !settings.notificationPrompted
    LaunchedEffect(shouldAsk) {
        if (!shouldAsk) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onResult() else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
}

private val previewRotation = WorkoutCycle.DEFAULT_EXERCISES.mapIndexed { index, name -> Exercise(index + 1L, name) }

private fun previewState(restEndsAt: Long? = null) = ActiveWorkoutUiState.Active(
    current = previewRotation[2].copy(setup = MachineSetup(pulleyPosition = "Notch 3", note = "Side-on, one step away")),
    upNext = previewRotation[3],
    rotation = previewRotation,
    roundPosition = 3,
    setsCompleted = 7,
    roundsCompleted = 1,
    entry = SetEntry(weight = 15.0, reps = 12),
    unit = WeightUnit.LB,
    lastSet = LoggedSet(1, 3, "Shoulders", 15.0, WeightUnit.LB, 12, System.currentTimeMillis(), CableMovement.LATERAL_RAISE),
    pageEntries = mapOf(CableMovement.LATERAL_RAISE to SetEntry(weight = 15.0, reps = 12)),
    restEndsAt = restEndsAt,
    restSeconds = 60,
)

@Preview(name = "Mid-round", showSystemUi = true)
@Preview(name = "Mid-round, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActiveWorkoutScreenPreview() {
    WorkoutCycleTheme(dynamicColor = false) {
        ActiveWorkoutScreen(
            uiState = previewState(),
            snackbarHostState = remember { SnackbarHostState() },
            onEntryChange = { _, _ -> },
            onSetCompleted = { _, _ -> },
            onMovementSelected = { _, _ -> },
            onSkipRest = {},
            onExtendRest = {},
            onRestart = {},
            onManageCycle = {},
            onOpenHistory = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "Resting", showSystemUi = true)
@Composable
private fun RestingPreview() {
    WorkoutCycleTheme(dynamicColor = false) {
        ActiveWorkoutScreen(
            uiState = previewState(restEndsAt = System.currentTimeMillis() + 45_000),
            snackbarHostState = remember { SnackbarHostState() },
            onEntryChange = { _, _ -> },
            onSetCompleted = { _, _ -> },
            onMovementSelected = { _, _ -> },
            onSkipRest = {},
            onExtendRest = {},
            onRestart = {},
            onManageCycle = {},
            onOpenHistory = {},
            onOpenSettings = {},
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
            onEntryChange = { _, _ -> },
            onSetCompleted = { _, _ -> },
            onMovementSelected = { _, _ -> },
            onSkipRest = {},
            onExtendRest = {},
            onRestart = {},
            onManageCycle = {},
            onOpenHistory = {},
            onOpenSettings = {},
        )
    }
}
