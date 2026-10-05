package com.pavneet.workoutcycle.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.ExerciseProgress
import com.pavneet.workoutcycle.domain.Session
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.domain.WorkoutHistory
import com.pavneet.workoutcycle.domain.formatWeight
import com.pavneet.workoutcycle.session.formatWeightWithUnit
import com.pavneet.workoutcycle.ui.relativeDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(uiState: HistoryUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            HistoryUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is HistoryUiState.Loaded -> if (uiState.history.isEmpty) {
                EmptyHistory(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                HistoryContent(uiState.history, PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ))
            }
        }
    }
}

@Composable
private fun HistoryContent(history: WorkoutHistory, contentPadding: PaddingValues) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = stringResource(R.string.stat_day_streak),
                    value = history.currentStreak.toString(),
                    detail = stringResource(R.string.stat_best_streak, history.longestStreak),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stat_this_week),
                    value = history.daysThisWeek.toString(),
                    detail = pluralStringResource(R.plurals.days_unit, history.daysThisWeek),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stat_total_sets),
                    value = history.totalSets.toString(),
                    detail = pluralStringResource(R.plurals.workouts_count, history.sessions.size, history.sessions.size),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "calendar") {
            HistoryCard(title = stringResource(R.string.training_days), color = cardColor) {
                TrainingCalendar(history = history, cardColor = cardColor, modifier = Modifier.fillMaxWidth())
            }
        }
        if (history.progress.isNotEmpty()) {
            item(key = "progress-header") { SectionHeader(stringResource(R.string.progress_title)) }
            items(history.progress, key = { "progress-${it.exerciseName}-${it.unit}" }) { progress ->
                ProgressCard(progress, cardColor)
            }
        }
        item(key = "sessions-header") { SectionHeader(stringResource(R.string.recent_workouts)) }
        items(history.sessions.take(MAX_SESSIONS), key = { "session-${it.date}" }) { session ->
            SessionRow(session)
        }
    }
}

/** Label, value and a quiet detail line; proportional figures for the number. */
@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    title: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                trailing()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp, start = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ProgressCard(progress: ExerciseProgress, cardColor: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    fun weight(value: Double) = formatWeightWithUnit(context, value, progress.unit)
    val first = progress.points.first()
    val change = progress.change
    // Plates are counted, not weighed: "up 2 plates" rather than "up Plate 2".
    val amount = if (progress.unit == WeightUnit.PLATE) {
        pluralStringResource(R.plurals.plates_count, abs(change).toInt(), formatWeight(abs(change)))
    } else {
        weight(abs(change))
    }
    val changeText = when {
        progress.points.size < 2 -> stringResource(R.string.progress_first_entry, weight(first.weight), relativeDay(first.date))
        change == 0.0 -> stringResource(R.string.progress_same_since, relativeDay(first.date))
        else -> stringResource(
            if (change > 0) R.string.progress_up_since else R.string.progress_down_since,
            amount,
            relativeDay(first.date),
        )
    }
    HistoryCard(
        title = progress.exerciseName,
        color = cardColor,
        trailing = {
            Text(
                text = weight(progress.latest),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.progress_summary, changeText, weight(progress.best)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress.points.size >= 2) {
            Spacer(Modifier.height(12.dp))
            ProgressChart(progress = progress, cardColor = cardColor, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SessionRow(session: Session) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = session.date.format(SESSION_DATE),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = session.exerciseNames.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.sets_count, session.setCount, session.setCount),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MAX_SESSIONS = 30
private val SESSION_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
