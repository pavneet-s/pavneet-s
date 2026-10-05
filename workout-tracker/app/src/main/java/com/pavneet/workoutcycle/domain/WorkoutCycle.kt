package com.pavneet.workoutcycle.domain

/**
 * One slot in the rotation, usually a muscle group such as "Chest", trained with one of its
 * cable exercises ([movement]).
 *
 * @property isActive `false` means "skip it for now": the exercise keeps its slot in the
 * rotation but is passed over until it is switched back on.
 */
data class Exercise(
    val id: Long,
    val name: String,
    val isActive: Boolean = true,
    val animation: AnimationSetting = AnimationSetting.Auto,
    val setup: MachineSetup = MachineSetup(),
) {
    /** The cable exercise to animate and log, or `null` for none. */
    val movement: CableMovement?
        get() = when (animation) {
            AnimationSetting.Auto -> CableMovement.guessFor(name)
            AnimationSetting.Off -> null
            is AnimationSetting.Fixed -> animation.movement
        }

    /** The muscle group trained: the chosen exercise's, or else a guess from the name. */
    val muscleGroup: MuscleGroup?
        get() = movement?.group ?: MuscleGroup.guessFor(name)

    /** The cable exercises to swipe between: the muscle group's, or none without an animation. */
    val movementChoices: List<CableMovement>
        get() = movement?.group?.movements.orEmpty()
}

/**
 * Immutable snapshot of the cyclical workout. Every operation returns a new snapshot, so the
 * logic is plain Kotlin that the repository persists and the unit tests exercise directly.
 *
 * The order of [exercises] is the rotation order. The pointer is the current exercise's **id**,
 * never a list index, so reordering, skipping or removing other exercises can't make it drift
 * onto the wrong movement.
 */
data class WorkoutCycle(
    val exercises: List<Exercise>,
    val currentExerciseId: Long? = null,
    val setsCompleted: Int = 0,
    val roundsCompleted: Int = 0,
    /** When the current rest period ends (epoch ms), or `null` when not resting. */
    val restEndsAt: Long? = null,
) {
    /** The exercises you actually cycle through, in order. */
    val activeExercises: List<Exercise> = exercises.filter { it.isActive }

    /**
     * The movement to do now. If the pointer sits on a skipped exercise, the next active one
     * after it (wrapping around) takes its place; with no valid pointer, the cycle starts at
     * the top. `null` only when nothing is active.
     */
    val current: Exercise? = resolveCurrent()

    /** 1-based position of [current] within a round, or 0 when the cycle is empty. */
    val roundPosition: Int = activeExercises.indexOfFirst { it.id == current?.id } + 1

    /** The movement after [current], wrapping to the start. Equals [current] if it's the only one. */
    val upNext: Exercise? = if (roundPosition == 0) null else activeExercises[roundPosition % activeExercises.size]

    /**
     * Marks [exerciseId] done and cues the next movement. Finishing the last exercise of the
     * rotation completes a round and loops back to the first.
     *
     * No-op unless [exerciseId] is the current exercise, so a double tap on the "done" button
     * (whose second tap still carries the old id) can't skip a movement.
     */
    fun completeSet(exerciseId: Long): WorkoutCycle {
        if (current?.id != exerciseId) return this
        val nextIndex = roundPosition % activeExercises.size
        return copy(
            currentExerciseId = activeExercises[nextIndex].id,
            setsCompleted = setsCompleted + 1,
            roundsCompleted = if (nextIndex == 0) roundsCompleted + 1 else roundsCompleted,
        )
    }

    /** Appends [exercise] to the end of the rotation. */
    fun add(exercise: Exercise): WorkoutCycle = copy(exercises = exercises + exercise).normalized()

    /** Skips ([active] = false) or restores an exercise. Skipping the current one cues the next. */
    fun setActive(exerciseId: Long, active: Boolean): WorkoutCycle = copy(
        exercises = exercises.map { if (it.id == exerciseId) it.copy(isActive = active) else it },
    ).normalized()

    /** Deletes an exercise. Removing the current one cues the movement that followed it. */
    fun remove(exerciseId: Long): WorkoutCycle {
        // Skipping first moves the pointer off the exercise while it still has a position.
        val skipped = setActive(exerciseId, active = false)
        return skipped.copy(exercises = skipped.exercises.filterNot { it.id == exerciseId })
    }

    /**
     * Applies a new rotation order. The current exercise stays current wherever it moves to.
     * Exercises missing from [orderedIds] keep their relative order at the end.
     */
    fun reorder(orderedIds: List<Long>): WorkoutCycle {
        val byId = exercises.associateBy { it.id }
        val reordered = orderedIds.distinct().mapNotNull { byId[it] }
        val idsInOrder = reordered.map { it.id }.toSet()
        return copy(exercises = reordered + exercises.filterNot { it.id in idsInOrder }).normalized()
    }

    /** Renames an exercise; blank names are ignored. Logged history keeps the old name. */
    fun rename(exerciseId: Long, name: String): WorkoutCycle {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return this
        return copy(exercises = exercises.map { if (it.id == exerciseId) it.copy(name = trimmed) else it })
    }

    /** Saves how the machine is set up for [exerciseId]. */
    fun setSetup(exerciseId: Long, setup: MachineSetup): WorkoutCycle = copy(
        exercises = exercises.map { if (it.id == exerciseId) it.copy(setup = setup) else it },
    )

    fun isResting(now: Long): Boolean = restEndsAt != null && restEndsAt > now

    fun startRest(endsAt: Long): WorkoutCycle = copy(restEndsAt = endsAt)

    fun endRest(): WorkoutCycle = if (restEndsAt == null) this else copy(restEndsAt = null)

    /** Adds time to a rest that's still running; a finished rest stays finished. */
    fun extendRest(byMillis: Long, now: Long): WorkoutCycle =
        if (isResting(now)) copy(restEndsAt = restEndsAt!! + byMillis) else this

    /** Chooses how [exerciseId] is animated; the rotation itself is unchanged. */
    fun setAnimation(exerciseId: Long, animation: AnimationSetting): WorkoutCycle = copy(
        exercises = exercises.map { if (it.id == exerciseId) it.copy(animation = animation) else it },
    )

    /** Starts over from the first active exercise with fresh counters. */
    fun restart(): WorkoutCycle = copy(
        currentExerciseId = activeExercises.firstOrNull()?.id,
        setsCompleted = 0,
        roundsCompleted = 0,
        restEndsAt = null,
    )

    /**
     * Rolls the pointer and counters back to [snapshot] (undo), keeping the current exercise
     * list. Any rest is cancelled, since it belonged to the set being undone.
     */
    fun restoreProgress(snapshot: WorkoutCycle): WorkoutCycle = copy(
        currentExerciseId = snapshot.currentExerciseId,
        setsCompleted = snapshot.setsCompleted,
        roundsCompleted = snapshot.roundsCompleted,
        restEndsAt = null,
    ).normalized()

    /** Pins the stored pointer to the resolved [current] so what's saved matches what's shown. */
    private fun normalized(): WorkoutCycle = copy(currentExerciseId = current?.id)

    private fun resolveCurrent(): Exercise? {
        if (activeExercises.isEmpty()) return null
        val start = exercises.indexOfFirst { it.id == currentExerciseId }
        if (start == -1) return activeExercises.first()
        return exercises.indices
            .map { offset -> exercises[(start + offset) % exercises.size] }
            .first { it.isActive }
    }

    companion object {
        val DEFAULT_EXERCISES = listOf("Chest", "Back", "Shoulders", "Triceps", "Biceps")
    }
}
