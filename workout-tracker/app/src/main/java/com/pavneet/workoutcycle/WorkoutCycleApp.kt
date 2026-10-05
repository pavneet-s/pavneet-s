package com.pavneet.workoutcycle

import android.app.Application
import android.content.Context
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.data.local.WorkoutDatabase

class WorkoutCycleApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * Manual dependency container. Two screens and one repository don't justify Hilt yet; if the
 * app grows (sync, health data, more routines), swap this for Hilt modules.
 */
class AppContainer(context: Context) {
    private val database = WorkoutDatabase.build(context)
    val workoutRepository = WorkoutRepository(database)
}
