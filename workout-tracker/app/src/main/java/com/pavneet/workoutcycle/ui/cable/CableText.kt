package com.pavneet.workoutcycle.ui.cable

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.MuscleGroup
import com.pavneet.workoutcycle.domain.PulleyHeight

@get:StringRes
val CableMovement.labelRes: Int
    get() = when (this) {
        CableMovement.CHEST_PRESS -> R.string.movement_chest_press
        CableMovement.CHEST_FLY -> R.string.movement_chest_fly
        CableMovement.HIGH_TO_LOW_FLY -> R.string.movement_high_to_low_fly
        CableMovement.LOW_TO_HIGH_FLY -> R.string.movement_low_to_high_fly
        CableMovement.ROW -> R.string.movement_row
        CableMovement.LAT_PULLDOWN -> R.string.movement_lat_pulldown
        CableMovement.STRAIGHT_ARM_PULLDOWN -> R.string.movement_straight_arm_pulldown
        CableMovement.LATERAL_RAISE -> R.string.movement_lateral_raise
        CableMovement.FRONT_RAISE -> R.string.movement_front_raise
        CableMovement.FACE_PULL -> R.string.movement_face_pull
        CableMovement.TRICEPS_PUSHDOWN -> R.string.movement_triceps_pushdown
        CableMovement.OVERHEAD_TRICEPS_EXTENSION -> R.string.movement_overhead_triceps_extension
        CableMovement.TRICEPS_KICKBACK -> R.string.movement_triceps_kickback
        CableMovement.BICEPS_CURL -> R.string.movement_biceps_curl
        CableMovement.HIGH_CABLE_CURL -> R.string.movement_high_cable_curl
        CableMovement.BEHIND_BACK_CURL -> R.string.movement_behind_back_curl
        CableMovement.GLUTE_KICKBACK -> R.string.movement_glute_kickback
        CableMovement.PULL_THROUGH -> R.string.movement_pull_through
        CableMovement.CABLE_CRUNCH -> R.string.movement_cable_crunch
        CableMovement.WOODCHOPPER -> R.string.movement_woodchopper
    }

/** Common mistakes with this exercise, worded as the figure talking to you. */
@get:ArrayRes
val CableMovement.tipsRes: Int
    get() = when (this) {
        CableMovement.CHEST_PRESS -> R.array.tips_chest_press
        CableMovement.CHEST_FLY -> R.array.tips_chest_fly
        CableMovement.HIGH_TO_LOW_FLY -> R.array.tips_high_to_low_fly
        CableMovement.LOW_TO_HIGH_FLY -> R.array.tips_low_to_high_fly
        CableMovement.ROW -> R.array.tips_row
        CableMovement.LAT_PULLDOWN -> R.array.tips_lat_pulldown
        CableMovement.STRAIGHT_ARM_PULLDOWN -> R.array.tips_straight_arm_pulldown
        CableMovement.LATERAL_RAISE -> R.array.tips_lateral_raise
        CableMovement.FRONT_RAISE -> R.array.tips_front_raise
        CableMovement.FACE_PULL -> R.array.tips_face_pull
        CableMovement.TRICEPS_PUSHDOWN -> R.array.tips_triceps_pushdown
        CableMovement.OVERHEAD_TRICEPS_EXTENSION -> R.array.tips_overhead_triceps_extension
        CableMovement.TRICEPS_KICKBACK -> R.array.tips_triceps_kickback
        CableMovement.BICEPS_CURL -> R.array.tips_biceps_curl
        CableMovement.HIGH_CABLE_CURL -> R.array.tips_high_cable_curl
        CableMovement.BEHIND_BACK_CURL -> R.array.tips_behind_back_curl
        CableMovement.GLUTE_KICKBACK -> R.array.tips_glute_kickback
        CableMovement.PULL_THROUGH -> R.array.tips_pull_through
        CableMovement.CABLE_CRUNCH -> R.array.tips_cable_crunch
        CableMovement.WOODCHOPPER -> R.array.tips_woodchopper
    }

@get:StringRes
val MuscleGroup.labelRes: Int
    get() = when (this) {
        MuscleGroup.CHEST -> R.string.muscle_chest
        MuscleGroup.BACK -> R.string.muscle_back
        MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
        MuscleGroup.TRICEPS -> R.string.muscle_triceps
        MuscleGroup.BICEPS -> R.string.muscle_biceps
        MuscleGroup.LEGS -> R.string.muscle_legs
        MuscleGroup.CORE -> R.string.muscle_core
    }

@get:StringRes
val PulleyHeight.labelRes: Int
    get() = when (this) {
        PulleyHeight.HIGH -> R.string.pulley_high
        PulleyHeight.CHEST -> R.string.pulley_chest
        PulleyHeight.LOW -> R.string.pulley_low
    }
