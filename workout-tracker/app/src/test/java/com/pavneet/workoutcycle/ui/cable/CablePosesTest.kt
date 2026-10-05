package com.pavneet.workoutcycle.ui.cable

import com.pavneet.workoutcycle.domain.CableMovement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometry checks across every movement and the whole rep, so no pose glitches mid-animation. */
class CablePosesTest {

    private val phases = (0..20).map { it / 20f }

    private fun forEachFrame(check: (CableMovement, Float, CableFrame) -> Unit) {
        for (movement in CableMovement.entries) {
            for (t in phases) check(movement, t, CablePoses.frame(movement, t))
        }
    }

    private fun assertLength(expected: Float, from: ScenePoint, to: ScenePoint, what: String) =
        assertEquals(what, expected, (to - from).length, 0.05f)

    @Test
    fun `limbs keep their length`() = forEachFrame { movement, t, frame ->
        for ((name, arm) in listOf("near arm" to frame.nearArm, "far arm" to frame.farArm)) {
            assertLength(UPPER_ARM, arm.root, arm.joint, "$movement t=$t $name upper")
            assertLength(FOREARM, arm.joint, arm.end, "$movement t=$t $name forearm")
        }
        for ((name, leg) in listOf("near leg" to frame.nearLeg, "far leg" to frame.farLeg)) {
            assertLength(THIGH, leg.root, leg.joint, "$movement t=$t $name thigh")
            assertLength(SHIN, leg.joint, leg.end, "$movement t=$t $name shin")
        }
    }

    @Test
    fun `feet stay planted on the floor`() = forEachFrame { movement, t, frame ->
        assertEquals("$movement t=$t near ankle", ANKLE_Y, frame.nearLeg.end.y, 0.05f)
        assertEquals("$movement t=$t far ankle", ANKLE_Y, frame.farLeg.end.y, 0.05f)
    }

    @Test
    fun `everything stays in view and out of the machine`() = forEachFrame { movement, t, frame ->
        val right = CableScene.VIEW_LEFT + CableScene.VIEW_WIDTH
        val bottom = CableScene.VIEW_TOP + CableScene.VIEW_HEIGHT
        val bodyPoints = listOf(frame.neck, frame.pelvis, frame.nearToe, frame.farToe) +
            listOf(frame.nearArm, frame.farArm, frame.nearLeg, frame.farLeg).flatMap { listOf(it.joint, it.end) }
        for (point in bodyPoints) {
            assertTrue("$movement t=$t $point is off screen", point.x in CableScene.VIEW_LEFT..right && point.y in CableScene.VIEW_TOP..bottom)
            assertTrue("$movement t=$t $point is inside the machine", point.x < CableScene.COLUMN_LEFT)
        }
        assertTrue("$movement t=$t head cut off", frame.head.y - CableScene.HEAD_RADIUS >= CableScene.VIEW_TOP)
    }

    @Test
    fun `weight stack follows the rep`() = forEachFrame { movement, t, frame ->
        assertEquals("$movement t=$t", t, frame.stackLift, 0f)
    }

    @Test
    fun `hand moves away from the pulley while lifting`() {
        for (movement in CableMovement.entries) {
            val start = CablePoses.frame(movement, 0f)
            val end = CablePoses.frame(movement, 1f)
            assertTrue(
                "$movement: the handle should end farther from the pulley than it starts",
                (end.handle - end.pulley).length > (start.handle - start.pulley).length,
            )
        }
    }

    @Test
    fun `two bone solver bends toward the pole`() {
        val root = ScenePoint(0f, 0f)
        val target = ScenePoint(10f, 0f)

        val down = twoBone(root, target, 8f, 8f, pole = ScenePoint(0f, 1f))
        val up = twoBone(root, target, 8f, 8f, pole = ScenePoint(0f, -1f))

        assertTrue(down.joint.y > 0f)
        assertTrue(up.joint.y < 0f)
        assertEquals(10f, down.end.x, 0.001f)
    }
}
