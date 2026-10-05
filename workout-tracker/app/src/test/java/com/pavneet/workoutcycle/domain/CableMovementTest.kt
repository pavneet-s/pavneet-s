package com.pavneet.workoutcycle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CableMovementTest {

    @Test
    fun `default rotation gets a cable movement for every exercise`() {
        val guesses = WorkoutCycle.DEFAULT_EXERCISES.map { CableMovement.guessFor(it) }

        assertEquals(
            listOf(
                CableMovement.CHEST_PRESS,
                CableMovement.ROW,
                CableMovement.LATERAL_RAISE,
                CableMovement.TRICEPS_PUSHDOWN,
                CableMovement.BICEPS_CURL,
            ),
            guesses,
        )
    }

    @Test
    fun `specific phrases win over broad keywords`() {
        assertEquals(CableMovement.TRICEPS_PUSHDOWN, CableMovement.guessFor("Rope pushdown"))
        assertEquals(CableMovement.OVERHEAD_TRICEPS_EXTENSION, CableMovement.guessFor("Overhead triceps extension"))
        assertEquals(CableMovement.FACE_PULL, CableMovement.guessFor("Rear delt fly"))
        assertEquals(CableMovement.LATERAL_RAISE, CableMovement.guessFor("Lateral raises"))
        assertEquals(CableMovement.STRAIGHT_ARM_PULLDOWN, CableMovement.guessFor("Straight-arm pulldown"))
    }

    @Test
    fun `matching ignores case, punctuation and plurals`() {
        assertEquals(CableMovement.FACE_PULL, CableMovement.guessFor("FACE-PULLS"))
        assertEquals(CableMovement.BICEPS_CURL, CableMovement.guessFor("hammer curls"))
        assertEquals(CableMovement.ROW, CableMovement.guessFor("Seated rows"))
    }

    @Test
    fun `keywords only match at the start of a word`() {
        assertNull(CableMovement.guessFor("Plate carry"))
        assertNull(CableMovement.guessFor("Plank"))
    }

    @Test
    fun `exercise movement follows its animation setting`() {
        val exercise = Exercise(id = 1, name = "Shoulders")

        assertEquals(CableMovement.LATERAL_RAISE, exercise.movement)
        assertEquals(
            CableMovement.FACE_PULL,
            exercise.copy(animation = AnimationSetting.Fixed(CableMovement.FACE_PULL)).movement,
        )
        assertNull(exercise.copy(animation = AnimationSetting.Off).movement)
    }

    @Test
    fun `setting an animation leaves the rotation alone`() {
        val cycle = WorkoutCycle(
            exercises = listOf(Exercise(1, "Shoulders"), Exercise(2, "Biceps")),
            currentExerciseId = 2,
            setsCompleted = 3,
        )

        val updated = cycle.setAnimation(1, AnimationSetting.Fixed(CableMovement.FACE_PULL))

        assertEquals(CableMovement.FACE_PULL, updated.exercises[0].movement)
        assertEquals(cycle.exercises[1], updated.exercises[1])
        assertEquals(2L, updated.currentExerciseId)
        assertEquals(3, updated.setsCompleted)
    }
}
