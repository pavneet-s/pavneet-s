package com.pavneet.workoutcycle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.pavneet.workoutcycle.ui.WorkoutNavHost
import com.pavneet.workoutcycle.ui.theme.WorkoutCycleTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutCycleTheme {
                // Lets the emulator test find tagged views, such as the form-tip bubble.
                Box(Modifier.semantics { testTagsAsResourceId = true }) {
                    WorkoutNavHost()
                }
            }
        }
    }
}
