package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_disc_label
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_per_side
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_state
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_title
import io.github.lopezjavier.appfitness.composeapp.resources.disc_loader_total
import io.github.lopezjavier.appfitness.composeapp.resources.help_disc_loader_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_disc_loader_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_bar_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_handle_label
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DiscStepperRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.DiscLoader
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Disc loader (US-33, ADR-56): one stepper per enabled plate (heaviest first) with the discs on ONE side; the same
 * go on the other side. The total (bar or handle plus both sides) closes the list.
 * Read-only (the picker archetype, which
 * also carries the "?"): nothing is stored or applied.
 */
@Composable
internal fun DiscLoaderSheet(tagPrefix: String, calculator: CalculatorUiState, actions: CalculatorActions) {
    val tag = "${tagPrefix}_disc_loader"
    val title = stringResource(Res.string.disc_loader_title)
    val help = CalculatorHelp(
        stringResource(Res.string.help_disc_loader_cd),
        stringResource(Res.string.help_disc_loader_body),
        "${tag}_help",
    )
    AppPickerSheet(
        title = title,
        onDismiss = actions.onDismiss,
        help = { CalculatorHelpButton(title, help) },
        testTagPrefix = "${tag}_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BarLabel(tag, calculator)
            calculator.availableDiscs.forEach { kg -> DiscRow(tag, kg, calculator, actions) }
            DiscTotals(tag, calculator)
        }
    }
}

@Composable
private fun DiscRow(tag: String, kg: Double, calculator: CalculatorUiState, actions: CalculatorActions) {
    val kgText = GhostData.weightText(kg)
    val count = calculator.draft.discs[kg] ?: 0
    DiscStepperRow(
        label = stringResource(Res.string.disc_loader_disc_label, kgText),
        count = count,
        maxCount = DiscLoader.MAX_DISCS_PER_WEIGHT,
        onCountChange = { actions.onDiscCount(kg, it) },
        decrementContentDescription = stringResource(Res.string.disc_loader_remove_cd, kgText),
        incrementContentDescription = stringResource(Res.string.disc_loader_add_cd, kgText),
        stateDescription = pluralStringResource(Res.plurals.disc_loader_state, count, count, kgText),
        tag = "${tag}_plate_${kgText.replace(',', '_')}",
    )
}

@Composable
private fun DiscTotals(tag: String, calculator: CalculatorUiState) {
    val total = GhostData.weightText(calculator.totalKg ?: calculator.barKg)
    val perSide = GhostData.weightText(DiscLoader.perSideKg(calculator.draft.discs))
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_results"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(Res.string.disc_loader_total, total),
            style = AppTheme.typography.headlineMetric,
            color = AppTheme.colors.athleticCyan,
            modifier = Modifier.testTag("${tag}_total"),
        )
        Text(
            text = stringResource(Res.string.disc_loader_per_side, perSide),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("${tag}_per_side"),
        )
    }
}

/** "Barra 20 kg", or "Mango: 1 kg" for dumbbells (the handle actually used). */
@Composable
internal fun BarLabel(tag: String, calculator: CalculatorUiState) {
    val barKg = GhostData.weightText(calculator.barKg)
    val dumbbell = calculator.isDumbbell
    Text(
        text = stringResource(
            if (dumbbell) Res.string.live_calc_handle_label else Res.string.live_calc_bar_label,
            barKg,
        ),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.testTag(if (dumbbell) "${tag}_handle_label" else "${tag}_bar_label"),
    )
}
