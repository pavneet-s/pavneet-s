package com.pavneet.workoutcycle.session

import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.SetKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the workout notification in step with the workout, whichever way it changed: the app,
 * the notification's own buttons, a watch or the rest alarm.
 */
class WorkoutNotificationSync(
    private val repository: WorkoutRepository,
    private val controller: WorkoutController,
    private val notifier: WorkoutNotifier,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            combine(
                repository.cycle,
                repository.settings,
                repository.lastSets,
                controller.activeUntil,
            ) { cycle, settings, lastSets, activeUntil ->
                val now = controller.now()
                val current = cycle.current
                if (!settings.workoutNotification || activeUntil <= now || current == null) {
                    null
                } else {
                    WorkoutNotificationState(
                        exerciseId = current.id,
                        exerciseName = current.name,
                        movement = current.movement,
                        nextName = cycle.upNext?.takeIf { it.id != current.id }?.name,
                        lastSet = lastSets[SetKey(current.id, current.movement)],
                        restEndsAt = cycle.restEndsAt?.takeIf { cycle.isResting(now) },
                        hideAt = activeUntil,
                    )
                }
            }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == null) notifier.hideWorkout() else notifier.showWorkout(state, controller.now())
                }
        }
    }
}
