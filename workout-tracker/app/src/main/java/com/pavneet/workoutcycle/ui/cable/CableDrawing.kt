package com.pavneet.workoutcycle.ui.cable

import com.pavneet.workoutcycle.domain.StackLoad

/** Colour roles; the Compose renderer maps them onto the Material theme. */
enum class Ink { BACKGROUND, FLOOR, MACHINE, PLATE, PLATE_LIFTED, CABLE, FIGURE_FAR, FIGURE, HANDLE }

/** A primitive in scene units. A null stroke width means filled. */
sealed interface SceneShape {
    val ink: Ink

    data class Line(val from: ScenePoint, val to: ScenePoint, val width: Float, override val ink: Ink) : SceneShape

    data class Circle(
        val center: ScenePoint,
        val radius: Float,
        override val ink: Ink,
        val strokeWidth: Float? = null,
    ) : SceneShape

    data class Box(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val corner: Float,
        override val ink: Ink,
        val strokeWidth: Float? = null,
    ) : SceneShape
}

/** Turns a [CableFrame] into shapes, back to front. Pure Kotlin, so it renders off-device too. */
object CableDrawing {
    private const val LIMB_WIDTH = 3.3f
    private const val TORSO_WIDTH = 5.2f
    private const val HALO_WIDTH = LIMB_WIDTH + 1.8f
    private const val PLATES = StackLoad.STACK_PLATES
    private const val PLATE_PITCH = 2.6f
    private const val PLATE_HEIGHT = 2f
    private const val PLATE_INSET = 1.8f
    private const val HALF_PLATE_HEIGHT = 1.2f
    private const val HALF_PLATE_INSET = 3.4f
    private const val STACK_TRAVEL = 10f

    /** For previews with no weight of their own to show. */
    val PREVIEW_LOAD = StackLoad(plates = 4)

    /** [load] is how much of the weight stack is pinned, so heavier sets light up more plates. */
    fun shapes(frame: CableFrame, load: StackLoad = PREVIEW_LOAD): List<SceneShape> = buildList {
        addMachine(frame, load)
        val cable = SceneShape.Line(frame.pulley, frame.handle, 0.6f, Ink.CABLE)
        // From the side the cable runs beside the body; from the front it crosses in front of it.
        if (!frame.frontView) add(cable)
        addFigure(frame)
        if (frame.frontView) add(cable)
        add(SceneShape.Circle(frame.handle, 1.7f, Ink.HANDLE))
    }

    private fun MutableList<SceneShape>.addMachine(frame: CableFrame, load: StackLoad): Unit = with(CableScene) {
        add(SceneShape.Line(ScenePoint(4f, FLOOR_Y), ScenePoint(98f, FLOOR_Y), 0.8f, Ink.FLOOR))
        add(SceneShape.Box(COLUMN_LEFT, COLUMN_TOP, COLUMN_RIGHT, FLOOR_Y, corner = 1.5f, Ink.MACHINE, strokeWidth = 1.1f))
        for (rodX in listOf(COLUMN_LEFT + 3.2f, COLUMN_RIGHT - 3.2f)) {
            add(SceneShape.Line(ScenePoint(rodX, COLUMN_TOP + 5f), ScenePoint(rodX, FLOOR_Y - 1f), 0.5f, Ink.MACHINE))
        }

        // The pin picks how many plates from the top rise with the lift; the rest stay down.
        val pinned = load.plates.coerceIn(0, PLATES)
        val lift = frame.stackLift * STACK_TRAVEL
        var stackTop = FLOOR_Y
        for (plate in 0 until PLATES) {
            val lifted = plate >= PLATES - pinned
            val bottom = FLOOR_Y - 1.2f - plate * PLATE_PITCH - if (lifted) lift else 0f
            add(
                SceneShape.Box(
                    COLUMN_LEFT + PLATE_INSET, bottom - PLATE_HEIGHT, COLUMN_RIGHT - PLATE_INSET, bottom,
                    corner = 0.6f,
                    ink = if (lifted) Ink.PLATE_LIFTED else Ink.PLATE,
                ),
            )
            stackTop = bottom - PLATE_HEIGHT
        }
        // A half-plate add-on rides on top, for the 5 lb (or 2.5 kg) between plates.
        if (load.halfPlate) {
            val bottom = stackTop - (PLATE_PITCH - PLATE_HEIGHT) - if (pinned == 0) lift else 0f
            add(
                SceneShape.Box(
                    COLUMN_LEFT + HALF_PLATE_INSET, bottom - HALF_PLATE_HEIGHT, COLUMN_RIGHT - HALF_PLATE_INSET, bottom,
                    corner = 0.5f,
                    ink = Ink.PLATE_LIFTED,
                ),
            )
            stackTop = bottom - HALF_PLATE_HEIGHT
        }
        val centerX = (COLUMN_LEFT + COLUMN_RIGHT) / 2f
        add(SceneShape.Line(ScenePoint(centerX, stackTop), ScenePoint(centerX, COLUMN_TOP + 2.5f), 0.5f, Ink.CABLE))

        // Pulley on its carriage at the front of the column.
        add(SceneShape.Line(frame.pulley, ScenePoint(COLUMN_LEFT, frame.pulley.y), 1.2f, Ink.MACHINE))
        add(SceneShape.Circle(frame.pulley, PULLEY_RADIUS, Ink.MACHINE, strokeWidth = 0.9f))
        add(SceneShape.Circle(frame.pulley, 0.6f, Ink.MACHINE))
    }

    private fun MutableList<SceneShape>.addFigure(frame: CableFrame) {
        val farInk = if (frame.frontView) Ink.FIGURE else Ink.FIGURE_FAR
        addLimb(frame.farLeg, farInk)
        add(SceneShape.Line(frame.farLeg.end, frame.farToe, LIMB_WIDTH * 0.8f, farInk))
        addLimb(frame.farArm, farInk)
        if (frame.frontView) {
            add(SceneShape.Line(frame.nearLeg.root, frame.farLeg.root, LIMB_WIDTH, Ink.FIGURE))
            add(SceneShape.Line(frame.nearArm.root, frame.farArm.root, LIMB_WIDTH, Ink.FIGURE))
        }
        add(SceneShape.Line(frame.pelvis, frame.neck, TORSO_WIDTH, Ink.FIGURE))
        add(SceneShape.Circle(frame.head, CableScene.HEAD_RADIUS, Ink.FIGURE))
        addLimb(frame.nearLeg, Ink.FIGURE)
        add(SceneShape.Line(frame.nearLeg.end, frame.nearToe, LIMB_WIDTH * 0.8f, Ink.FIGURE))
        addNearArm(frame.nearArm)
    }

    // A background-coloured outline keeps the arm readable where it crosses the head or torso.
    // It starts partway down the upper arm so it doesn't notch the shoulder.
    private fun MutableList<SceneShape>.addNearArm(arm: Limb) {
        add(SceneShape.Line(arm.root + (arm.joint - arm.root) * 0.4f, arm.joint, HALO_WIDTH, Ink.BACKGROUND))
        add(SceneShape.Line(arm.joint, arm.end, HALO_WIDTH, Ink.BACKGROUND))
        addLimb(arm, Ink.FIGURE)
    }

    private fun MutableList<SceneShape>.addLimb(limb: Limb, ink: Ink) {
        add(SceneShape.Line(limb.root, limb.joint, LIMB_WIDTH, ink))
        add(SceneShape.Line(limb.joint, limb.end, LIMB_WIDTH, ink))
    }
}
