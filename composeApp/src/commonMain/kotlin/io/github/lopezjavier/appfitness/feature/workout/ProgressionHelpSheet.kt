package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_add_rep
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_kept
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_lower
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_raise
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_set
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_set_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_sets_title
import io.github.lopezjavier.appfitness.composeapp.resources.progression_help_title
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SetUsed
import org.jetbrains.compose.resources.stringResource

/**
 * Info sheet of one suggestion (US-70): the reason, the sets it was computed from and a footer saying it changes
 * nothing. Its open state is `Active.progressionHelpPosition` (survives process death).
 */
@Composable
internal fun ProgressionHelpSheet(suggestion: ProgressionSuggestion, onDismiss: () -> Unit) {
    AppInfoSheet(
        title = stringResource(Res.string.progression_help_title),
        onDismiss = onDismiss,
        testTagPrefix = "progression_help_sheet",
    ) {
        Column(Modifier.testTag("progression_help_body"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HelpLine(reasonText(suggestion), Modifier.testTag("progression_help_reason"))
            if (suggestion.setsUsed.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.progression_help_sets_title),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
                suggestion.setsUsed.forEachIndexed { index, set ->
                    HelpLine(setText(set), Modifier.testTag("progression_help_set_${index + 1}"))
                }
            }
        }
    }
}

@Composable
private fun reasonText(suggestion: ProgressionSuggestion): String = when (suggestion.reason) {
    ProgressionReason.AllSetsAtMax -> stringResource(
        Res.string.progression_help_raise,
        AppNumberFormat.integer(suggestion.setsUsed.minOfOrNull { it.reps } ?: suggestion.goalReps),
    )

    ProgressionReason.BelowMax -> stringResource(
        Res.string.progression_help_add_rep,
        AppNumberFormat.integer(suggestion.goalReps),
    )

    ProgressionReason.TwoSessionsBelowMin -> stringResource(
        Res.string.progression_help_lower,
        AppNumberFormat.integer(suggestion.goalReps),
    )

    ProgressionReason.KeptDiscomfort -> stringResource(Res.string.progression_help_kept)
}

@Composable
private fun setText(set: SetUsed): String {
    val date = AppDateFormat.weekdayDayMonth(set.date.dayOfWeek, set.date.day, set.date.month)
    val weight = AppNumberFormat.decimal(set.weightKg, maxDecimals = 2)
    val reps = AppNumberFormat.integer(set.reps)
    return set.rpe?.let {
        stringResource(Res.string.progression_help_set_rpe, date, weight, reps, AppNumberFormat.decimal(it))
    } ?: stringResource(Res.string.progression_help_set, date, weight, reps)
}

@Composable
private fun HelpLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary, modifier = modifier)
}
