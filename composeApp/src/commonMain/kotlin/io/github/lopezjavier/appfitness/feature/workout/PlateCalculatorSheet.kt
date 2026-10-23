package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_plate_calc_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_plate_calc_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_bar_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_bar_only
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_close
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_hint_missing
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_not_exact
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_plate_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_plates_line_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_plates_per_side
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_target_weight
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PlateResult
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import io.github.lopezjavier.appfitness.domain.usecase.perSideText
import org.jetbrains.compose.resources.stringResource

/**
 * Plate calculator: read-only, the result follows the target weight and bar as they change. "Cerrar" is the pinned
 * action; nothing is stored.
 */
@Composable
internal fun PlateCalculatorSheet(position: Int, calculator: CalculatorUiState, onEvent: (LiveSessionEvent) -> Unit) {
    val tag = "live_exercise_${position}_plate_calc"
    CalculatorSheetFrame(
        tag = tag,
        title = stringResource(Res.string.live_calc_plate_title),
        weightLabel = stringResource(Res.string.live_calc_target_weight),
        calculator = calculator,
        onEvent = onEvent,
        help = CalculatorHelp(
            stringResource(Res.string.help_plate_calc_cd),
            stringResource(Res.string.help_plate_calc_body),
            "plate_calc_help",
        ),
        results = { PlateResults(tag, calculator) },
        action = {
            SecondaryButton(
                text = stringResource(Res.string.live_calc_close),
                onClick = { onEvent(LiveSessionEvent.DismissCalculator) },
                modifier = Modifier.fillMaxWidth().testTag("${tag}_close"),
            )
        },
    )
}

@Composable
private fun PlateResults(tag: String, calculator: CalculatorUiState) {
    val plates = calculator.plates
    val target = calculator.weightKg
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_results"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (plates == null || target == null) {
            Text(
                text = stringResource(Res.string.live_calc_hint_missing),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${tag}_hint"),
            )
            return@Column
        }
        val barKg = GhostData.weightText(calculator.draft.barKg)
        PlatesLine(tag, plates)
        Text(
            text = stringResource(Res.string.live_calc_bar_label, barKg),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("${tag}_bar_label"),
        )
        if (!plates.exact && target > calculator.draft.barKg) {
            NotExactNote(tag, target, plates.achievedKg)
        }
    }
}

@Composable
private fun PlatesLine(tag: String, plates: PlateResult) {
    if (plates.platesPerSide.isEmpty()) {
        Text(
            text = stringResource(Res.string.live_calc_bar_only),
            style = AppTheme.typography.headlineMetric,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("${tag}_plates"),
        )
        return
    }
    val line = plates.perSideText()
    val description = stringResource(Res.string.live_calc_plates_line_cd, line)
    Column {
        Text(
            text = line,
            style = AppTheme.typography.headlineMetric,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { contentDescription = description }.testTag("${tag}_plates"),
        )
        Text(
            text = stringResource(Res.string.live_calc_plates_per_side),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun NotExactNote(tag: String, targetKg: Double, achievedKg: Double) {
    Text(
        text = stringResource(
            Res.string.live_calc_not_exact,
            GhostData.weightText(targetKg),
            GhostData.weightText(achievedKg),
        ),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.energyAmber,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceOverlay, AppShapes.sm)
            .padding(Spacing.md)
            .testTag("${tag}_note"),
    )
}
