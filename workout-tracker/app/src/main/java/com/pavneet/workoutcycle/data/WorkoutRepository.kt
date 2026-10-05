package com.pavneet.workoutcycle.data

import androidx.room.withTransaction
import com.pavneet.workoutcycle.data.local.CycleWithExercises
import com.pavneet.workoutcycle.data.local.ExerciseEntity
import com.pavneet.workoutcycle.data.local.SetLogEntity
import com.pavneet.workoutcycle.data.local.SettingsEntity
import com.pavneet.workoutcycle.data.local.WorkoutDatabase
import com.pavneet.workoutcycle.domain.AnimationSetting
import com.pavneet.workoutcycle.domain.AppSettings
import com.pavneet.workoutcycle.domain.Attachment
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.Exercise
import com.pavneet.workoutcycle.domain.LoggedSet
import com.pavneet.workoutcycle.domain.MachineSetup
import com.pavneet.workoutcycle.domain.SetEntry
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.domain.WorkoutCycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** A completed set, kept by the caller so it can be undone. */
data class CompletedSet(
    /** The cycle before the set, which undo restores. */
    val before: WorkoutCycle,
    val logId: Long,
    val exerciseName: String,
    val restEndsAt: Long?,
)

/**
 * Single source of truth for the rotation, the set log and settings. Each write loads the
 * current snapshot, applies a pure [WorkoutCycle] operation and saves the result inside one
 * transaction, so the rules live in the domain model and rapid taps can't interleave.
 */
class WorkoutRepository(
    private val database: WorkoutDatabase,
    private val cycleId: Long = WorkoutDatabase.DEFAULT_CYCLE_ID,
) {
    private val dao = database.workoutDao()

    val cycle: Flow<WorkoutCycle> = dao.observeCycle(cycleId)
        .filterNotNull()
        .map { it.toDomain() }

    val settings: Flow<AppSettings> = dao.observeSettings().map { it?.toDomain() ?: AppSettings() }

    /** Every logged set, oldest first. */
    val history: Flow<List<LoggedSet>> = dao.observeLogs().map { logs -> logs.map { it.toDomain() } }

    /** The most recent set of each exercise, by exercise id. */
    val lastSets: Flow<Map<Long, LoggedSet>> = dao.observeLatestLogs().map { logs ->
        logs.mapNotNull { log -> log.exerciseId?.let { id -> id to log.toDomain() } }.toMap()
    }

    suspend fun currentCycle(): WorkoutCycle = load()

    suspend fun currentSettings(): AppSettings = dao.getSettings()?.toDomain() ?: AppSettings()

    /**
     * Logs a set of [exerciseId], advances to the next movement and starts a rest if enabled.
     * A null [entry] repeats the exercise's last weight and reps, which is what the watch's
     * Done button uses.
     *
     * @return what to undo, or `null` if [exerciseId] was no longer current, e.g. the second
     * tap of a double tap.
     */
    suspend fun completeSet(exerciseId: Long, entry: SetEntry?, now: Long): CompletedSet? =
        database.withTransaction {
            val before = load()
            val exercise = before.current ?: return@withTransaction null
            val advanced = before.completeSet(exerciseId)
            if (advanced === before) return@withTransaction null

            val settings = currentSettings()
            val values = entry ?: lastEntry(exercise.id, settings.weightUnit)
            val logId = dao.insertLog(
                SetLogEntity(
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    weight = values.weight,
                    weightUnit = values.weight?.let { settings.weightUnit.name },
                    reps = values.reps,
                    completedAt = now,
                ),
            )
            val after = if (settings.restEnabled) {
                advanced.startRest(endsAt = now + settings.restSeconds * 1_000L)
            } else {
                advanced.endRest()
            }
            save(before, after)
            CompletedSet(before, logId, exercise.name, after.restEndsAt)
        }

    /** The values to prefill for an exercise: its last reps, and its last weight if it was in [unit]. */
    suspend fun lastEntry(exerciseId: Long, unit: WeightUnit): SetEntry {
        val last = dao.lastLogFor(exerciseId) ?: return SetEntry()
        return SetEntry(weight = last.weight.takeIf { last.weightUnit == unit.name }, reps = last.reps)
    }

    /** Takes back a completed set: the pointer, counters and rest go back, and the log entry goes. */
    suspend fun undoSet(completed: CompletedSet) {
        database.withTransaction {
            update { it.restoreProgress(completed.before) }
            dao.deleteLog(completed.logId)
        }
    }

    suspend fun restart() = update { it.restart() }

    suspend fun endRest() = update { it.endRest() }

    /** Adds time to a running rest and returns its new end, or `null` if no rest is running. */
    suspend fun extendRest(seconds: Int, now: Long): Long? = database.withTransaction {
        val before = load()
        val after = before.extendRest(seconds * 1_000L, now)
        if (after != before) save(before, after)
        after.restEndsAt?.takeIf { after.isResting(now) }
    }

    /**
     * Ends the rest if its time is up (allowing for an alarm firing a moment early).
     * Returns whether a rest was ended.
     */
    suspend fun finishRestIfDue(now: Long): Boolean = database.withTransaction {
        val before = load()
        val endsAt = before.restEndsAt
        if (endsAt == null || endsAt > now + ALARM_TOLERANCE_MS) return@withTransaction false
        save(before, before.endRest())
        true
    }

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

    suspend fun setExerciseAnimation(exerciseId: Long, animation: AnimationSetting) =
        update { it.setAnimation(exerciseId, animation) }

    suspend fun renameExercise(exerciseId: Long, name: String) = update { it.rename(exerciseId, name) }

    suspend fun setExerciseSetup(exerciseId: Long, setup: MachineSetup) =
        update { it.setSetup(exerciseId, setup) }

    /** Saves the exercise editor in one go: the new name and the machine setup. */
    suspend fun updateExercise(exerciseId: Long, name: String, setup: MachineSetup) =
        update { it.rename(exerciseId, name).setSetup(exerciseId, setup) }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        database.withTransaction {
            val current = currentSettings()
            val updated = transform(current)
            if (updated != current) dao.upsertSettings(updated.toEntity())
        }
    }

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
                        animation = exercise.animation.encode(),
                        attachment = exercise.setup.attachment?.name,
                        pulleyPosition = exercise.setup.pulleyPosition.trim().ifEmpty { null },
                        setupNote = exercise.setup.note.trim().ifEmpty { null },
                    )
                },
            )
        }
        dao.updateProgress(
            cycleId,
            after.currentExerciseId,
            after.setsCompleted,
            after.roundsCompleted,
            after.restEndsAt,
        )
    }

    private fun CycleWithExercises.toDomain() = WorkoutCycle(
        exercises = exercises
            .sortedBy { it.position }
            .map {
                Exercise(
                    id = it.id,
                    name = it.name,
                    isActive = it.isActive,
                    animation = decodeAnimation(it.animation),
                    setup = MachineSetup(
                        attachment = Attachment.entries.firstOrNull { attachment -> attachment.name == it.attachment },
                        pulleyPosition = it.pulleyPosition.orEmpty(),
                        note = it.setupNote.orEmpty(),
                    ),
                )
            },
        currentExerciseId = cycle.currentExerciseId,
        setsCompleted = cycle.setsCompleted,
        roundsCompleted = cycle.roundsCompleted,
        restEndsAt = cycle.restEndsAt,
    )

    private fun SetLogEntity.toDomain() = LoggedSet(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        weight = weight,
        unit = WeightUnit.entries.firstOrNull { it.name == weightUnit },
        reps = reps,
        completedAt = completedAt,
    )

    private fun SettingsEntity.toDomain() = AppSettings(
        restEnabled = restEnabled,
        restSeconds = restSeconds,
        weightUnit = WeightUnit.entries.firstOrNull { it.name == weightUnit } ?: AppSettings().weightUnit,
        workoutNotification = workoutNotification,
        notificationPrompted = notificationPrompted,
    )

    private fun AppSettings.toEntity() = SettingsEntity(
        restEnabled = restEnabled,
        restSeconds = restSeconds,
        weightUnit = weightUnit.name,
        workoutNotification = workoutNotification,
        notificationPrompted = notificationPrompted,
    )

    private fun AnimationSetting.encode(): String? = when (this) {
        AnimationSetting.Auto -> null
        AnimationSetting.Off -> ANIMATION_OFF
        is AnimationSetting.Fixed -> movement.name
    }

    // An unknown name (e.g. a movement removed in a later version) falls back to guessing.
    private fun decodeAnimation(value: String?): AnimationSetting = when (value) {
        null -> AnimationSetting.Auto
        ANIMATION_OFF -> AnimationSetting.Off
        else -> CableMovement.entries.firstOrNull { it.name == value }
            ?.let { AnimationSetting.Fixed(it) }
            ?: AnimationSetting.Auto
    }

    private companion object {
        const val ANIMATION_OFF = "OFF"
        const val ALARM_TOLERANCE_MS = 2_000L
    }
}
