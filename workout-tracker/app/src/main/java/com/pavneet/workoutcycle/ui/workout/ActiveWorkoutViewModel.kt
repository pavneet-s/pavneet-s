package com.pavneet.workoutcycle.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.data.CompletedSet
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.AnimationSetting
import com.pavneet.workoutcycle.domain.AppSettings
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.LoggedSet
import com.pavneet.workoutcycle.domain.SetEntry
import com.pavneet.workoutcycle.domain.SetKey
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.session.WorkoutController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ActiveWorkoutUiState {
    data object Loading : ActiveWorkoutUiState

    /** Every exercise was removed or skipped. */
    data object Empty : ActiveWorkoutUiState

    data class Active(
        val current: Exercise,
        val upNext: Exercise,
        /** Active exercises in rotation order; drives the progress segments. */
        val rotation: List<Exercise>,
        /** 1-based position of [current] in [rotation]. */
        val roundPosition: Int,
        val setsCompleted: Int,
        val roundsCompleted: Int,
        /** What Done will log for [current] as its chosen cable exercise: the last set's values unless edited. */
        val entry: SetEntry,
        val unit: WeightUnit,
        /** The most recent set of [current] as its chosen cable exercise, for the "Last:" hint. */
        val lastSet: LoggedSet?,
        /** What each of [current]'s cable exercises would log, so every swipe page shows its own weight. */
        val pageEntries: Map<CableMovement, SetEntry>,
        /** End of the current rest (epoch ms). The screen compares it with the clock. */
        val restEndsAt: Long?,
        val restSeconds: Int,
    ) : ActiveWorkoutUiState {
        /** Identifies what Done logs: [current] as its chosen cable exercise. */
        val key: SetKey get() = SetKey(current.id, current.movement)
    }
}

/** One-off event for the "Chest done · Undo" snackbar. */
data class CompletedSetEvent(val exerciseName: String)

class ActiveWorkoutViewModel(
    private val repository: WorkoutRepository,
    private val controller: WorkoutController,
) : ViewModel() {

    /** Weight and reps edited on screen but not logged yet, per exercise and cable exercise. */
    private val drafts = MutableStateFlow<Map<SetKey, SetEntry>>(emptyMap())

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        repository.cycle,
        repository.settings,
        repository.lastSets,
        drafts,
    ) { cycle, settings, lastSets, drafts ->
        val exercise = cycle.current ?: return@combine ActiveWorkoutUiState.Empty
        fun entryFor(key: SetKey): SetEntry = drafts[key] ?: prefill(lastSets[key], settings.weightUnit)
        val key = SetKey(exercise.id, exercise.movement)
        ActiveWorkoutUiState.Active(
            current = exercise,
            upNext = checkNotNull(cycle.upNext),
            rotation = cycle.activeExercises,
            roundPosition = cycle.roundPosition,
            setsCompleted = cycle.setsCompleted,
            roundsCompleted = cycle.roundsCompleted,
            entry = entryFor(key),
            unit = settings.weightUnit,
            lastSet = lastSets[key],
            pageEntries = exercise.movementChoices.associateWith { entryFor(SetKey(exercise.id, it)) },
            restEndsAt = cycle.restEndsAt,
            restSeconds = settings.restSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState.Loading)

    /** `null` until loaded, so the screen doesn't act on defaults. */
    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val completions = Channel<CompletedSetEvent>(Channel.CONFLATED)
    val completedSets: Flow<CompletedSetEvent> = completions.receiveAsFlow()

    private var undoable: CompletedSet? = null

    /** Keeps the workout notification (and the watch's Done button) up while training. */
    fun onScreenShown() = controller.markActive()

    fun onEntryChange(key: SetKey, entry: SetEntry) {
        drafts.update { it + (key to entry) }
    }

    /** [key] is what the user saw on screen, which makes repeat taps harmless. */
    fun onSetCompleted(key: SetKey, entry: SetEntry) {
        viewModelScope.launch {
            val completed = controller.completeSet(key.exerciseId, entry) ?: return@launch
            drafts.update { it - key }
            undoable = completed
            completions.send(CompletedSetEvent(completed.exerciseName))
        }
    }

    /** Swiped to another of the muscle group's cable exercises: remember it for next time too. */
    fun onMovementSelected(exerciseId: Long, movement: CableMovement) {
        viewModelScope.launch { repository.setExerciseAnimation(exerciseId, AnimationSetting.Fixed(movement)) }
    }

    fun onUndo() {
        val completed = undoable ?: return
        undoable = null
        viewModelScope.launch { controller.undo(completed) }
    }

    fun onSkipRest() {
        viewModelScope.launch { controller.skipRest() }
    }

    fun onExtendRest() {
        viewModelScope.launch { controller.extendRest() }
    }

    fun onRestart() {
        undoable = null
        viewModelScope.launch { controller.restart() }
    }

    /** Remember that we asked, and refresh the notification in case it was just allowed. */
    fun onNotificationPermissionResult() {
        controller.markActive()
        viewModelScope.launch { repository.updateSettings { it.copy(notificationPrompted = true) } }
    }

    // Only carry the weight over if it was logged in the unit in use now.
    private fun prefill(last: LoggedSet?, unit: WeightUnit) =
        SetEntry(weight = last?.weight?.takeIf { last?.unit == unit }, reps = last?.reps)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WorkoutCycleApp).container
                ActiveWorkoutViewModel(container.workoutRepository, container.workoutController)
            }
        }
    }
}
