package com.pavneet.workoutcycle.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pavneet.workoutcycle.ui.manage.ManageCycleRoute
import com.pavneet.workoutcycle.ui.workout.ActiveWorkoutRoute
import kotlinx.serialization.Serializable

@Serializable
data object ActiveWorkoutDestination

@Serializable
data object ManageCycleDestination

@Composable
fun WorkoutNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = ActiveWorkoutDestination) {
        composable<ActiveWorkoutDestination> {
            ActiveWorkoutRoute(onManageCycle = { navController.navigate(ManageCycleDestination) })
        }
        composable<ManageCycleDestination> {
            // navigateUp is a no-op on the start destination, so a double tap on back is safe.
            ManageCycleRoute(onBack = { navController.navigateUp() })
        }
    }
}
