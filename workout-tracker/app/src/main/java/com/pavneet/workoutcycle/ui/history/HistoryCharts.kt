package com.pavneet.workoutcycle.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.ExerciseProgress
import com.pavneet.workoutcycle.domain.WorkoutHistory
import com.pavneet.workoutcycle.session.formatWeightWithUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Sequential steps: the theme's primary blended over the card at rising strength, so it's one
 * hue, light to dark (and lighter for more on a dark card). The strengths were checked with the
 * dataviz validator against the fallback palette in light and dark: monotone lightness, a
 * visible gap between steps, and a light end of at least 2:1 against the card.
 */
private val HEAT_STRENGTHS = listOf(0.5f, 0.68f, 0.84f, 1f)

/** Sets in a day at which each heat step starts. */
private val HEAT_THRESHOLDS = listOf(1, 5, 10, 15)

private val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** GitHub-style grid of the last [weeks] weeks: one column per week, Monday at the top. */
@Composable
fun TrainingCalendar(history: WorkoutHistory, cardColor: Color, modifier: Modifier = Modifier, weeks: Int = 12) {
    val today = history.today
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeks - 1L)
    val primary = MaterialTheme.colorScheme.primary
    val steps = remember(primary, cardColor) { HEAT_STRENGTHS.map { primary.copy(alpha = it).compositeOver(cardColor) } }
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    fun colorFor(sets: Int): Color = HEAT_THRESHOLDS.indexOfLast { sets >= it }.let { if (it < 0) empty else steps[it] }

    BoxWithConstraints(modifier) {
        // Labels and squares share one measured cell size, so the rows always line up.
        val cell = (maxWidth - DAY_LABEL_WIDTH - CELL_GAP * weeks) / weeks
        Column {
            // Month names above the week where each month starts.
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                Spacer(Modifier.width(DAY_LABEL_WIDTH))
                for (week in 0 until weeks) {
                    val monday = firstMonday.plusWeeks(week.toLong())
                    val label = if (week == 0 || monday.month != monday.minusWeeks(1).month) {
                        monday.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                    } else {
                        ""
                    }
                    Box(Modifier.width(cell)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            // A month name may run into the next (label-free) column.
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                Column(Modifier.width(DAY_LABEL_WIDTH), verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    DayOfWeek.entries.forEach { day ->
                        Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                            // Every other row is labelled, like a wall calendar's margin.
                            if (day.value % 2 == 1) {
                                Text(
                                    text = day.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                for (week in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        for (dayIndex in 0 until 7) {
                            val date = firstMonday.plusWeeks(week.toLong()).plusDays(dayIndex.toLong())
                            if (date.isAfter(today)) {
                                Spacer(Modifier.size(cell))
                            } else {
                                val sets = history.setsOn(date)
                                val description = pluralStringResource(R.plurals.day_sets, sets, date.format(DAY_SHORT), sets)
                                Box(
                                    Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(colorFor(sets))
                                        .clickable(role = Role.Button) { selected = date }
                                        .semantics { contentDescription = description },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.legend_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                (listOf(empty) + steps).forEach { color ->
                    Box(
                        Modifier
                            .padding(start = 3.dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
                Text(
                    text = stringResource(R.string.legend_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                val day = selected
                Text(
                    text = if (day == null) {
                        stringResource(R.string.calendar_tap_hint)
                    } else {
                        pluralStringResource(R.plurals.day_sets, history.setsOn(day), day.format(DAY_SHORT), history.setsOn(day))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val DAY_LABEL_WIDTH = 18.dp
private val CELL_GAP = 2.dp

/**
 * One exercise's heaviest weight per training day over time. Single series, so no legend: the
 * card's title names it. Tapping picks the nearest day; every value is also in the card's text.
 */
@Composable
fun ProgressChart(progress: ExerciseProgress, cardColor: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val points = progress.points
    var selectedIndex by remember(points) { mutableStateOf(points.lastIndex) }
    val selected = points[selectedIndex.coerceIn(points.indices)]
    val valueText = { weight: Double -> formatWeightWithUnit(context, weight, progress.unit) }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inkColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()

    val first = points.first().date
    val spanDays = ChronoUnit.DAYS.between(first, points.last().date).coerceAtLeast(1)
    val ticks = remember(points) { niceTicks(points.minOf { it.weight }, points.maxOf { it.weight }) }
    val description = points.joinToString(", ") { "${it.date.format(DAY_SHORT)} ${valueText(it.weight)}" }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val plotLeft = PLOT_LEFT.toPx()
                        val plotWidth = size.width - plotLeft - PLOT_RIGHT.toPx()
                        val fraction = ((tap.x - plotLeft) / plotWidth).coerceIn(0f, 1f)
                        selectedIndex = points.indices.minBy { index ->
                            val x = ChronoUnit.DAYS.between(first, points[index].date).toFloat() / spanDays
                            kotlin.math.abs(x - fraction)
                        }
                    }
                },
        ) {
            val plotLeft = PLOT_LEFT.toPx()
            val plotTop = 8.dp.toPx()
            val plotRight = size.width - PLOT_RIGHT.toPx()
            val plotBottom = size.height - 20.dp.toPx()
            val low = ticks.first()
            val high = ticks.last()

            fun x(date: LocalDate) =
                plotLeft + (plotRight - plotLeft) * ChronoUnit.DAYS.between(first, date) / spanDays.toFloat()
            fun y(weight: Double) = plotBottom - (plotBottom - plotTop) * ((weight - low) / (high - low)).toFloat()

            // Hairline, solid, recessive gridlines with clean values on the left.
            ticks.forEach { tick ->
                val tickY = y(tick)
                drawLine(gridColor, Offset(plotLeft, tickY), Offset(plotRight, tickY), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(formatTick(tick), TextStyle(color = labelColor, fontSize = labelStyle.fontSize))
                drawText(label, topLeft = Offset(plotLeft - label.size.width - 6.dp.toPx(), tickY - label.size.height / 2f))
            }
            // First and last dates under the axis.
            val startLabel = measurer.measure(first.format(DAY_SHORT), TextStyle(color = labelColor, fontSize = labelStyle.fontSize))
            drawText(startLabel, topLeft = Offset(plotLeft, plotBottom + 4.dp.toPx()))
            if (points.size > 1) {
                val endLabel = measurer.measure(points.last().date.format(DAY_SHORT), TextStyle(color = labelColor, fontSize = labelStyle.fontSize))
                drawText(endLabel, topLeft = Offset(plotRight - endLabel.size.width, plotBottom + 4.dp.toPx()))
            }

            val line = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) line.moveTo(x(point.date), y(point.weight)) else line.lineTo(x(point.date), y(point.weight))
            }
            if (points.size > 1) {
                // A faint wash under a single series, then the 2dp line.
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(points.last().date), plotBottom)
                    lineTo(x(points.first().date), plotBottom)
                    close()
                }
                drawPath(area, lineColor.copy(alpha = 0.10f))
                drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Markers with a ring in the card colour; the selected one is larger.
            points.forEachIndexed { index, point ->
                val center = Offset(if (points.size > 1) x(point.date) else (plotLeft + plotRight) / 2f, y(point.weight))
                val radius = if (index == selectedIndex) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(cardColor, radius = radius + 2.dp.toPx(), center = center)
                drawCircle(lineColor, radius = radius, center = center)
            }
        }
        Text(
            text = stringResource(R.string.progress_selected, valueText(selected.weight), selected.date.format(DAY_SHORT)),
            style = MaterialTheme.typography.labelMedium,
            color = inkColor,
            modifier = Modifier.padding(start = PLOT_LEFT, top = 2.dp),
        )
    }
}

private val PLOT_LEFT = 36.dp
private val PLOT_RIGHT = 8.dp

