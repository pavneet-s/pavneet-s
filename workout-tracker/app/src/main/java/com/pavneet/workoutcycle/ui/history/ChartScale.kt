package com.pavneet.workoutcycle.ui.history

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Round axis values covering [min]..[max], e.g. 30, 35, 40, 45: steps of 1, 2, 2.5 or 5 times a
 * power of ten, about three to five of them. A single value gets some room either side.
 */
internal fun niceTicks(min: Double, max: Double): List<Double> {
    val flat = max <= min
    val span = if (flat) maxOf(1.0, abs(max) * 0.2) else max - min
    val rough = span / 3
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    val low = floor((if (flat) min - span / 2 else min) / step) * step
    val high = ceil((if (flat) max + span / 2 else max) / step) * step
    return generateSequence(low) { it + step }.takeWhile { it <= high + step / 2 }.toList()
}

/** "35" or "32.5" for axis labels. */
internal fun formatTick(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
