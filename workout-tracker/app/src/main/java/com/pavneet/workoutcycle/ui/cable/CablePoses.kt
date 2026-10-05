package com.pavneet.workoutcycle.ui.cable

import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.PulleyHeight
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in scene units: x grows to the right, y grows downward. */
data class ScenePoint(val x: Float, val y: Float) {
    operator fun plus(other: ScenePoint) = ScenePoint(x + other.x, y + other.y)
    operator fun minus(other: ScenePoint) = ScenePoint(x - other.x, y - other.y)
    operator fun times(factor: Float) = ScenePoint(x * factor, y * factor)
    operator fun unaryMinus() = ScenePoint(-x, -y)
    infix fun dot(other: ScenePoint) = x * other.x + y * other.y
    val length: Float get() = sqrt(x * x + y * y)
}

/** Shoulder, elbow, hand — or hip, knee, ankle. */
data class Limb(val root: ScenePoint, val joint: ScenePoint, val end: ScenePoint)

/** One frame of a cable exercise, in scene units. */
data class CableFrame(
    /** The figure faces the viewer instead of being seen from the side. */
    val frontView: Boolean,
    val head: ScenePoint,
    val neck: ScenePoint,
    val pelvis: ScenePoint,
    /** In side view the near limbs face the viewer and the far ones are drawn faded. */
    val nearArm: Limb,
    val farArm: Limb,
    val nearLeg: Limb,
    val farLeg: Limb,
    val nearToe: ScenePoint,
    val farToe: ScenePoint,
    val pulley: ScenePoint,
    /** Where the cable meets the handle. */
    val handle: ScenePoint,
    /** 0 = weight stack resting, 1 = fully lifted. */
    val stackLift: Float,
)

/** Fixed layout: the figure stands on the left, a cable tower with its weight stack on the right. */
object CableScene {
    const val VIEW_LEFT = 0f
    const val VIEW_TOP = 12f
    const val VIEW_WIDTH = 100f
    const val VIEW_HEIGHT = 86f
    const val FLOOR_Y = 94f
    const val COLUMN_LEFT = 82f
    const val COLUMN_RIGHT = 96f
    const val COLUMN_TOP = 14f
    const val PULLEY_X = 79.5f
    const val PULLEY_RADIUS = 2f
    const val HEAD_RADIUS = 4.4f

    fun pulleyY(height: PulleyHeight) = when (height) {
        PulleyHeight.HIGH -> 22f
        PulleyHeight.CHEST -> 49f
        PulleyHeight.LOW -> 86f
    }
}

/** Body proportions in scene units; a standing figure is about 62 units tall. */
internal const val NECK = 1.6f
internal const val TORSO = 19f
internal const val UPPER_ARM = 11.5f
internal const val FOREARM = 11f
internal const val THIGH = 16.5f
internal const val SHIN = 16f
internal const val ANKLE_Y = CableScene.FLOOR_Y - 1.6f

/**
 * Key poses for every [CableMovement]. [t] runs from 0 (start of the rep) to 1 (fully
 * contracted); the animation plays it forward to lift and backward to lower.
 */
object CablePoses {

    fun frame(movement: CableMovement, t: Float): CableFrame = when (movement) {
        CableMovement.CHEST_PRESS -> chestPress(t)
        CableMovement.ROW -> row(t)
        CableMovement.STRAIGHT_ARM_PULLDOWN -> straightArmPulldown(t)
        CableMovement.FACE_PULL -> facePull(t)
        CableMovement.LATERAL_RAISE -> lateralRaise(t)
        CableMovement.TRICEPS_PUSHDOWN -> tricepsPushdown(t)
        CableMovement.OVERHEAD_TRICEPS_EXTENSION -> overheadTricepsExtension(t)
        CableMovement.BICEPS_CURL -> bicepsCurl(t)
    }

    // Standing with the back to the machine, pressing handles forward from the chest.
    private fun chestPress(t: Float): CableFrame {
        val pose = SidePose(facing = AWAY, pelvis = ScenePoint(62f, 62.5f), lean = 12f)
        val arm = pose.armReach(forward = lerp(3f, 21f, t), down = lerp(8.5f, 4.5f, t), elbow = ScenePoint(-0.6f, 1f))
        return pose.frame(PulleyHeight.CHEST, nearFootX = 53f, farFootX = 70f, arm = arm, stackLift = t)
    }

    // Facing the machine, pulling the handle from arm's length to the stomach.
    private fun row(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(40f, 63.5f), lean = lerp(16f, 4f, t))
        val arm = pose.armReach(forward = lerp(21.5f, 1f, t), down = lerp(6f, 11f, t), elbow = ScenePoint(-1f, 0.4f))
        return pose.frame(PulleyHeight.CHEST, nearFootX = 45f, farFootX = 38f, arm = arm, stackLift = t)
    }

    // Hinged forward, sweeping straight arms from overhead down to the thighs.
    private fun straightArmPulldown(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(45f, 63f), lean = 24f)
        val shoulder = lerp(38f, 166f, t)
        val arm = pose.armAngles(upper = shoulder, forearm = shoulder - 6f)
        return pose.frame(PulleyHeight.HIGH, nearFootX = 50f, farFootX = 43f, arm = arm, stackLift = t)
    }

    // Pulling a rope from a high pulley toward the face, elbows high.
    private fun facePull(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(42f, 62.5f), lean = lerp(8f, 0f, t))
        val arm = pose.armReach(forward = lerp(21f, 7f, t), down = lerp(-3f, -5f, t), elbow = ScenePoint(-1f, -0.15f))
        return pose.frame(PulleyHeight.HIGH, nearFootX = 47f, farFootX = 39f, arm = arm, stackLift = t)
    }

    // Seen from the front: side-on to the machine, raising the far arm out to shoulder height.
    private fun lateralRaise(t: Float): CableFrame {
        val centerX = 58f
        val pelvis = ScenePoint(centerX, 60.2f)
        val neck = ScenePoint(centerX, pelvis.y - TORSO)
        val head = neck + ScenePoint(0f, -(NECK + CableScene.HEAD_RADIUS))
        val workingShoulder = ScenePoint(centerX - 6f, neck.y + 1.4f)
        val raise = lerp(-14f, 86f, t)
        val elbow = workingShoulder + sideways(raise) * UPPER_ARM
        val workingArm = Limb(workingShoulder, elbow, elbow + sideways(raise - 8f) * FOREARM)
        // The other hand steadies the body on the machine.
        val supportArm = twoBone(
            root = ScenePoint(centerX + 6f, neck.y + 1.4f),
            target = ScenePoint(CableScene.COLUMN_LEFT - 0.8f, 53f),
            upper = UPPER_ARM,
            lower = FOREARM,
            pole = ScenePoint(0.2f, 1f),
        )
        val leftLeg = twoBone(ScenePoint(centerX - 3.6f, pelvis.y), ScenePoint(centerX - 6.5f, ANKLE_Y), THIGH, SHIN, ScenePoint(-1f, 0f))
        val rightLeg = twoBone(ScenePoint(centerX + 3.6f, pelvis.y), ScenePoint(centerX + 6.5f, ANKLE_Y), THIGH, SHIN, ScenePoint(1f, 0f))
        return CableFrame(
            frontView = true,
            head = head,
            neck = neck,
            pelvis = pelvis,
            nearArm = workingArm,
            farArm = supportArm,
            nearLeg = leftLeg,
            farLeg = rightLeg,
            nearToe = leftLeg.end + ScenePoint(-3.4f, 1.6f),
            farToe = rightLeg.end + ScenePoint(3.4f, 1.6f),
            pulley = ScenePoint(CableScene.PULLEY_X, CableScene.pulleyY(PulleyHeight.LOW)),
            handle = workingArm.end,
            stackLift = t,
        )
    }

    // Elbows pinned at the sides, pushing a rope from chest height down to straight arms.
    private fun tricepsPushdown(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(57f, 62.5f), lean = 12f)
        val arm = pose.armAngles(upper = 172f, forearm = lerp(78f, 174f, t))
        return pose.frame(PulleyHeight.HIGH, nearFootX = 61f, farFootX = 54f, arm = arm, stackLift = t)
    }

    // Back to the machine, leaning forward, extending the rope from behind the head.
    private fun overheadTricepsExtension(t: Float): CableFrame {
        val pose = SidePose(facing = AWAY, pelvis = ScenePoint(61f, 63f), lean = 24f)
        val arm = pose.armAngles(upper = 40f, forearm = lerp(245f, 400f, t))
        return pose.frame(PulleyHeight.HIGH, nearFootX = 51f, farFootX = 69f, arm = arm, stackLift = t)
    }

    // Facing a low pulley, curling the handle up with the elbows at the sides.
    private fun bicepsCurl(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(52f, 62f), lean = 3f)
        val arm = pose.armAngles(upper = 172f, forearm = lerp(166f, 32f, t))
        return pose.frame(PulleyHeight.LOW, nearFootX = 55f, farFootX = 49f, arm = arm, stackLift = t)
    }
}

private const val TOWARD = 1f
private const val AWAY = -1f

/**
 * Side view of a figure facing toward (+1, right) or away from (-1, left) the machine.
 * Angles are absolute: 0° points up, 90° forward and 180° down.
 */
private class SidePose(private val facing: Float, val pelvis: ScenePoint, lean: Float) {
    val neck = pelvis + direction(lean) * TORSO
    val head = neck + direction(lean) * (NECK + CableScene.HEAD_RADIUS)

    fun direction(degrees: Float): ScenePoint {
        val radians = Math.toRadians(degrees.toDouble())
        return ScenePoint(facing * sin(radians).toFloat(), -cos(radians).toFloat())
    }

    /** Converts a (forward, down) vector in the body's frame to scene units. */
    fun body(forward: Float, down: Float) = ScenePoint(facing * forward, down)

    /** An arm posed by segment angles; suits single-joint moves like curls. */
    fun armAngles(upper: Float, forearm: Float): Limb {
        val elbow = neck + direction(upper) * UPPER_ARM
        return Limb(neck, elbow, elbow + direction(forearm) * FOREARM)
    }

    /** An arm reaching for a hand position relative to the shoulder, elbow bending toward [elbow]. */
    fun armReach(forward: Float, down: Float, elbow: ScenePoint): Limb =
        twoBone(neck, neck + body(forward, down), UPPER_ARM, FOREARM, pole = body(elbow.x, elbow.y))

    fun frame(pulley: PulleyHeight, nearFootX: Float, farFootX: Float, arm: Limb, stackLift: Float): CableFrame {
        val nearLeg = leg(nearFootX)
        val farLeg = leg(farFootX)
        // The far arm sits just behind the near one, as if the body were turned slightly toward us.
        val depth = body(-1.5f, -0.7f)
        return CableFrame(
            frontView = false,
            head = head,
            neck = neck,
            pelvis = pelvis,
            nearArm = arm,
            farArm = Limb(arm.root + depth, arm.joint + depth, arm.end + depth),
            nearLeg = nearLeg,
            farLeg = farLeg,
            nearToe = nearLeg.end + body(4.2f, 1.6f),
            farToe = farLeg.end + body(4.2f, 1.6f),
            pulley = ScenePoint(CableScene.PULLEY_X, CableScene.pulleyY(pulley)),
            handle = arm.end,
            stackLift = stackLift,
        )
    }

    private fun leg(footX: Float) =
        twoBone(pelvis, ScenePoint(footX, ANKLE_Y), THIGH, SHIN, pole = body(1f, 0f))
}

/** Direction of an arm raised [degrees] out toward the viewer's left from hanging straight down. */
private fun sideways(degrees: Float): ScenePoint {
    val radians = Math.toRadians(degrees.toDouble())
    return ScenePoint(-sin(radians).toFloat(), cos(radians).toFloat())
}

/**
 * Two-bone inverse kinematics: places the middle joint so the limb reaches [target], bending
 * toward [pole]. Targets out of reach get a straight limb pointing at them.
 */
internal fun twoBone(root: ScenePoint, target: ScenePoint, upper: Float, lower: Float, pole: ScenePoint): Limb {
    val delta = target - root
    val distance = delta.length
    val direction = if (distance < 1e-4f) ScenePoint(0f, 1f) else delta * (1f / distance)
    val reach = distance.coerceIn(abs(upper - lower) + 0.01f, upper + lower - 0.01f)
    val along = (upper * upper - lower * lower + reach * reach) / (2f * reach)
    val offset = sqrt(max(upper * upper - along * along, 0f))
    var normal = ScenePoint(-direction.y, direction.x)
    if (normal dot pole < 0f) normal = -normal
    return Limb(root, root + direction * along + normal * offset, root + direction * reach)
}

private fun lerp(start: Float, end: Float, t: Float) = start + (end - start) * t
