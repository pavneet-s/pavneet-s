package com.pavneet.workoutcycle.domain

import com.pavneet.workoutcycle.domain.CableMovement.BEHIND_BACK_CURL
import com.pavneet.workoutcycle.domain.CableMovement.BICEPS_CURL
import com.pavneet.workoutcycle.domain.CableMovement.CABLE_CRUNCH
import com.pavneet.workoutcycle.domain.CableMovement.CHEST_FLY
import com.pavneet.workoutcycle.domain.CableMovement.CHEST_PRESS
import com.pavneet.workoutcycle.domain.CableMovement.FACE_PULL
import com.pavneet.workoutcycle.domain.CableMovement.FRONT_RAISE
import com.pavneet.workoutcycle.domain.CableMovement.GLUTE_KICKBACK
import com.pavneet.workoutcycle.domain.CableMovement.HIGH_TO_LOW_FLY
import com.pavneet.workoutcycle.domain.CableMovement.LATERAL_RAISE
import com.pavneet.workoutcycle.domain.CableMovement.LAT_PULLDOWN
import com.pavneet.workoutcycle.domain.CableMovement.LOW_TO_HIGH_FLY
import com.pavneet.workoutcycle.domain.CableMovement.OVERHEAD_TRICEPS_EXTENSION
import com.pavneet.workoutcycle.domain.CableMovement.ROW
import com.pavneet.workoutcycle.domain.CableMovement.STRAIGHT_ARM_PULLDOWN
import com.pavneet.workoutcycle.domain.CableMovement.TRICEPS_KICKBACK
import com.pavneet.workoutcycle.domain.CableMovement.TRICEPS_PUSHDOWN
import com.pavneet.workoutcycle.domain.CableMovement.WOODCHOPPER
import com.pavneet.workoutcycle.domain.CableMovement.Companion.guessFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CableMovementTest {

    @Test
    fun `default rotation is muscle groups, each starting on its default cable exercise`() {
        val exercises = WorkoutCycle.DEFAULT_EXERCISES.mapIndexed { index, name -> Exercise(index + 1L, name) }

        assertEquals(
            listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS, MuscleGroup.BICEPS),
            exercises.map { it.muscleGroup },
        )
        assertEquals(listOf(CHEST_PRESS, ROW, LATERAL_RAISE, TRICEPS_PUSHDOWN, BICEPS_CURL), exercises.map { it.movement })
    }

    @Test
    fun `the old default names keep their animations`() {
        assertEquals(CHEST_PRESS, guessFor("Push-ups"))
        assertEquals(ROW, guessFor("Back stretches"))
    }

    @Test
    fun `every muscle group has several cable exercises to swipe between`() {
        for (group in MuscleGroup.entries) {
            assertTrue("$group has ${group.movements}", group.movements.size >= 2)
            assertTrue(group.movements.all { it.group == group })
        }
    }

    @Test
    fun `specific phrases win over broad keywords`() {
        assertEquals(TRICEPS_PUSHDOWN, guessFor("Rope pushdown"))
        assertEquals(OVERHEAD_TRICEPS_EXTENSION, guessFor("Overhead triceps extension"))
        assertEquals(FACE_PULL, guessFor("Rear delt fly"))
        assertEquals(LATERAL_RAISE, guessFor("Lateral raises"))
        assertEquals(STRAIGHT_ARM_PULLDOWN, guessFor("Straight-arm pulldown"))
        assertEquals(LAT_PULLDOWN, guessFor("Kneeling lat pulldown"))
        assertEquals(GLUTE_KICKBACK, guessFor("Glute kickbacks"))
        assertEquals(TRICEPS_KICKBACK, guessFor("Tricep kickback"))
        assertEquals(HIGH_TO_LOW_FLY, guessFor("Cable crossover"))
        assertEquals(LOW_TO_HIGH_FLY, guessFor("Low to high fly"))
        assertEquals(CHEST_FLY, guessFor("Chest fly"))
        assertEquals(FRONT_RAISE, guessFor("Front raises"))
        assertEquals(BEHIND_BACK_CURL, guessFor("Bayesian curl"))
        assertEquals(CABLE_CRUNCH, guessFor("Cable crunches"))
        assertEquals(WOODCHOPPER, guessFor("Wood chops"))
    }

    @Test
    fun `muscle group names fall back to the group's first exercise`() {
        assertEquals(MuscleGroup.SHOULDERS, MuscleGroup.guessFor("Shoulder press"))
        assertEquals(MuscleGroup.LEGS, MuscleGroup.guessFor("Leg press"))
        assertEquals(MuscleGroup.CHEST, MuscleGroup.guessFor("Push day"))
        assertEquals(MuscleGroup.CORE, MuscleGroup.guessFor("Abs"))
        assertEquals(LATERAL_RAISE, guessFor("Shoulder press"))
        assertEquals(GLUTE_KICKBACK, guessFor("Glutes"))
    }

    @Test
    fun `matching ignores case, punctuation and plurals`() {
        assertEquals(FACE_PULL, guessFor("FACE-PULLS"))
        assertEquals(BICEPS_CURL, guessFor("hammer curls"))
        assertEquals(ROW, guessFor("Seated rows"))
    }

    @Test
    fun `keywords only match at the start of a word`() {
        assertNull(guessFor("Plate carry"))
        assertNull(guessFor("Plank"))
    }

    @Test
    fun `exercise movement follows its animation setting`() {
        val exercise = Exercise(id = 1, name = "Shoulders")

        assertEquals(LATERAL_RAISE, exercise.movement)
        assertEquals(FACE_PULL, exercise.copy(animation = AnimationSetting.Fixed(FACE_PULL)).movement)
        assertNull(exercise.copy(animation = AnimationSetting.Off).movement)
    }

    @Test
    fun `swipe choices are the muscle group's cable exercises`() {
        val shoulders = Exercise(id = 1, name = "Shoulders")
        val facePulls = shoulders.copy(animation = AnimationSetting.Fixed(FACE_PULL))
        val off = shoulders.copy(animation = AnimationSetting.Off)

        assertEquals(listOf(LATERAL_RAISE, FRONT_RAISE, FACE_PULL), shoulders.movementChoices)
        assertEquals(shoulders.movementChoices, facePulls.movementChoices)
        assertTrue(off.movementChoices.isEmpty())
        assertEquals(MuscleGroup.SHOULDERS, off.muscleGroup)
        assertTrue(Exercise(id = 2, name = "Stretching").movementChoices.isEmpty())
    }

    @Test
    fun `setting an animation leaves the rotation alone`() {
        val cycle = WorkoutCycle(
            exercises = listOf(Exercise(1, "Shoulders"), Exercise(2, "Biceps")),
            currentExerciseId = 2,
            setsCompleted = 3,
        )

        val updated = cycle.setAnimation(1, AnimationSetting.Fixed(FACE_PULL))

        assertEquals(FACE_PULL, updated.exercises[0].movement)
        assertEquals(cycle.exercises[1], updated.exercises[1])
        assertEquals(2L, updated.currentExerciseId)
        assertEquals(3, updated.setsCompleted)
    }
}
