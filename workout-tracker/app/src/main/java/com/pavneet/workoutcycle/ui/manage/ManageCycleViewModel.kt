package com.pavneet.workoutcycle.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.AnimationSetting
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.MachineSetup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManageCycleUiState(
    /** Every exercise, skipped ones included, in rotation order. */
    val exercises: List<Exercise> = emptyList(),
    val currentExerciseId: Long? = null,
    val isLoading: Boolean = true,
)

class ManageCycleViewModel(private val repository: WorkoutRepository) : ViewModel() {

    val uiState: StateFlow<ManageCycleUiState> = repository.cycle
        .map { ManageCycleUiState(exercises = it.exercises, currentExerciseId = it.current?.id, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageCycleUiState())

    fun addExercise(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addExercise(trimmed) }
    }

    fun removeExercise(exerciseId: Long) {
        viewModelScope.launch { repository.removeExercise(exerciseId) }
    }

    fun setExerciseActive(exerciseId: Long, active: Boolean) {
        viewModelScope.launch { repository.setExerciseActive(exerciseId, active) }
    }

    fun updateExercise(exerciseId: Long, name: String, setup: MachineSetup) {
        viewModelScope.launch { repository.updateExercise(exerciseId, name, setup) }
    }

    fun setExerciseAnimation(exerciseId: Long, animation: AnimationSetting) {
        viewModelScope.launch { repository.setExerciseAnimation(exerciseId, animation) }
    }

    /** Called once per drop (not per frame of the drag) with the full new order. */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorder(orderedIds) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as WorkoutCycleApp
                ManageCycleViewModel(app.container.workoutRepository)
            }
        }
    }
}
