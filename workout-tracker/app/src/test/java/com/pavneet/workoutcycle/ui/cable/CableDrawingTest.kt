package com.pavneet.workoutcycle.ui.cable

import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.StackLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CableDrawingTest {

    private fun plates(load: StackLoad, t: Float = 1f): List<SceneShape.Box> =
        CableDrawing.shapes(CablePoses.frame(CableMovement.ROW, t), load)
            .filterIsInstance<SceneShape.Box>()
            .filter { it.ink == Ink.PLATE || it.ink == Ink.PLATE_LIFTED }

    @Test
    fun `heavier weights light up more of the stack`() {
        fun lifted(load: StackLoad) = plates(load).count { it.ink == Ink.PLATE_LIFTED }

        assertEquals(0, lifted(StackLoad.EMPTY))
        assertEquals(1, lifted(StackLoad(0, halfPlate = true)))
        assertEquals(3, lifted(StackLoad(3)))
        assertEquals(4, lifted(StackLoad(3, halfPlate = true)))
        assertEquals(StackLoad.STACK_PLATES, lifted(StackLoad(StackLoad.STACK_PLATES)))
    }

    @Test
    fun `the whole stack is always drawn, plus the add-on when used`() {
        assertEquals(StackLoad.STACK_PLATES, plates(StackLoad(3)).size)
        assertEquals(StackLoad.STACK_PLATES + 1, plates(StackLoad(3, halfPlate = true)).size)
    }

    @Test
    fun `only the pinned plates rise with the rep`() {
        val load = StackLoad(2, halfPlate = true)
        val resting = plates(load, t = 0f)
        val lifted = plates(load, t = 1f)

        resting.zip(lifted).forEach { (before, after) ->
            if (after.ink == Ink.PLATE_LIFTED) {
                assertTrue("a pinned plate should rise", after.top < before.top - 5f)
            } else {
                assertEquals(before.top, after.top, 0f)
            }
        }
    }

    @Test
    fun `an add-on alone rises from the top of the stack`() {
        val addOn = { t: Float -> plates(StackLoad(0, halfPlate = true), t).single { it.ink == Ink.PLATE_LIFTED } }
        assertTrue(addOn(1f).top < addOn(0f).top - 5f)
    }
}
