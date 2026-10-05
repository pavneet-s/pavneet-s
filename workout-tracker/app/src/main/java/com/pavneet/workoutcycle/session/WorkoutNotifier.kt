package com.pavneet.workoutcycle.session

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pavneet.workoutcycle.MainActivity
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.LoggedSet
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.domain.formatWeight
import com.pavneet.workoutcycle.ui.cable.labelRes
import kotlinx.coroutines.launch

/** What the workout notification shows; rebuilt whenever the workout changes. */
data class WorkoutNotificationState(
    val exerciseId: Long,
    val exerciseName: String,
    /** The muscle group's cable exercise, named so the watch shows which one is up. */
    val movement: CableMovement?,
    val nextName: String?,
    /** The set the Done button will repeat, if this exercise has been logged before. */
    val lastSet: LoggedSet?,
    /** Non-null while resting. */
    val restEndsAt: Long?,
    /** When the notification should disappear if nothing else happens. */
    val hideAt: Long,
)

/**
 * Posts the workout notification and the rest-over alert. The workout notification is a normal
 * (not ongoing) notification on purpose: Wear OS mirrors those to a paired watch, Done button
 * included, which is how a Galaxy Watch can finish a set.
 */
class WorkoutNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val workout = NotificationChannelCompat.Builder(CHANNEL_WORKOUT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.channel_workout))
            .setDescription(context.getString(R.string.channel_workout_description))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        val restOver = NotificationChannelCompat.Builder(CHANNEL_REST_OVER, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.channel_rest_over))
            .setDescription(context.getString(R.string.channel_rest_over_description))
            .setVibrationEnabled(true)
            .setVibrationPattern(longArrayOf(0, 400, 200, 400))
            .build()
        manager.createNotificationChannelsCompat(listOf(workout, restOver))
    }

    /** False when the user turned notifications off or hasn't granted the permission. */
    val canNotify: Boolean
        get() = manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )

    @SuppressLint("MissingPermission") // checked by canNotify
    fun showWorkout(state: WorkoutNotificationState, now: Long) {
        if (!canNotify) return
        val builder = NotificationCompat.Builder(context, CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter((state.hideAt - now).coerceAtLeast(1_000L))
        val lastSet = state.lastSet?.let(::describe)?.let { context.getString(R.string.notification_last_set, it) }
        val movement = state.movement?.let { context.getString(it.labelRes) }
        if (state.restEndsAt != null) {
            builder
                .setContentTitle(context.getString(R.string.notification_resting, state.exerciseName))
                .setContentText(listOfNotNull(movement, lastSet ?: context.getString(R.string.notification_rest_hint)).joinToString(" · "))
                .setWhen(state.restEndsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(R.drawable.ic_skip_next, context.getString(R.string.skip_rest), actionIntent(ACTION_SKIP_REST))
                .addAction(R.drawable.ic_add, context.getString(R.string.add_rest_time), actionIntent(ACTION_EXTEND_REST))
        } else {
            val lines = listOfNotNull(
                listOfNotNull(movement, lastSet).joinToString(" · ").ifEmpty { null },
                state.nextName?.let { context.getString(R.string.notification_up_next, it) },
            )
            builder
                .setContentTitle(context.getString(R.string.notification_now, state.exerciseName))
                .setContentText(lines.joinToString(" · "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setShowWhen(false)
                .addAction(
                    R.drawable.ic_check,
                    context.getString(R.string.mark_done),
                    actionIntent(ACTION_DONE) { putExtra(EXTRA_EXERCISE_ID, state.exerciseId) },
                )
        }
        manager.notify(ID_WORKOUT, builder.build())
    }

    fun hideWorkout() = manager.cancel(ID_WORKOUT)

    @SuppressLint("MissingPermission") // checked by canNotify
    fun showRestOver(nextExerciseName: String) {
        if (!canNotify) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REST_OVER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_rest_over))
            .setContentText(context.getString(R.string.notification_rest_over_next, nextExerciseName))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setTimeoutAfter(REST_OVER_TIMEOUT_MS)
            .build()
        manager.notify(ID_REST_OVER, notification)
    }

    fun cancelRestOver() = manager.cancel(ID_REST_OVER)

    /** "35 lb × 12 reps", or `null` if the set was logged without either. */
    private fun describe(set: LoggedSet): String? {
        val weight = set.weight?.let { weight -> set.unit?.let { formatWeightWithUnit(context, weight, it) } }
        val reps = set.reps?.let { context.resources.getQuantityString(R.plurals.reps_count, it, it) }
        return listOfNotNull(weight, reps).joinToString(" × ").ifEmpty { null }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, extras: Intent.() -> Unit = {}): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, WorkoutActionReceiver::class.java).setAction(action).apply(extras),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_WORKOUT = "workout"
        const val CHANNEL_REST_OVER = "rest_over"
        const val ACTION_DONE = "com.pavneet.workoutcycle.action.DONE"
        const val ACTION_SKIP_REST = "com.pavneet.workoutcycle.action.SKIP_REST"
        const val ACTION_EXTEND_REST = "com.pavneet.workoutcycle.action.EXTEND_REST"
        const val EXTRA_EXERCISE_ID = "exercise_id"
        private const val ID_WORKOUT = 1
        private const val ID_REST_OVER = 2
        private const val REQUEST_OPEN_APP = 200
        private const val REST_OVER_TIMEOUT_MS = 2 * 60 * 1_000L
    }
}

/** "35 lb", "15 kg" or "Plate 7". */
fun formatWeightWithUnit(context: Context, weight: Double, unit: WeightUnit): String = context.getString(
    when (unit) {
        WeightUnit.KG -> R.string.weight_kg
        WeightUnit.LB -> R.string.weight_lb
        WeightUnit.PLATE -> R.string.weight_plate
    },
    formatWeight(weight),
)

/** Handles the notification's buttons, whether tapped on the phone or on a paired watch. */
class WorkoutActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WorkoutCycleApp).container
        val controller = container.workoutController
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    WorkoutNotifier.ACTION_DONE -> {
                        val exerciseId = intent.getLongExtra(WorkoutNotifier.EXTRA_EXERCISE_ID, -1L)
                        if (exerciseId != -1L) controller.completeSet(exerciseId, entry = null)
                    }
                    WorkoutNotifier.ACTION_SKIP_REST -> controller.skipRest()
                    WorkoutNotifier.ACTION_EXTEND_REST -> controller.extendRest()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
