package com.pavneet.workoutcycle.domain

import kotlin.math.roundToInt

/**
 * How weights are counted. [step] is one tap of the +/- buttons; [plateWeight] is what one
 * plate of the animated weight stack stands for.
 */
enum class WeightUnit(val step: Double, val plateWeight: Double) {
    KG(2.5, 5.0),
    LB(5.0, 10.0),

    /** The pin position on a numbered stack, e.g. "Plate 7". */
    PLATE(1.0, 1.0),
    ;

    /** One step up; an empty weight starts at one step. */
    fun increase(weight: Double?): Double = (weight ?: 0.0) + step

    /** One step down; reaching zero clears the weight. */
    fun decrease(weight: Double?): Double? = weight?.minus(step)?.takeIf { it > 0.0 }
}

/** 35.0 -> "35", 22.5 -> "22.5". */
fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()

/** What the user is about to log for a set. Either value may be left empty. */
data class SetEntry(val weight: Double? = null, val reps: Int? = null) {
    fun increaseReps() = copy(reps = (reps ?: (DEFAULT_REPS - 1)) + 1)
    fun decreaseReps() = copy(reps = reps?.minus(1)?.takeIf { it > 0 })

    companion object {
        /** First tap on + for reps jumps straight to a typical working set. */
        const val DEFAULT_REPS = 10
    }
}

/**
 * How much of the cable machine's weight stack a set lifts, for the animation: whole plates
 * plus a half-plate add-on for the step in between (5 lb or 2.5 kg).
 */
data class StackLoad(val plates: Int, val halfPlate: Boolean = false) {
    companion object {
        /** Plates in the animated stack; heavier sets lift all of them. */
        const val STACK_PLATES = 15

        /** No weight entered, so nothing on the pin. */
        val EMPTY = StackLoad(plates = 0)

        fun of(weight: Double?, unit: WeightUnit): StackLoad {
            if (weight == null || weight <= 0.0) return EMPTY
            val halfPlates = (weight / (unit.plateWeight / 2)).roundToInt().coerceIn(1, STACK_PLATES * 2 + 1)
            return StackLoad(plates = halfPlates / 2, halfPlate = halfPlates % 2 == 1)
        }
    }
}

/**
 * A completed set, as stored in the history. The name is kept even if the exercise is deleted.
 *
 * @property movement the cable exercise done for the muscle group, if it had one.
 */
data class LoggedSet(
    val id: Long,
    val exerciseId: Long?,
    val exerciseName: String,
    val weight: Double?,
    val unit: WeightUnit?,
    val reps: Int?,
    /** Epoch milliseconds. */
    val completedAt: Long,
    val movement: CableMovement? = null,
)

/** What a set is remembered by: its slot in the rotation and the cable exercise done for it. */
data class SetKey(val exerciseId: Long, val movement: CableMovement?)

enum class Attachment { ROPE, HANDLE, STRAIGHT_BAR, V_BAR, ANKLE_STRAP }

/** How the cable machine is set up for an exercise. */
data class MachineSetup(
    val attachment: Attachment? = null,
    /** Free text such as "Notch 12" or "Top". */
    val pulleyPosition: String = "",
    /** Where to stand and anything else worth remembering. */
    val note: String = "",
) {
    val isEmpty: Boolean get() = attachment == null && pulleyPosition.isBlank() && note.isBlank()
}

data class AppSettings(
    val restEnabled: Boolean = true,
    val restSeconds: Int = 60,
    val weightUnit: WeightUnit = WeightUnit.LB,
    /** Workout notification with a Done button, which also shows on a paired watch. */
    val workoutNotification: Boolean = true,
    /** Whether the app has already asked for notification permission once. */
    val notificationPrompted: Boolean = false,
) {
    companion object {
        val REST_CHOICES_SECONDS = listOf(30, 45, 60, 90, 120, 180)
    }
}
