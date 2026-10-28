package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sleep
import io.github.lopezjavier.appfitness.composeapp.resources.activity_stage_awake
import io.github.lopezjavier.appfitness.composeapp.resources.activity_stage_deep
import io.github.lopezjavier.appfitness.composeapp.resources.activity_stage_light
import io.github.lopezjavier.appfitness.composeapp.resources.activity_stage_rem
import io.github.lopezjavier.appfitness.composeapp.resources.activity_stage_sleeping
import io.github.lopezjavier.appfitness.composeapp.resources.activity_stage_unknown
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SleepStageKind
import io.github.lopezjavier.appfitness.designsystem.SleepStageSegment
import io.github.lopezjavier.appfitness.designsystem.SleepStagesBar
import io.github.lopezjavier.appfitness.domain.usecase.SleepStageSummary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun kindOf(key: String): SleepStageKind = when (key) {
    "awake" -> SleepStageKind.Awake
    "light" -> SleepStageKind.Light
    "deep" -> SleepStageKind.Deep
    "rem" -> SleepStageKind.Rem
    "sleeping" -> SleepStageKind.Sleeping
    else -> SleepStageKind.Unknown
}

private fun SleepStageKind.nameRes(): StringResource = when (this) {
    SleepStageKind.Awake -> Res.string.activity_stage_awake
    SleepStageKind.Light -> Res.string.activity_stage_light
    SleepStageKind.Deep -> Res.string.activity_stage_deep
    SleepStageKind.Rem -> Res.string.activity_stage_rem
    SleepStageKind.Sleeping -> Res.string.activity_stage_sleeping
    SleepStageKind.Unknown -> Res.string.activity_stage_unknown
}

/** Minutes per stage kind in display order (unknown keys join "Sin clasificar"). */
private fun SleepStageSummary.byKind(): List<Pair<SleepStageKind, Int>> = minutesByStage.entries
    .groupBy({ kindOf(it.key) }, { it.value })
    .map { (kind, minutes) -> kind to minutes.sum() }
    .sortedBy { it.first.ordinal }

/**
 * Sleep detail of the selected day under the sleep chart: the stage distribution when Health Connect gave stages,
 * otherwise only the total duration (nothing when the day has no sleep).
 */
@Composable
internal fun SleepDetail(state: ActivityUiState.Content) {
    val stages = state.sleepStages
    if (stages != null) {
        val segments = stages.byKind().map { (kind, minutes) ->
            SleepStageSegment(kind, minutes, stringResource(kind.nameRes()), sleepText(minutes))
        }
        SleepStagesBar(
            segments = segments,
            description = segments.joinToString(", ") { "${it.label} ${it.durationText}" },
            testTag = "activity_sleep_stages",
        )
    } else {
        state.summary.sleep?.let {
            Text(
                text = "${stringResource(Res.string.activity_sleep)} ${sleepText(it.minutes)}",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth().testTag("activity_sleep_total"),
            )
        }
    }
}
