package com.pavneet.workoutcycle.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {

    @Test
    fun `ticks are round values covering the data`() {
        assertEquals(listOf(30.0, 35.0, 40.0, 45.0), niceTicks(30.0, 45.0))
        assertEquals(listOf(100.0, 150.0, 200.0), niceTicks(100.0, 200.0))
        assertEquals(listOf(7.0, 8.0, 9.0), niceTicks(7.0, 9.0))
    }

    @Test
    fun `a single value gets room either side`() {
        val ticks = niceTicks(35.0, 35.0)

        assertTrue(ticks.first() < 35.0 && ticks.last() > 35.0)
        assertTrue(ticks.size in 3..5)
    }

    @Test
    fun `labels drop needless decimals`() {
        assertEquals("40", formatTick(40.0))
        assertEquals("32.5", formatTick(32.5))
    }
}
