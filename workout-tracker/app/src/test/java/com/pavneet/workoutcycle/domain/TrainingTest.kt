package com.pavneet.workoutcycle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {

    @Test
    fun `weight steps follow the unit`() {
        assertEquals(5.0, WeightUnit.LB.increase(null), 0.0)
        assertEquals(40.0, WeightUnit.LB.increase(35.0), 0.0)
        assertEquals(22.5, WeightUnit.KG.increase(20.0), 0.0)
        assertEquals(8.0, WeightUnit.PLATE.increase(7.0), 0.0)
    }

    @Test
    fun `decreasing to zero clears the weight`() {
        assertEquals(30.0, WeightUnit.LB.decrease(35.0)!!, 0.0)
        assertNull(WeightUnit.LB.decrease(5.0))
        assertNull(WeightUnit.LB.decrease(null))
    }

    @Test
    fun `weights print without needless decimals`() {
        assertEquals("35", formatWeight(35.0))
        assertEquals("22.5", formatWeight(22.5))
    }

    @Test
    fun `the weight stack lifts a plate per 10 lb or 5 kg, with a half plate in between`() {
        assertEquals(StackLoad.EMPTY, StackLoad.of(null, WeightUnit.LB))
        assertEquals(StackLoad(0, halfPlate = true), StackLoad.of(5.0, WeightUnit.LB))
        assertEquals(StackLoad(1), StackLoad.of(10.0, WeightUnit.LB))
        assertEquals(StackLoad(1, halfPlate = true), StackLoad.of(15.0, WeightUnit.LB))
        assertEquals(StackLoad(4, halfPlate = true), StackLoad.of(22.5, WeightUnit.KG))
        assertEquals(StackLoad(7), StackLoad.of(7.0, WeightUnit.PLATE))
        assertEquals(StackLoad(StackLoad.STACK_PLATES, halfPlate = true), StackLoad.of(500.0, WeightUnit.LB))
    }

    @Test
    fun `every tap of plus lifts more of the stack until it is all lifted`() {
        for (unit in WeightUnit.entries) {
            var weight: Double? = null
            var lifted = 0
            repeat(if (unit == WeightUnit.PLATE) StackLoad.STACK_PLATES else StackLoad.STACK_PLATES * 2) {
                weight = unit.increase(weight)
                val load = StackLoad.of(weight, unit)
                val halves = load.plates * 2 + if (load.halfPlate) 1 else 0
                assertTrue("$unit at $weight", halves > lifted)
                lifted = halves
            }
        }
    }

    @Test
    fun `reps start at a typical set and never go below one`() {
        assertEquals(10, SetEntry().increaseReps().reps)
        assertEquals(13, SetEntry(reps = 12).increaseReps().reps)
        assertNull(SetEntry(reps = 1).decreaseReps().reps)
    }

    @Test
    fun `rest runs until its end time and can be extended or ended`() {
        val cycle = WorkoutCycle(exercises = listOf(Exercise(1, "Biceps"))).startRest(endsAt = 60_000)

        assertTrue(cycle.isResting(now = 59_999))
        assertFalse(cycle.isResting(now = 60_000))
        assertEquals(75_000L, cycle.extendRest(15_000, now = 30_000).restEndsAt)
        assertEquals(cycle, cycle.extendRest(15_000, now = 61_000))
        assertNull(cycle.endRest().restEndsAt)
    }

    @Test
    fun `undo and restart cancel any rest`() {
        val before = WorkoutCycle(exercises = listOf(Exercise(1, "Biceps"), Exercise(2, "Triceps")))
        val resting = before.completeSet(1).startRest(endsAt = 60_000)

        assertNull(resting.restoreProgress(before).restEndsAt)
        assertNull(resting.restart().restEndsAt)
    }

    @Test
    fun `rename and setup change only that exercise`() {
        val cycle = WorkoutCycle(exercises = listOf(Exercise(1, "Biceps"), Exercise(2, "Triceps")))
        val setup = MachineSetup(Attachment.ROPE, pulleyPosition = "Notch 12", note = "Two steps back")

        val updated = cycle.rename(1, "  Hammer curls ").setSetup(2, setup)

        assertEquals("Hammer curls", updated.exercises[0].name)
        assertEquals(setup, updated.exercises[1].setup)
        assertTrue(updated.exercises[0].setup.isEmpty)
        assertEquals(cycle, cycle.rename(1, "   "))
    }
}
