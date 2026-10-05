package com.pavneet.workoutcycle.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.Attachment
import com.pavneet.workoutcycle.domain.LoggedSet
import com.pavneet.workoutcycle.domain.MachineSetup
import com.pavneet.workoutcycle.domain.WeightUnit
import com.pavneet.workoutcycle.domain.formatWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "35 lb", "15 kg" or "Plate 7". */
@Composable
fun weightText(weight: Double, unit: WeightUnit): String = stringResource(
    when (unit) {
        WeightUnit.KG -> R.string.weight_kg
        WeightUnit.LB -> R.string.weight_lb
        WeightUnit.PLATE -> R.string.weight_plate
    },
    formatWeight(weight),
)

/** Short unit name for labels: "kg", "lb" or "plate". */
@get:StringRes
val WeightUnit.unitLabelRes: Int
    get() = when (this) {
        WeightUnit.KG -> R.string.unit_kg
        WeightUnit.LB -> R.string.unit_lb
        WeightUnit.PLATE -> R.string.unit_plate
    }

@get:StringRes
val Attachment.attachmentLabelRes: Int
    get() = when (this) {
        Attachment.ROPE -> R.string.attachment_rope
        Attachment.HANDLE -> R.string.attachment_handle
        Attachment.STRAIGHT_BAR -> R.string.attachment_straight_bar
        Attachment.V_BAR -> R.string.attachment_v_bar
        Attachment.ANKLE_STRAP -> R.string.attachment_ankle_strap
    }

/** "35 lb × 12 reps", or `null` for a set logged without either. */
@Composable
fun setSummary(set: LoggedSet): String? {
    val weight = set.weight?.let { weight -> set.unit?.let { weightText(weight, it) } }
    val reps = set.reps?.let { pluralStringResource(R.plurals.reps_count, it, it) }
    return listOfNotNull(weight, reps).joinToString(" × ").ifEmpty { null }
}

/** "Rope · Notch 12 · Two steps back", or `null` when nothing is saved. */
@Composable
fun setupSummary(setup: MachineSetup): String? = listOfNotNull(
    setup.attachment?.let { stringResource(it.attachmentLabelRes) },
    setup.pulleyPosition.trim().ifEmpty { null },
    setup.note.trim().ifEmpty { null },
).joinToString(" · ").ifEmpty { null }

/** "today", "yesterday", a weekday within the last week, otherwise a short date. */
@Composable
fun relativeDay(epochMillis: Long, today: LocalDate = LocalDate.now()): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return relativeDay(date, today)
}

@Composable
fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    when (ChronoUnit.DAYS.between(date, today)) {
        0L -> stringResource(R.string.day_today)
        1L -> stringResource(R.string.day_yesterday)
        in 2L..6L -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
