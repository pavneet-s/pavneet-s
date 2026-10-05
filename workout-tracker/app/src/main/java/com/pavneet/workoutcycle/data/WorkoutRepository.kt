package com.pavneet.workoutcycle.data

import androidx.room.withTransaction
import com.pavneet.workoutcycle.data.local.CycleWithExercises
import com.pavneet.workoutcycle.data.local.ExerciseEntity
import com.pavneet.workoutcycle.data.local.WorkoutDatabase
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.WorkoutCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for the rotation. Each write loads the current snapshot, applies a
 * pure [WorkoutCycle] operation and saves the result inside one transaction, so the cycle
 * rules live in the domain model and rapid taps can't interleave.
 */
class WorkoutRepository(
    private val database: WorkoutDatabase,
    private val cycleId: Long = WorkoutDatabase.DEFAULT_CYCLE_ID,
) {
    private val dao = database.workoutDao()

    val cycle: Flow<WorkoutCycle> = dao.observeCycle(cycleId)
        .filterNotNull()
        .map { it.toDomain() }

    /**
     * Marks [exerciseId] done and advances to the next movement.
     *
     * @return the snapshot from before the completion (for undo), or `null` if [exerciseId]
     * was no longer current, e.g. the second tap of a double tap.
     */
    suspend fun completeSet(exerciseId: Long): WorkoutCycle? = database.withTransaction {
        val before = load()
        val after = before.completeSet(exerciseId)
        if (after === before) {
            null
        } else {
            save(before, after)
            before
        }
    }

    suspend fun undo(snapshot: WorkoutCycle) = update { it.restoreProgress(snapshot) }

    suspend fun restart() = update { it.restart() }

    suspend fun addExercise(name: String) {
        database.withTransaction {
            val before = load()
            val id = dao.insertExercise(
                ExerciseEntity(cycleId = cycleId, name = name, position = before.exercises.size, isActive = true),
            )
            save(before, before.add(Exercise(id = id, name = name)))
        }
    }

    suspend fun removeExercise(exerciseId: Long) = update { it.remove(exerciseId) }

    suspend fun setExerciseActive(exerciseId: Long, active: Boolean) =
        update { it.setActive(exerciseId, active) }

    suspend fun reorder(orderedIds: List<Long>) = update { it.reorder(orderedIds) }

    private suspend fun update(transform: (WorkoutCycle) -> WorkoutCycle) {
        database.withTransaction {
            val before = load()
            val after = transform(before)
            // Skipping no-op writes avoids a redundant emission, e.g. dropping a row where it started.
            if (after != before) save(before, after)
        }
    }

    private suspend fun load(): WorkoutCycle =
        checkNotNull(dao.getCycle(cycleId)) { "Cycle $cycleId is missing" }.toDomain()

    private suspend fun save(before: WorkoutCycle, after: WorkoutCycle) {
        if (after.exercises != before.exercises) {
            val ids = after.exercises.map { it.id }
            dao.deleteExercisesNotIn(cycleId, ids)
            dao.upsertExercises(
                after.exercises.mapIndexed { position, exercise ->
                    ExerciseEntity(
                        id = exercise.id,
                        cycleId = cycleId,
                        name = exercise.name,
                        position = position,
                        isActive = exercise.isActive,
                    )
                },
            )
        }
        dao.updateProgress(cycleId, after.currentExerciseId, after.setsCompleted, after.roundsCompleted)
    }

    private fun CycleWithExercises.toDomain() = WorkoutCycle(
        exercises = exercises
            .sortedBy { it.position }
            .map { Exercise(id = it.id, name = it.name, isActive = it.isActive) },
        currentExerciseId = cycle.currentExerciseId,
        setsCompleted = cycle.setsCompleted,
        roundsCompleted = cycle.roundsCompleted,
    )
}
