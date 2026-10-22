package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weight_field_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_field_weight
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hour_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hours_unit
import io.github.lopezjavier.appfitness.composeapp.resources.weight_minute_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_minutes_unit
import io.github.lopezjavier.appfitness.composeapp.resources.weight_range_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_range_weight
import io.github.lopezjavier.appfitness.composeapp.resources.weight_time_label
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WheelPicker
import io.github.lopezjavier.appfitness.designsystem.WheelPickerDefaults
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val LAST_HOUR = HOURS_PER_DAY - 1
private const val LAST_MINUTE = MINUTES_PER_HOUR - 1
private const val WEIGHT_INTEGER_DIGITS = 3
private const val FAT_INTEGER_DIGITS = 2

/**
 * The manual weight form: weight and body fat, the range hint of an invalid field and the time wheels. The typed
 * texts live here too (seeded from [form] when the form appears) because [NumberInput] needs its value updated
 * synchronously; every edit is also sent to the view model.
 */
@Composable
internal fun MeasurementFormFields(day: LocalDate, form: MeasurementForm, onEvent: (WeightEvent) -> Unit) {
    var weight by remember(day, form.existingId) { mutableStateOf(form.weightText) }
    var fat by remember(day, form.existingId) { mutableStateOf(form.fatText) }
    val weightLabel = stringResource(Res.string.weight_field_weight)
    val fatLabel = stringResource(Res.string.weight_field_fat)
    val weightInput: @Composable (Modifier) -> Unit = { modifier ->
        NumberInput(
            value = weight,
            onValueChange = {
                weight = it
                onEvent(WeightEvent.SetWeight(it))
            },
            label = weightLabel,
            modifier = modifier.testTag("weight_sheet_weight_input"),
            maxDecimals = 1,
            maxIntegerDigits = WEIGHT_INTEGER_DIGITS,
        )
    }
    val fatInput: @Composable (Modifier) -> Unit = { modifier ->
        NumberInput(
            value = fat,
            onValueChange = {
                fat = it
                onEvent(WeightEvent.SetBodyFat(it))
            },
            label = fatLabel,
            modifier = modifier.testTag("weight_sheet_fat_input"),
            maxDecimals = 1,
            maxIntegerDigits = FAT_INTEGER_DIGITS,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < WheelPickerDefaults.SideBySideMinWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    weightInput(Modifier)
                    fatInput(Modifier)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    weightInput(Modifier.weight(1f))
                    fatInput(Modifier.weight(1f))
                }
            }
        }
        RangeHint(form)
        TimeWheels(form, onEvent)
    }
}

/** The allowed range of every invalid field, in one block (blank fields report no problem). */
@Composable
private fun RangeHint(form: MeasurementForm) {
    val lines = listOfNotNull(
        if (form.weightProblem != null) stringResource(Res.string.weight_range_weight) else null,
        if (form.fatProblem != null) stringResource(Res.string.weight_range_fat) else null,
    )
    if (lines.isNotEmpty()) {
        Text(
            text = lines.joinToString("\n"),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.error,
            modifier = Modifier.testTag("weight_sheet_range_hint"),
        )
    }
}

/** Hour and minute wheels; on today they stop at the current minute (the view model clamps what is requested). */
@Composable
private fun TimeWheels(form: MeasurementForm, onEvent: (WeightEvent) -> Unit) = TimeWheelFields(
    hour = form.hour,
    minute = form.minute,
    maxMinuteOfDay = form.maxMinuteOfDay,
    testTagPrefix = "weight_sheet",
    onTimeChange = { hour, minute -> onEvent(WeightEvent.SetTime(hour, minute)) },
)

/**
 * Hour and minute wheels with the time label. Tags: "[testTagPrefix]_time", "_time_value", "_hour" and "_minute".
 * [maxMinuteOfDay] (today) stops the wheels at the current minute; null leaves the whole day.
 */
@Composable
internal fun TimeWheelFields(
    hour: Int,
    minute: Int,
    maxMinuteOfDay: Int?,
    testTagPrefix: String,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
) {
    val maxMinute = maxMinuteOfDay
    val maxHour = maxMinute?.div(MINUTES_PER_HOUR) ?: LAST_HOUR
    val hourRange = 0..maxHour
    val minuteRange = if (maxMinute != null &&
        hour >= maxHour
    ) {
        0..(maxMinute % MINUTES_PER_HOUR)
    } else {
        0..LAST_MINUTE
    }
    val hoursUnit = stringResource(Res.string.weight_hours_unit)
    val minutesUnit = stringResource(Res.string.weight_minutes_unit)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(Res.string.weight_time_label).uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
            )
            Text(
                text = timeText(hour, minute),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("${testTagPrefix}_time_value"),
            )
        }
        Row(
            Modifier.fillMaxWidth().testTag("${testTagPrefix}_time"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelPicker(
                range = hourRange,
                value = hour,
                onValueChange = { onTimeChange(it, minute) },
                label = ::twoDigits,
                contentDescription = stringResource(Res.string.weight_hour_cd),
                stateDescription = { "${twoDigits(it)} $hoursUnit" },
                testTagPrefix = "${testTagPrefix}_hour",
            )
            Text(text = ":", style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textPrimary)
            WheelPicker(
                range = minuteRange,
                value = minute,
                onValueChange = { onTimeChange(hour, it) },
                label = ::twoDigits,
                contentDescription = stringResource(Res.string.weight_minute_cd),
                stateDescription = { "${twoDigits(it)} $minutesUnit" },
                testTagPrefix = "${testTagPrefix}_minute",
            )
        }
    }
}
