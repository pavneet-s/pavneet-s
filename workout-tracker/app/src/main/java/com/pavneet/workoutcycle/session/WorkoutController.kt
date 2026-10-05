package com.pavneet.workoutcycle.session

import com.pavneet.workoutcycle.data.CompletedSet
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.SetEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything that happens when you finish a set or rest, from any entry point: the app's Done
 * button, the notification (and so a paired watch) or the rest alarm. Keeping it in one place
 * means the set log, the rest alarm and the notification always agree.
 */
class WorkoutController(
    private val repository: WorkoutRepository,
    private val alarms: RestAlarmScheduler,
    private val notifier: WorkoutNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _activeUntil = MutableStateFlow(0L)

    /** The workout notification shows until this time (epoch ms); every action extends it. */
    val activeUntil: StateFlow<Long> = _activeUntil.asStateFlow()

    fun now(): Long = clock()

    /** Called while the workout screen is open, and by every action below. */
    fun markActive() {
        _activeUntil.value = clock() + SESSION_TIMEOUT_MS
    }

    /** See [WorkoutRepository.completeSet]; a null [entry] repeats the last weight and reps. */
    suspend fun completeSet(exerciseId: Long, entry: SetEntry?): CompletedSet? {
        markActive()
        val completed = repository.completeSet(exerciseId, entry, clock()) ?: return null
        notifier.cancelRestOver()
        val restEndsAt = completed.restEndsAt
        if (restEndsAt != null) alarms.schedule(restEndsAt) else alarms.cancel()
        return completed
    }

    suspend fun undo(completed: CompletedSet) {
        markActive()
        repository.undoSet(completed)
        alarms.cancel()
    }

    suspend fun skipRest() {
        markActive()
        repository.endRest()
        alarms.cancel()
    }

    suspend fun extendRest(seconds: Int = EXTEND_REST_SECONDS) {
        markActive()
        repository.extendRest(seconds, clock())?.let(alarms::schedule)
    }

    suspend fun restart() {
        repository.restart()
        alarms.cancel()
        notifier.cancelRestOver()
    }

    /** The rest alarm went off: end the rest and alert, unless it was skipped meanwhile. */
    suspend fun onRestAlarm() {
        if (!repository.finishRestIfDue(clock())) return
        val next = repository.currentCycle().current ?: return
        notifier.showRestOver(next.name)
    }

    companion object {
        const val EXTEND_REST_SECONDS = 15
        const val SESSION_TIMEOUT_MS = 60 * 60 * 1_000L
    }
}
