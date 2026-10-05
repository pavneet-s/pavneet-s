package com.pavneet.workoutcycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pavneet.workoutcycle.ui.WorkoutNavHost
import com.pavneet.workoutcycle.ui.theme.WorkoutCycleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutCycleTheme {
                WorkoutNavHost()
            }
        }
    }
}
