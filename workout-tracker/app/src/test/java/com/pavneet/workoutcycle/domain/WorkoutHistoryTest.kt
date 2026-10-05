package com.pavneet.workoutcycle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WorkoutHistoryTest {

    private val zone = ZoneId.of("America/Toronto")
    private val today = LocalDate.of(2026, 10, 7) // a Wednesday
    private var nextId = 1L

    private fun set(
        date: LocalDate,
        name: String = "Push-ups",
        weight: Double? = null,
        unit: WeightUnit? = weight?.let { WeightUnit.LB },
        exerciseId: Long? = 1,
        time: LocalTime = LocalTime.of(18, 0),
    ) = LoggedSet(
        id = nextId++,
        exerciseId = exerciseId,
        exerciseName = name,
        weight = weight,
        unit = unit,
        reps = 10,
        completedAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
    )

    private fun history(vararg sets: LoggedSet) = WorkoutHistory(sets.toList(), zone, today)

    @Test
    fun `streak counts consecutive days ending today`() {
        val history = history(set(today), set(today.minusDays(1)), set(today.minusDays(2)), set(today.minusDays(4)))

        assertEquals(3, history.currentStreak)
    }

    @Test
    fun `streak is not broken before today's first set`() {
        val history = history(set(today.minusDays(1)), set(today.minusDays(2)))

        assertEquals(2, history.currentStreak)
    }

    @Test
    fun `streak resets after a missed day`() {
        assertEquals(0, history(set(today.minusDays(2))).currentStreak)
    }

    @Test
    fun `longest streak looks at the whole history`() {
        val history = history(
            set(today.minusDays(10)), set(today.minusDays(9)), set(today.minusDays(8)), set(today.minusDays(7)),
            set(today),
        )

        assertEquals(4, history.longestStreak)
        assertEquals(1, history.currentStreak)
    }

    @Test
    fun `days this week start on monday`() {
        val monday = LocalDate.of(2026, 10, 5)
        val history = history(set(monday), set(monday), set(today), set(monday.minusDays(1)))

        assertEquals(2, history.daysThisWeek)
    }

    @Test
    fun `sets late in the evening count for that local day`() {
        val history = history(set(today.minusDays(1), time = LocalTime.of(23, 50)))

        assertEquals(1, history.setsOn(today.minusDays(1)))
        assertEquals(0, history.setsOn(today))
    }

    @Test
    fun `sessions group sets by day, newest first`() {
        val history = history(
            set(today.minusDays(1), name = "Push-ups"),
            set(today, name = "Biceps", exerciseId = 5),
            set(today, name = "Push-ups"),
            set(today, name = "Biceps", exerciseId = 5),
        )

        assertEquals(
            listOf(
                Session(today, 3, listOf("Biceps", "Push-ups")),
                Session(today.minusDays(1), 1, listOf("Push-ups")),
            ),
            history.sessions,
        )
        assertEquals(4, history.totalSets)
    }

    @Test
    fun `progress keeps the best weight per day`() {
        val history = history(
            set(today.minusDays(7), weight = 30.0),
            set(today.minusDays(7), weight = 35.0),
            set(today, weight = 40.0),
            set(today, weight = null),
        )

        val progress = history.progress.single()
        assertEquals(listOf(ProgressPoint(today.minusDays(7), 35.0), ProgressPoint(today, 40.0)), progress.points)
        assertEquals(40.0, progress.best, 0.0)
        assertEquals(5.0, progress.change, 0.0)
    }

    @Test
    fun `progress follows the latest unit and survives renames`() {
        val history = history(
            set(today.minusDays(2), name = "Push-ups", weight = 20.0, unit = WeightUnit.KG),
            set(today.minusDays(1), name = "Chest press", weight = 7.0, unit = WeightUnit.PLATE),
            set(today, name = "Chest press", weight = 8.0, unit = WeightUnit.PLATE),
        )

        val progress = history.progress.single()
        assertEquals("Chest press", progress.exerciseName)
        assertEquals(WeightUnit.PLATE, progress.unit)
        assertEquals(2, progress.points.size)
    }

    @Test
    fun `most recently trained exercise comes first in progress`() {
        val history = history(
            set(today, name = "Biceps", weight = 25.0, exerciseId = 5),
            set(today.minusDays(1), name = "Push-ups", weight = 40.0),
        )

        assertEquals(listOf("Biceps", "Push-ups"), history.progress.map { it.exerciseName })
    }

    @Test
    fun `empty history`() {
        val history = history()

        assertTrue(history.isEmpty)
        assertEquals(0, history.currentStreak)
        assertEquals(0, history.longestStreak)
        assertTrue(history.progress.isEmpty())
    }
}
