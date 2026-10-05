package com.pavneet.workoutcycle.session

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pavneet.workoutcycle.WorkoutCycleApp
import kotlinx.coroutines.launch

/**
 * Wakes the app when a rest ends, even if it's in the background or the screen is off.
 * Uses an exact alarm when allowed (rests are short, so a late alert would be useless).
 */
class RestAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(endsAt: Long) {
        val intent = pendingIntent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        }
    }

    fun cancel() = alarmManager.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_REST_ALARM,
        Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_REST_ALARM = 100
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WorkoutCycleApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.workoutController.onRestAlarm()
            } finally {
                pending.finish()
            }
        }
    }
}
