package com.pavneet.workoutcycle.ui

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_CLICK
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
import android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
import android.os.VibrationEffect.Composition.PRIMITIVE_THUD
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** One step of a [Haptic]: a vibration primitive, its strength (0-1) and the pause before it. */
class HapticPrimitive(val id: Int, val scale: Float, val delayMillis: Int = 0)

/**
 * A distinct feel for each kind of button, so they can be told apart without looking: a solid
 * double thump for Done, rising and falling ticks for + and -, a double tap to skip a rest.
 *
 * @property primitives the crisp version, for phones whose motor supports these primitives.
 * @property timings the fallback: alternating off and on milliseconds, starting with off.
 * @property amplitudes the fallback's strength (1-255) for each step, where the phone can vary it.
 */
enum class Haptic(val primitives: List<HapticPrimitive>, val timings: LongArray, val amplitudes: IntArray) {
    DONE(
        listOf(HapticPrimitive(PRIMITIVE_THUD, 1f), HapticPrimitive(PRIMITIVE_CLICK, 1f, delayMillis = 80)),
        longArrayOf(0, 40, 70, 25),
        intArrayOf(0, 255, 0, 200),
    ),
    WEIGHT_UP(listOf(HapticPrimitive(PRIMITIVE_QUICK_RISE, 0.8f)), longArrayOf(0, 10, 30, 16), intArrayOf(0, 80, 0, 200)),
    WEIGHT_DOWN(listOf(HapticPrimitive(PRIMITIVE_QUICK_FALL, 0.8f)), longArrayOf(0, 16, 30, 10), intArrayOf(0, 200, 0, 80)),
    REPS_UP(listOf(HapticPrimitive(PRIMITIVE_TICK, 1f)), longArrayOf(0, 12), intArrayOf(0, 220)),
    REPS_DOWN(listOf(HapticPrimitive(PRIMITIVE_LOW_TICK, 1f)), longArrayOf(0, 12), intArrayOf(0, 80)),
    SKIP_REST(
        listOf(HapticPrimitive(PRIMITIVE_CLICK, 0.8f), HapticPrimitive(PRIMITIVE_CLICK, 0.8f, delayMillis = 90)),
        longArrayOf(0, 20, 90, 20),
        intArrayOf(0, 200, 0, 200),
    ),
    ADD_REST_TIME(
        listOf(
            HapticPrimitive(PRIMITIVE_TICK, 0.7f),
            HapticPrimitive(PRIMITIVE_TICK, 0.7f, delayMillis = 60),
            HapticPrimitive(PRIMITIVE_TICK, 0.7f, delayMillis = 60),
        ),
        longArrayOf(0, 10, 60, 10, 60, 10),
        intArrayOf(0, 150, 0, 150, 0, 150),
    ),
    UNDO(
        listOf(HapticPrimitive(PRIMITIVE_QUICK_FALL, 1f), HapticPrimitive(PRIMITIVE_LOW_TICK, 0.6f, delayMillis = 50)),
        longArrayOf(0, 30, 40, 30),
        intArrayOf(0, 200, 0, 70),
    ),
    SWIPE(listOf(HapticPrimitive(PRIMITIVE_TICK, 0.5f)), longArrayOf(0, 8), intArrayOf(0, 120)),
    NAVIGATE(listOf(HapticPrimitive(PRIMITIVE_CLICK, 0.45f)), longArrayOf(0, 12), intArrayOf(0, 150)),
    SELECT(listOf(HapticPrimitive(PRIMITIVE_CLICK, 0.6f)), longArrayOf(0, 10), intArrayOf(0, 180)),
    TOGGLE_ON(
        listOf(HapticPrimitive(PRIMITIVE_TICK, 0.6f), HapticPrimitive(PRIMITIVE_CLICK, 0.8f, delayMillis = 50)),
        longArrayOf(0, 8, 50, 14),
        intArrayOf(0, 100, 0, 230),
    ),
    TOGGLE_OFF(
        listOf(HapticPrimitive(PRIMITIVE_CLICK, 0.8f), HapticPrimitive(PRIMITIVE_LOW_TICK, 0.6f, delayMillis = 50)),
        longArrayOf(0, 14, 50, 8),
        intArrayOf(0, 230, 0, 90),
    ),
    SAVE(
        listOf(HapticPrimitive(PRIMITIVE_CLICK, 0.7f), HapticPrimitive(PRIMITIVE_TICK, 0.8f, delayMillis = 70)),
        longArrayOf(0, 16, 70, 10),
        intArrayOf(0, 210, 0, 170),
    ),
    DELETE(listOf(HapticPrimitive(PRIMITIVE_THUD, 1f)), longArrayOf(0, 50), intArrayOf(0, 255)),
    ;

    companion object {
        fun toggle(on: Boolean) = if (on) TOGGLE_ON else TOGGLE_OFF
    }
}

/** Plays [Haptic]s as touch feedback, so they follow the phone's touch-vibration setting. */
class Haptics(private val view: View) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        view.context.getSystemService(Vibrator::class.java)
    }

    fun perform(haptic: Haptic) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() }
        if (vibrator == null) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return
        }
        val effect = effectFor(haptic, vibrator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Touch usage makes the system apply the user's touch-vibration strength, or skip it when off.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else if (touchFeedbackEnabled()) {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        }
    }

    private fun effectFor(haptic: Haptic, vibrator: Vibrator): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ids = haptic.primitives.map { it.id }.toIntArray()
            if (vibrator.areAllPrimitivesSupported(*ids)) {
                val composition = VibrationEffect.startComposition()
                haptic.primitives.forEach { composition.addPrimitive(it.id, it.scale, it.delayMillis) }
                return composition.compose()
            }
        }
        return if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(haptic.timings, haptic.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(haptic.timings, -1)
        }
    }

    private fun touchFeedbackEnabled(): Boolean =
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
