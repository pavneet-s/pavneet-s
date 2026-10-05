package com.pavneet.workoutcycle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WorkoutCycleTest {

    private val defaultCycle = WorkoutCycle(
        exercises = WorkoutCycle.DEFAULT_EXERCISES.mapIndexed { index, name -> Exercise(index + 1L, name) },
    )

    private fun WorkoutCycle.completeCurrent() = completeSet(current!!.id)

    @Test
    fun `starts at the first exercise when no pointer is stored`() {
        assertEquals("Chest", defaultCycle.current?.name)
        assertEquals("Back", defaultCycle.upNext?.name)
        assertEquals(1, defaultCycle.roundPosition)
    }

    @Test
    fun `completing every exercise loops back to the start and counts a round`() {
        var cycle = defaultCycle
        val seen = mutableListOf<String>()
        repeat(5) {
            seen += cycle.current!!.name
            cycle = cycle.completeCurrent()
        }

        assertEquals(WorkoutCycle.DEFAULT_EXERCISES, seen)
        assertEquals("Chest", cycle.current?.name)
        assertEquals(5, cycle.setsCompleted)
        assertEquals(1, cycle.roundsCompleted)
    }

    @Test
    fun `round is only counted when the last exercise is completed`() {
        val cycle = defaultCycle.completeCurrent().completeCurrent()

        assertEquals(0, cycle.roundsCompleted)
        assertEquals(3, cycle.roundPosition)
    }

    @Test
    fun `up next wraps around on the last exercise`() {
        val onBiceps = defaultCycle.copy(currentExerciseId = 5)

        assertEquals("Biceps", onBiceps.current?.name)
        assertEquals("Chest", onBiceps.upNext?.name)
    }

    @Test
    fun `stale completion for a non-current exercise is ignored`() {
        val afterFirstTap = defaultCycle.completeSet(1)
        val afterSecondTap = afterFirstTap.completeSet(1)

        assertSame(afterFirstTap, afterSecondTap)
        assertEquals("Back", afterSecondTap.current?.name)
    }

    @Test
    fun `skipped exercises are passed over`() {
        val cycle = defaultCycle.setActive(2, active = false).completeCurrent()

        assertEquals("Shoulders", cycle.current?.name)
        assertEquals(4, cycle.activeExercises.size)
    }

    @Test
    fun `skipping the current exercise cues the next one`() {
        val cycle = defaultCycle.copy(currentExerciseId = 3).setActive(3, active = false)

        assertEquals("Triceps", cycle.current?.name)
        assertEquals(4L, cycle.currentExerciseId)
    }

    @Test
    fun `re-enabling a skipped exercise does not move the pointer back`() {
        val cycle = defaultCycle.setActive(1, active = false).setActive(1, active = true)

        assertEquals("Back", cycle.current?.name)
    }

    @Test
    fun `removing the current exercise cues the one after it`() {
        val cycle = defaultCycle.copy(currentExerciseId = 3).remove(3)

        assertEquals("Triceps", cycle.current?.name)
        assertEquals(4, cycle.exercises.size)
    }

    @Test
    fun `removing the current last exercise wraps to the first`() {
        val cycle = defaultCycle.copy(currentExerciseId = 5).remove(5)

        assertEquals("Chest", cycle.current?.name)
    }

    @Test
    fun `removing another exercise keeps the current one`() {
        val cycle = defaultCycle.copy(currentExerciseId = 3).remove(1)

        assertEquals("Shoulders", cycle.current?.name)
        assertEquals(2, cycle.roundPosition)
    }

    @Test
    fun `reordering keeps the current exercise current`() {
        val cycle = defaultCycle.copy(currentExerciseId = 3).reorder(listOf(3L, 5, 1, 2, 4))

        assertEquals("Shoulders", cycle.current?.name)
        assertEquals(1, cycle.roundPosition)
        assertEquals("Biceps", cycle.upNext?.name)
    }

    @Test
    fun `reorder appends exercises missing from the new order`() {
        val cycle = defaultCycle.reorder(listOf(2L, 1))

        assertEquals(listOf(2L, 1, 3, 4, 5), cycle.exercises.map { it.id })
    }

    @Test
    fun `added exercise joins the end of the rotation`() {
        val cycle = defaultCycle.copy(currentExerciseId = 5).add(Exercise(6, "Squats"))

        assertEquals("Squats", cycle.upNext?.name)
        assertEquals("Squats", cycle.completeCurrent().current?.name)
    }

    @Test
    fun `adding to an empty cycle makes the new exercise current`() {
        val cycle = WorkoutCycle(exercises = emptyList()).add(Exercise(1, "Plank"))

        assertEquals(1L, cycle.currentExerciseId)
        assertEquals("Plank", cycle.upNext?.name)
    }

    @Test
    fun `cycle with nothing active has no current exercise and ignores completions`() {
        val cycle = defaultCycle.exercises.fold(defaultCycle) { acc, e -> acc.setActive(e.id, active = false) }

        assertNull(cycle.current)
        assertNull(cycle.upNext)
        assertEquals(0, cycle.roundPosition)
        assertSame(cycle, cycle.completeSet(1))
    }

    @Test
    fun `restart resets counters and returns to the first active exercise`() {
        val cycle = defaultCycle.setActive(1, active = false)
            .completeCurrent().completeCurrent()
            .restart()

        assertEquals("Back", cycle.current?.name)
        assertEquals(0, cycle.setsCompleted)
        assertEquals(0, cycle.roundsCompleted)
    }

    @Test
    fun `restoring progress undoes a completion`() {
        val before = defaultCycle.copy(currentExerciseId = 5, setsCompleted = 4)
        val after = before.completeCurrent()

        val undone = after.restoreProgress(before)

        assertEquals("Biceps", undone.current?.name)
        assertEquals(4, undone.setsCompleted)
        assertEquals(0, undone.roundsCompleted)
    }
}
