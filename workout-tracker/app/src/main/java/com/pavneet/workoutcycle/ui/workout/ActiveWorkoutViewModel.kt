package com.pavneet.workoutcycle.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.data.CompletedSet
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.AppSettings
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.LoggedSet
import com.pavneet.workoutcycle.domain.SetEntry
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
        /** What Done will log for [current]: the last set's values unless edited. */
        val entry: SetEntry,
        val unit: WeightUnit,
        /** The most recent set of [current], for the "Last:" hint. */
        val lastSet: LoggedSet?,
        /** End of the current rest (epoch ms). The screen compares it with the clock. */
        val restEndsAt: Long?,
        val restSeconds: Int,
    ) : ActiveWorkoutUiState
}

/** One-off event for the "Push-ups done · Undo" snackbar. */
data class CompletedSetEvent(val exerciseName: String)

class ActiveWorkoutViewModel(
    private val repository: WorkoutRepository,
    private val controller: WorkoutController,
) : ViewModel() {

    /** Weight and reps edited on screen but not logged yet, per exercise. */
    private val drafts = MutableStateFlow<Map<Long, SetEntry>>(emptyMap())

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        repository.cycle,
        repository.settings,
        repository.lastSets,
        drafts,
    ) { cycle, settings, lastSets, drafts ->
        val exercise = cycle.current ?: return@combine ActiveWorkoutUiState.Empty
        val lastSet = lastSets[exercise.id]
        // Only carry the weight over if it was logged in the unit in use now.
        val prefill = SetEntry(weight = lastSet?.weight?.takeIf { lastSet?.unit == settings.weightUnit }, reps = lastSet?.reps)
        ActiveWorkoutUiState.Active(
            current = exercise,
            upNext = checkNotNull(cycle.upNext),
            rotation = cycle.activeExercises,
            roundPosition = cycle.roundPosition,
            setsCompleted = cycle.setsCompleted,
            roundsCompleted = cycle.roundsCompleted,
            entry = drafts[exercise.id] ?: prefill,
            unit = settings.weightUnit,
            lastSet = lastSet,
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

    fun onEntryChange(exerciseId: Long, entry: SetEntry) {
        drafts.update { it + (exerciseId to entry) }
    }

    /** [exerciseId] is the exercise the user saw on screen, which makes repeat taps harmless. */
    fun onSetCompleted(exerciseId: Long, entry: SetEntry) {
        viewModelScope.launch {
            val completed = controller.completeSet(exerciseId, entry) ?: return@launch
            drafts.update { it - exerciseId }
            undoable = completed
            completions.send(CompletedSetEvent(completed.exerciseName))
        }
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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WorkoutCycleApp).container
                ActiveWorkoutViewModel(container.workoutRepository, container.workoutController)
            }
        }
    }
}
