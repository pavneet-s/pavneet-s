package com.pavneet.workoutcycle

import android.app.Application
import android.content.Context
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.data.local.WorkoutDatabase
import com.pavneet.workoutcycle.session.RestAlarmScheduler
import com.pavneet.workoutcycle.session.WorkoutController
import com.pavneet.workoutcycle.session.WorkoutNotificationSync
import com.pavneet.workoutcycle.session.WorkoutNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WorkoutCycleApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.workoutNotifier.createChannels()
        container.notificationSync.start()
    }
}

/**
 * Manual dependency container. A handful of app-wide objects don't justify Hilt yet; if the app
 * grows much further, swap this for Hilt modules.
 */
class AppContainer(context: Context) {
    /** Outlives screens, for work started by notifications, a watch or the rest alarm. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = WorkoutDatabase.build(context)
    val workoutRepository = WorkoutRepository(database)
    val workoutNotifier = WorkoutNotifier(context)
    val workoutController = WorkoutController(workoutRepository, RestAlarmScheduler(context), workoutNotifier)
    val notificationSync = WorkoutNotificationSync(workoutRepository, workoutController, workoutNotifier, appScope)
}
