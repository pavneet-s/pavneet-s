package com.pavneet.workoutcycle.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pavneet.workoutcycle.ui.history.HistoryRoute
import com.pavneet.workoutcycle.ui.manage.ManageCycleRoute
import com.pavneet.workoutcycle.ui.settings.SettingsRoute
import com.pavneet.workoutcycle.ui.workout.ActiveWorkoutRoute
import kotlinx.serialization.Serializable

@Serializable
data object ActiveWorkoutDestination

@Serializable
data object ManageCycleDestination

@Serializable
data object HistoryDestination

@Serializable
data object SettingsDestination

@Composable
fun WorkoutNavHost(navController: NavHostController = rememberNavController()) {
    // navigateUp is a no-op on the start destination, so a double tap on back is safe.
    val back: () -> Unit = { navController.navigateUp() }
    NavHost(navController = navController, startDestination = ActiveWorkoutDestination) {
        composable<ActiveWorkoutDestination> {
            ActiveWorkoutRoute(
                onManageCycle = { navController.navigate(ManageCycleDestination) },
                onOpenHistory = { navController.navigate(HistoryDestination) },
                onOpenSettings = { navController.navigate(SettingsDestination) },
            )
        }
        composable<ManageCycleDestination> { ManageCycleRoute(onBack = back) }
        composable<HistoryDestination> { HistoryRoute(onBack = back) }
        composable<SettingsDestination> { SettingsRoute(onBack = back) }
    }
}
