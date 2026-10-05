package com.pavneet.workoutcycle.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.WorkoutCycleApp
import com.pavneet.workoutcycle.data.WorkoutRepository
import com.pavneet.workoutcycle.domain.AppSettings
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.session.WorkoutController
import com.pavneet.workoutcycle.ui.unitLabelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: WorkoutRepository,
    private val controller: WorkoutController,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
            // Re-show the workout notification right away if it was just switched on.
            controller.markActive()
        }
    }

    fun onNotificationsMaybeChanged() = controller.markActive()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WorkoutCycleApp).container
                SettingsViewModel(container.workoutRepository, container.workoutController)
            }
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Re-check whenever the screen resumes, e.g. after coming back from system settings.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val notificationsAllowed = remember(lifecycleState) { notificationsAllowed(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationsMaybeChanged()
    }

    SettingsScreen(
        settings = settings,
        notificationsAllowed = notificationsAllowed,
        onBack = onBack,
        onChange = viewModel::update,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Blocked for the app or a channel: only the system screen can change that.
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        },
    )
}

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    notificationsAllowed: Boolean,
    onBack: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onAllowNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_rest_section))
            SwitchRow(
                title = stringResource(R.string.settings_rest_timer),
                subtitle = stringResource(R.string.settings_rest_timer_detail),
                checked = settings.restEnabled,
                onCheckedChange = { enabled -> onChange { it.copy(restEnabled = enabled) } },
            )
            if (settings.restEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSettings.REST_CHOICES_SECONDS.forEach { seconds ->
                        FilterChip(
                            selected = settings.restSeconds == seconds,
                            onClick = { onChange { it.copy(restSeconds = seconds) } },
                            label = { Text(restLabel(seconds)) },
                        )
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_weight_section))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = settings.weightUnit == unit,
                        onClick = { onChange { it.copy(weightUnit = unit) } },
                        label = { Text(stringResource(unit.unitLabelRes)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_weight_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_watch_section))
            SwitchRow(
                title = stringResource(R.string.settings_workout_notification),
                subtitle = stringResource(R.string.settings_workout_notification_detail),
                checked = settings.workoutNotification,
                onCheckedChange = { enabled -> onChange { it.copy(workoutNotification = enabled) } },
            )
            if (!notificationsAllowed) {
                Text(
                    text = stringResource(R.string.settings_notifications_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onAllowNotifications) {
                    Text(stringResource(R.string.settings_allow_notifications))
                }
            }
            Text(
                text = stringResource(R.string.settings_watch_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun restLabel(seconds: Int): String = when {
    seconds < 60 -> stringResource(R.string.duration_seconds, seconds)
    seconds % 60 == 0 -> stringResource(R.string.duration_minutes, seconds / 60)
    else -> stringResource(R.string.duration_minutes_seconds, seconds / 60, seconds % 60)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/** A whole-row toggle, so the label is part of the touch target. */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The row handles the toggle; the switch only shows it.
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}
