package com.pavneet.workoutcycle.data.local

import com.pavneet.workoutcycle.domain.AnimationSetting
import com.pavneet.workoutcycle.domain.CableMovement

/** How an [AnimationSetting] is stored in `exercises.animation`. */
internal object AnimationColumn {
    private const val OFF = "OFF"

    fun encode(setting: AnimationSetting): String? = when (setting) {
        AnimationSetting.Auto -> null
        AnimationSetting.Off -> OFF
        is AnimationSetting.Fixed -> setting.movement.name
    }

    // An unknown name (e.g. a movement removed in a later version) falls back to guessing.
    fun decode(value: String?): AnimationSetting = when (value) {
        null -> AnimationSetting.Auto
        OFF -> AnimationSetting.Off
        else -> CableMovement.entries.firstOrNull { it.name == value }
            ?.let { AnimationSetting.Fixed(it) }
            ?: AnimationSetting.Auto
    }
}
