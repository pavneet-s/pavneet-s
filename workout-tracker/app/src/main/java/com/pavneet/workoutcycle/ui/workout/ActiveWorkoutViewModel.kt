package com.pavneet.workoutcycle.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.WorkoutCycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    ) : ActiveWorkoutUiState
}

/** One-off event for the "Push-ups done · Undo" snackbar. */
data class CompletedSet(val exerciseName: String)

class ActiveWorkoutViewModel(private val repository: WorkoutRepository) : ViewModel() {

    val uiState: StateFlow<ActiveWorkoutUiState> = repository.cycle
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState.Loading)

    private val completions = Channel<CompletedSet>(Channel.CONFLATED)
    val completedSets: Flow<CompletedSet> = completions.receiveAsFlow()

    private var undoSnapshot: WorkoutCycle? = null

    /** [exerciseId] is the exercise the user saw on screen, which makes repeat taps harmless. */
    fun onSetCompleted(exerciseId: Long) {
        viewModelScope.launch {
            val before = repository.completeSet(exerciseId) ?: return@launch
            val finished = before.current ?: return@launch
            undoSnapshot = before
            completions.send(CompletedSet(finished.name))
        }
    }

    fun onUndo() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        viewModelScope.launch { repository.undo(snapshot) }
    }

    fun onRestart() {
        undoSnapshot = null
        viewModelScope.launch { repository.restart() }
    }

    private fun WorkoutCycle.toUiState(): ActiveWorkoutUiState {
        val exercise = current ?: return ActiveWorkoutUiState.Empty
        return ActiveWorkoutUiState.Active(
            current = exercise,
            upNext = checkNotNull(upNext),
            rotation = activeExercises,
            roundPosition = roundPosition,
            setsCompleted = setsCompleted,
            roundsCompleted = roundsCompleted,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as WorkoutCycleApp
                ActiveWorkoutViewModel(app.container.workoutRepository)
            }
        }
    }
}
