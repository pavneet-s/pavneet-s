package com.pavneet.workoutcycle.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One training day: how many sets and which exercises, in the order they were first done. */
data class Session(val date: LocalDate, val setCount: Int, val exerciseNames: List<String>)

/** The heaviest weight logged for an exercise on one day. */
data class ProgressPoint(val date: LocalDate, val weight: Double)

/**
 * Weight progress for one cable exercise of a muscle group (or one exercise without an
 * animation), in the unit it was most recently logged in.
 */
data class ExerciseProgress(
    val exerciseName: String,
    val movement: CableMovement?,
    val unit: WeightUnit,
    /** One point per training day, oldest first. */
    val points: List<ProgressPoint>,
) {
    val latest: Double get() = points.last().weight
    val best: Double get() = points.maxOf { it.weight }

    /** Latest minus first; positive means you've moved up. */
    val change: Double get() = points.last().weight - points.first().weight
}

/**
 * Stats over the logged sets. [today] and [zone] are passed in, so the date maths is
 * deterministic in tests and follows the phone's time zone in the app.
 */
class WorkoutHistory(sets: List<LoggedSet>, private val zone: ZoneId, val today: LocalDate) {

    private val sorted = sets.sortedWith(compareBy({ it.completedAt }, { it.id }))
    private val byDay: Map<LocalDate, List<LoggedSet>> = sorted.groupBy { it.date() }

    val totalSets: Int = sorted.size

    val isEmpty: Boolean get() = totalSets == 0

    fun setsOn(day: LocalDate): Int = byDay[day]?.size ?: 0

    /**
     * Consecutive training days up to today. A streak isn't broken until a whole day passes,
     * so before today's first set it still counts through yesterday.
     */
    val currentStreak: Int = run {
        var day = if (today in byDay) today else today.minusDays(1)
        var streak = 0
        while (day in byDay) {
            streak++
            day = day.minusDays(1)
        }
        streak
    }

    val longestStreak: Int = run {
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in byDay.keys.sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        longest
    }

    /** Days trained in the current Monday-to-Sunday week. */
    val daysThisWeek: Int = run {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        byDay.keys.count { it in monday..today }
    }

    /** Training days, newest first. */
    val sessions: List<Session> = byDay.entries
        .sortedByDescending { it.key }
        .map { (date, sets) -> Session(date, sets.size, sets.map { it.exerciseName }.distinct()) }

    /**
     * Exercises with logged weights, most recently trained first. Each cable exercise of a
     * muscle group is charted on its own, since a fly and a press use very different weights.
     */
    val progress: List<ExerciseProgress> = sorted
        .filter { it.weight != null && it.unit != null }
        // An exercise keeps its history through renames; deleted ones fall back to their name.
        .groupBy { (it.exerciseId?.let { id -> "id:$id" } ?: "name:${it.exerciseName}") to it.movement }
        .values
        .map { sets ->
            val latest = sets.last()
            val unit = latest.unit!!
            val points = sets
                .filter { it.unit == unit }
                .groupBy { it.date() }
                .map { (date, daySets) -> ProgressPoint(date, daySets.maxOf { it.weight!! }) }
                .sortedBy { it.date }
            ExerciseProgress(latest.exerciseName, latest.movement, unit, points) to latest.completedAt
        }
        .sortedByDescending { it.second }
        .map { it.first }

    private fun LoggedSet.date(): LocalDate = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
}
