package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_trend_note
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_unreliable_days
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_unreliable_note
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import org.jetbrains.compose.resources.stringResource

/** Few weeks of data: the trend is only a guide (R-41: text, not colour alone). */
@Composable
internal fun LowConfidenceTrendNote(trend: DerivedWeightTrend) {
    if (!trend.lowConfidence) return
    InlineNote(
        text = stringResource(Res.string.trainer_trend_note),
        modifier = Modifier.padding(top = Spacing.xs),
        testTag = "trainer_trend_note",
    )
}

/** The diary is too incomplete for a reliable expenditure estimate (US-69), with the days that were logged. */
@Composable
internal fun UnreliableIntakeNote(values: CoachDerivedValues) {
    val intake = values.intake
    if (intake.reliable) return
    val logged = intake.loggedDays
    val window = intake.windowDays
    val base = stringResource(Res.string.trainer_unreliable_note)
    val text = if (logged != null && window != null) {
        base + " " + stringResource(Res.string.trainer_unreliable_days, logged, window)
    } else {
        base
    }
    InlineNote(text = text, modifier = Modifier.padding(top = Spacing.xs), testTag = "trainer_unreliable_note")
}
