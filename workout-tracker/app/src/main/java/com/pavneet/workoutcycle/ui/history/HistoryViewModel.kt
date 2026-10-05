package com.pavneet.workoutcycle.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.WorkoutHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Loaded(val history: WorkoutHistory) : HistoryUiState
}

class HistoryViewModel(repository: WorkoutRepository) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.history
        .map { sets -> HistoryUiState.Loaded(WorkoutHistory(sets, ZoneId.systemDefault(), LocalDate.now())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as WorkoutCycleApp
                HistoryViewModel(app.container.workoutRepository)
            }
        }
    }
}
