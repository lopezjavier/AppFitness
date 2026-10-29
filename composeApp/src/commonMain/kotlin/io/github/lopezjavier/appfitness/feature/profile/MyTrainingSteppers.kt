package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_count
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_less
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_days_more
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_session_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_session_less
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_session_more
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.durationText
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SECONDS_PER_MINUTE = 60

/** The stepper row of "Días por semana": "Sin definir" is the lowest step (R-47). */
@Composable
internal fun DaysRow(days: Int?, unset: String, onChange: (Int?) -> Unit) {
    val text = days?.let { daysText(it) } ?: unset
    StepperRow(
        label = stringResource(Res.string.my_training_days_label),
        valueText = text,
        isUnset = days == null,
        decrementEnabled = days != null,
        incrementEnabled = days == null || days < MyTraining.DAYS.last,
        decreaseCd = stringResource(Res.string.my_training_days_less),
        increaseCd = stringResource(Res.string.my_training_days_more),
        tag = "preferences_days_stepper",
        onDecrement = { onChange(previousDays(days)) },
        onIncrement = { onChange(nextDays(days)) },
    )
}

/** The stepper row of "Duración del entreno": steps of 5 min, "Sin definir" below the first. */
@Composable
internal fun SessionRow(minutes: Int?, unset: String, onChange: (Int?) -> Unit) {
    val text = minutes?.let { durationText(it.toLong() * SECONDS_PER_MINUTE) } ?: unset
    StepperRow(
        label = stringResource(Res.string.my_training_session_label),
        valueText = text,
        isUnset = minutes == null,
        decrementEnabled = minutes != null,
        incrementEnabled = minutes == null || minutes < MyTraining.SESSION_MINUTES.last,
        decreaseCd = stringResource(Res.string.my_training_session_less),
        increaseCd = stringResource(Res.string.my_training_session_more),
        tag = "preferences_session_stepper",
        onDecrement = { onChange(previousSessionMinutes(minutes)) },
        onIncrement = { onChange(nextSessionMinutes(minutes)) },
    )
}

@Composable
@Suppress("LongParameterList") // Label, value, two flags, two descriptions, tag and two handlers of one row.
private fun StepperRow(
    label: String,
    valueText: String,
    isUnset: Boolean,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    decreaseCd: String,
    increaseCd: String,
    tag: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            valueText = valueText,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
            decrementEnabled = decrementEnabled,
            incrementEnabled = incrementEnabled,
            decrementContentDescription = decreaseCd,
            incrementContentDescription = increaseCd,
            stateDescription = valueText,
            testTagPrefix = tag,
            valueColor = if (isUnset) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary,
        )
    }
}

internal const val DEFAULT_DAYS = 3
internal const val DEFAULT_SESSION_MINUTES = 60
internal const val SESSION_STEP_MINUTES = 5

/** "+" from "Sin definir" jumps to the default; otherwise one day up. */
internal fun nextDays(days: Int?): Int = if (days == null) DEFAULT_DAYS else minOf(days + 1, MyTraining.DAYS.last)

/** "-" at the first day returns to "Sin definir" (null). */
internal fun previousDays(days: Int?): Int? = days?.let { if (it <= MyTraining.DAYS.first) null else it - 1 }

/** "+" adds one step (stored values are always multiples of 5, so no snapping is needed). */
internal fun nextSessionMinutes(minutes: Int?): Int = if (minutes == null) {
    DEFAULT_SESSION_MINUTES
} else {
    minOf(minutes + SESSION_STEP_MINUTES, MyTraining.SESSION_MINUTES.last)
}

/** "-" removes one step; below the first value (15 min) it returns to "Sin definir" (null). */
internal fun previousSessionMinutes(minutes: Int?): Int? =
    minutes?.minus(SESSION_STEP_MINUTES)?.takeIf { it >= MyTraining.SESSION_MINUTES.first }

@Composable
private fun daysText(days: Int) = pluralStringResource(Res.plurals.my_training_days_count, days, days)
