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

    /** Every pose stays right of this, so the strip to its left is free for overlays. */
    const val CLEAR_LEFT = 20f

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
        CableMovement.CHEST_FLY -> fly(t, PulleyHeight.CHEST, startElevation = -14f, endElevation = -18f)
        CableMovement.HIGH_TO_LOW_FLY -> fly(t, PulleyHeight.HIGH, startElevation = 36f, endElevation = -40f)
        CableMovement.LOW_TO_HIGH_FLY -> fly(t, PulleyHeight.LOW, startElevation = -55f, endElevation = 0f, endAzimuth = 120f)
        CableMovement.ROW -> row(t)
        CableMovement.LAT_PULLDOWN -> latPulldown(t)
        CableMovement.STRAIGHT_ARM_PULLDOWN -> straightArmPulldown(t)
        CableMovement.LATERAL_RAISE -> lateralRaise(t)
        CableMovement.FRONT_RAISE -> frontRaise(t)
        CableMovement.FACE_PULL -> facePull(t)
        CableMovement.TRICEPS_PUSHDOWN -> tricepsPushdown(t)
        CableMovement.OVERHEAD_TRICEPS_EXTENSION -> overheadTricepsExtension(t)
        CableMovement.TRICEPS_KICKBACK -> tricepsKickback(t)
        CableMovement.BICEPS_CURL -> bicepsCurl(t)
        CableMovement.HIGH_CABLE_CURL -> highCableCurl(t)
        CableMovement.BEHIND_BACK_CURL -> behindBackCurl(t)
        CableMovement.GLUTE_KICKBACK -> gluteKickback(t)
        CableMovement.PULL_THROUGH -> pullThrough(t)
        CableMovement.CABLE_CRUNCH -> cableCrunch(t)
        CableMovement.WOODCHOPPER -> woodchopper(t)
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
        val pose = FrontPose(centerX = 58f)
        val raise = lerp(-14f, 86f, t)
        val elbow = pose.leftShoulder + sideways(raise) * UPPER_ARM
        val workingArm = Limb(pose.leftShoulder, elbow, elbow + sideways(raise - 8f) * FOREARM)
        // The other hand steadies the body on the machine.
        val supportArm = twoBone(
            root = pose.rightShoulder,
            target = ScenePoint(CableScene.COLUMN_LEFT - 0.8f, 53f),
            upper = UPPER_ARM,
            lower = FOREARM,
            pole = ScenePoint(0.2f, 1f),
        )
        return pose.frame(workingArm, supportArm, PulleyHeight.LOW, stackLift = t)
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

    // Seen from the front: the near arm sweeps from out toward the pulley to across the chest.
    private fun fly(
        t: Float,
        pulley: PulleyHeight,
        startElevation: Float,
        endElevation: Float,
        endAzimuth: Float = 112f,
    ): CableFrame {
        val pose = FrontPose(centerX = 44f)
        // Pointing straight at us while level, the arm would all but vanish from this angle. So a
        // fly from high drops first and then sweeps across, and one from low sweeps, then rises.
        val early = 1f - (1f - t) * (1f - t)
        val late = t * t
        val (sweep, rise) = when {
            startElevation > 0f -> t to early
            startElevation < -30f -> early to late
            else -> t to t
        }
        val workingArm = swungArm(
            shoulder = pose.rightShoulder,
            azimuth = lerp(-10f, endAzimuth, sweep),
            elevation = lerp(startElevation, endElevation, rise),
            elbowBend = 16f,
        )
        return pose.frame(workingArm, pose.handOnHip(), pulley, stackLift = t)
    }

    // Kneeling facing a high pulley, pulling the handles from overhead down to the upper chest.
    private fun latPulldown(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(54f, KNEELING_PELVIS_Y), lean = lerp(-2f, -12f, t))
        val arm = pose.armReach(forward = lerp(6f, 4f, t), down = lerp(-21.2f, 1f, t), elbow = ScenePoint(-0.7f, 1f))
        return pose.kneelingFrame(PulleyHeight.HIGH, arm, stackLift = t)
    }

    // Back to a low pulley, raising a straight arm forward to shoulder height.
    private fun frontRaise(t: Float): CableFrame {
        val pose = SidePose(facing = AWAY, pelvis = ScenePoint(60f, 62.5f), lean = 4f)
        val upper = lerp(172f, 90f, t)
        val arm = pose.armAngles(upper = upper, forearm = upper - 6f)
        return pose.frame(PulleyHeight.LOW, nearFootX = 56f, farFootX = 63f, arm = arm, stackLift = t)
    }

    // Hinged over facing a low pulley, upper arm pinned back, straightening the elbow.
    private fun tricepsKickback(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(45f, 63f), lean = 55f)
        val arm = pose.armAngles(upper = 248f, forearm = lerp(178f, 251f, t))
        return pose.frame(PulleyHeight.LOW, nearFootX = 49f, farFootX = 42f, arm = arm, stackLift = t)
    }

    // Seen from the front: the near arm held out toward a high pulley, curling the hand to the head.
    private fun highCableCurl(t: Float): CableFrame {
        val pose = FrontPose(centerX = 44f)
        val shoulder = pose.rightShoulder
        val elbow = shoulder + raised(22f) * UPPER_ARM
        val workingArm = Limb(shoulder, elbow, elbow + raised(lerp(28f, 150f, t)) * FOREARM)
        return pose.frame(workingArm, pose.hangingArm(), PulleyHeight.HIGH, stackLift = t)
    }

    // Back to a low pulley, the arm trailing behind the body, curling up from full stretch.
    private fun behindBackCurl(t: Float): CableFrame {
        val pose = SidePose(facing = AWAY, pelvis = ScenePoint(57f, 62.5f), lean = 10f)
        val arm = pose.armAngles(upper = 200f, forearm = lerp(204f, 42f, t))
        return pose.frame(PulleyHeight.LOW, nearFootX = 50f, farFootX = 64f, arm = arm, stackLift = t)
    }

    // Holding the machine, an ankle strap on the near leg, kicking the leg back.
    private fun gluteKickback(t: Float): CableFrame {
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(54f, 60.5f), lean = 28f)
        val grip = twoBone(pose.neck, ScenePoint(CableScene.COLUMN_LEFT - 1f, 50f), UPPER_ARM, FOREARM, pole = ScenePoint(0f, 1f))
        val thigh = lerp(176f, 226f, t)
        val shin = thigh + lerp(22f, 6f, t)
        val knee = pose.pelvis + pose.direction(thigh) * THIGH
        val workingLeg = Limb(pose.pelvis, knee, knee + pose.direction(shin) * SHIN)
        return pose.frame(
            PulleyHeight.LOW,
            arm = grip,
            stackLift = t,
            nearLeg = workingLeg,
            farLeg = pose.leg(footX = 57f),
            // A flexed foot, pointing down from the raised ankle.
            nearToe = workingLeg.end + pose.direction(shin - 90f) * 3.6f,
            handle = workingLeg.end,
        )
    }

    // Back to a low pulley, a rope between the legs: hinge forward, then drive the hips through.
    private fun pullThrough(t: Float): CableFrame {
        val pose = SidePose(
            facing = AWAY,
            pelvis = ScenePoint(lerp(63f, 59f, t), lerp(66f, 62.5f, t)),
            lean = lerp(70f, 6f, t),
        )
        val upper = lerp(213f, 172f, t)
        val arm = pose.armAngles(upper = upper, forearm = upper + lerp(4f, -4f, t))
        return pose.frame(PulleyHeight.LOW, nearFootX = 57f, farFootX = 61f, arm = arm, stackLift = t)
    }

    // Kneeling facing a high pulley, rope at the forehead, curling the ribs down toward the hips.
    private fun cableCrunch(t: Float): CableFrame {
        val lean = lerp(15f, 78f, t)
        // A curling spine brings the head down toward the knees: drawn as a shorter torso.
        val pose = SidePose(facing = TOWARD, pelvis = ScenePoint(42f, KNEELING_PELVIS_Y), lean = lean, torso = lerp(TORSO, 16.5f, t))
        val hands = pose.head + pose.direction(lean + 90f) * 3f
        val arm = twoBone(pose.neck, hands, UPPER_ARM, FOREARM, pole = pose.direction(lean + 120f))
        return pose.kneelingFrame(PulleyHeight.HIGH, arm, stackLift = t)
    }

    // Seen from the front: both hands on one handle, chopping from the high pulley down across the body.
    private fun woodchopper(t: Float): CableFrame {
        // The torso turns toward the machine, then away, so the shoulders look narrower at each end.
        val pose = FrontPose(centerX = 46f, tilt = lerp(3f, -3f, t), stance = 8f, shoulderWidth = lerp(3.8f, 6f, bump(t)))
        val hands = ScenePoint(lerp(60f, 39f, t), lerp(33f, 62f, t)) + ScenePoint(-3f, -2f) * bump(t)
        val nearArm = twoBone(pose.rightShoulder, hands, UPPER_ARM, FOREARM, pole = ScenePoint(1f, 0.4f))
        val farArm = twoBone(pose.leftShoulder, hands, UPPER_ARM, FOREARM, pole = ScenePoint(-0.3f, 1f))
        return pose.frame(nearArm, farArm, PulleyHeight.HIGH, stackLift = t)
    }
}

private const val TOWARD = 1f
private const val AWAY = -1f

/** Kneeling upright: the hips a thigh's length above the knees on the floor. */
private const val KNEELING_PELVIS_Y = ANKLE_Y - THIGH

/**
 * Side view of a figure facing toward (+1, right) or away from (-1, left) the machine.
 * Angles are absolute: 0° points up, 90° forward and 180° down.
 */
private class SidePose(private val facing: Float, val pelvis: ScenePoint, lean: Float, torso: Float = TORSO) {
    val neck = pelvis + direction(lean) * torso
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

    fun frame(pulley: PulleyHeight, nearFootX: Float, farFootX: Float, arm: Limb, stackLift: Float): CableFrame =
        frame(pulley, arm, stackLift, nearLeg = leg(nearFootX), farLeg = leg(farFootX))

    fun frame(
        pulley: PulleyHeight,
        arm: Limb,
        stackLift: Float,
        nearLeg: Limb,
        farLeg: Limb,
        nearToe: ScenePoint = nearLeg.end + body(4.2f, 1.6f),
        farToe: ScenePoint = farLeg.end + body(4.2f, 1.6f),
        handle: ScenePoint = arm.end,
    ): CableFrame {
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
            nearToe = nearToe,
            farToe = farToe,
            pulley = ScenePoint(CableScene.PULLEY_X, CableScene.pulleyY(pulley)),
            handle = handle,
            stackLift = stackLift,
        )
    }

    /** Kneeling on both knees with the shins flat on the floor behind; the pelvis sits at [KNEELING_PELVIS_Y]. */
    fun kneelingFrame(pulley: PulleyHeight, arm: Limb, stackLift: Float): CableFrame {
        val nearLeg = kneelingLeg(thighTilt = 0f)
        val farLeg = kneelingLeg(thighTilt = -7f)
        return frame(
            pulley,
            arm,
            stackLift,
            nearLeg = nearLeg,
            farLeg = farLeg,
            nearToe = nearLeg.end + body(-3.6f, 0.4f),
            farToe = farLeg.end + body(-3.6f, 0.4f),
        )
    }

    /** A standing leg with the foot planted at [footX]. */
    fun leg(footX: Float) =
        twoBone(pelvis, ScenePoint(footX, ANKLE_Y), THIGH, SHIN, pole = body(1f, 0f))

    // The thigh hangs [thighTilt] degrees forward of straight down; the shin runs back along the floor.
    private fun kneelingLeg(thighTilt: Float): Limb {
        val knee = pelvis + direction(180f - thighTilt) * THIGH
        val drop = (ANKLE_Y - knee.y).coerceIn(0f, SHIN)
        val ankle = ScenePoint(knee.x - facing * sqrt(SHIN * SHIN - drop * drop), ANKLE_Y)
        return Limb(pelvis, knee, ankle)
    }
}

/**
 * Front view of a figure standing side-on to the machine, which is to its left (our right).
 * [tilt] leans the torso toward the machine (positive) or away, in scene units at the neck.
 */
private class FrontPose(centerX: Float, tilt: Float = 0f, stance: Float = 6.5f, shoulderWidth: Float = 6f) {
    val pelvis = ScenePoint(centerX, 60.2f)
    val neck = ScenePoint(centerX + tilt, pelvis.y - sqrt(TORSO * TORSO - tilt * tilt))
    val head = neck + (neck - pelvis) * ((NECK + CableScene.HEAD_RADIUS) / TORSO)

    /** On our left, away from the machine. */
    val leftShoulder = ScenePoint(neck.x - shoulderWidth, neck.y + 1.4f)

    /** On our right, toward the machine. */
    val rightShoulder = ScenePoint(neck.x + shoulderWidth, neck.y + 1.4f)

    private val leftLeg = twoBone(
        ScenePoint(centerX - 3.6f, pelvis.y), ScenePoint(centerX - stance, ANKLE_Y), THIGH, SHIN, ScenePoint(-1f, 0f),
    )
    private val rightLeg = twoBone(
        ScenePoint(centerX + 3.6f, pelvis.y), ScenePoint(centerX + stance, ANKLE_Y), THIGH, SHIN, ScenePoint(1f, 0f),
    )

    /** The left hand resting on the left hip, elbow out. */
    fun handOnHip(): Limb =
        twoBone(leftShoulder, ScenePoint(pelvis.x - 7.2f, pelvis.y - 2.6f), UPPER_ARM, FOREARM, pole = ScenePoint(-1f, 0f))

    /** The left arm hanging relaxed at the side. */
    fun hangingArm(): Limb =
        twoBone(leftShoulder, leftShoulder + ScenePoint(-2.6f, 21.6f), UPPER_ARM, FOREARM, pole = ScenePoint(-1f, 0.2f))

    /** [workingArm] is drawn in front of the body, so it reads where it crosses the torso. */
    fun frame(workingArm: Limb, otherArm: Limb, pulley: PulleyHeight, stackLift: Float): CableFrame = CableFrame(
        frontView = true,
        head = head,
        neck = neck,
        pelvis = pelvis,
        nearArm = workingArm,
        farArm = otherArm,
        nearLeg = leftLeg,
        farLeg = rightLeg,
        nearToe = leftLeg.end + ScenePoint(-3.4f, 1.6f),
        farToe = rightLeg.end + ScenePoint(3.4f, 1.6f),
        pulley = ScenePoint(CableScene.PULLEY_X, CableScene.pulleyY(pulley)),
        handle = workingArm.end,
        stackLift = stackLift,
    )
}

/** Direction of an arm raised [degrees] out toward the viewer's left from hanging straight down. */
private fun sideways(degrees: Float): ScenePoint {
    val radians = Math.toRadians(degrees.toDouble())
    return ScenePoint(-sin(radians).toFloat(), cos(radians).toFloat())
}

/** Front view: direction [degrees] above pointing straight at the machine (our right). */
private fun raised(degrees: Float): ScenePoint {
    val radians = Math.toRadians(degrees.toDouble())
    return ScenePoint(cos(radians).toFloat(), -sin(radians).toFloat())
}

/**
 * Front view of an arm swinging out of the picture: [azimuth] turns it from pointing at the
 * machine (0°) toward us (90°) and across the body; [elevation] raises it above horizontal.
 * The parts pointing at us look shorter, as they would from the front.
 */
private fun swungArm(shoulder: ScenePoint, azimuth: Float, elevation: Float, elbowBend: Float): Limb {
    fun projected(azimuthDegrees: Float): ScenePoint {
        val a = Math.toRadians(azimuthDegrees.toDouble())
        val e = Math.toRadians(elevation.toDouble())
        return ScenePoint((cos(e) * cos(a)).toFloat(), (-sin(e)).toFloat())
    }
    // A soft elbow: the upper arm trails the sweep and the forearm leads it.
    val elbow = shoulder + projected(azimuth - elbowBend / 2f) * UPPER_ARM
    return Limb(shoulder, elbow, elbow + projected(azimuth + elbowBend / 2f) * FOREARM)
}

/** 0 at both ends of the rep, 1 in the middle, for paths that arc instead of running straight. */
private fun bump(t: Float) = 4f * t * (1f - t)

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
