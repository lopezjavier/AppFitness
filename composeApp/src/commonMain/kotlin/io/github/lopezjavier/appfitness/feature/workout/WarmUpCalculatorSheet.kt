package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_warmup_calc_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_warmup_calc_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_hint_at_or_below_bar
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_hint_missing
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_row
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_sets_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_sets_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_sets_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_sets_state
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_warm_up_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_working_weight
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SetOrdinal
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import io.github.lopezjavier.appfitness.domain.usecase.WarmUpCalculator
import org.jetbrains.compose.resources.stringResource

/**
 * Warm-up calculator: the working weight and bar produce up to four "C" rows; "Añadir series" inserts them before the
 * working sets (edit archetype, commit "Añadir"). The rows are a draft, so closing the sheet inserts nothing.
 * [capHint] (non-null) disables the commit.
 */
@Composable
internal fun WarmUpCalculatorSheet(
    tagPrefix: String,
    calculator: CalculatorUiState,
    actions: CalculatorActions,
    capHint: String? = null,
) {
    val tag = "${tagPrefix}_warm_up_calc"
    val title = stringResource(Res.string.live_calc_warm_up_title)
    val help = CalculatorHelp(
        stringResource(Res.string.help_warmup_calc_cd),
        stringResource(Res.string.help_warmup_calc_body),
        "warmup_calc_help",
    )
    AppEditSheet(
        title = title,
        commit = SheetCommit.Add,
        onCommit = { actions.onAddWarmUp(calculator.warmUp) },
        onDismiss = actions.onDismiss,
        commitEnabled = calculator.canAddWarmUp && capHint == null,
        help = { CalculatorHelpButton(title, help) },
        testTagPrefix = "${tag}_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CalculatorWeightField(tag, stringResource(Res.string.live_calc_working_weight), calculator, actions)
            BarLabel(tag, calculator)
            WarmUpSetsStepper(calculator.draft.warmUpSets, actions.onWarmUpSets)
            WarmUpResults(tag, calculator)
            calculator.hint?.let {
                Text(
                    text = stringResource(
                        if (it == CalculatorHint.MissingWeight) {
                            Res.string.live_calc_hint_missing
                        } else {
                            Res.string.live_calc_hint_at_or_below_bar
                        },
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${tag}_hint"),
                )
            }
            capHint?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.energyAmber,
                    modifier = Modifier.testTag("${tag}_cap_hint"),
                )
            }
        }
    }
}

@Composable
private fun WarmUpSetsStepper(sets: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.live_calc_warm_up_sets_label),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            valueText = sets.toString(),
            onDecrement = { onChange(sets - 1) },
            onIncrement = { onChange(sets + 1) },
            decrementEnabled = sets > 1,
            incrementEnabled = sets < WarmUpCalculator.MAX_SETS,
            decrementContentDescription = stringResource(Res.string.live_calc_warm_up_sets_decrease_cd),
            incrementContentDescription = stringResource(Res.string.live_calc_warm_up_sets_increase_cd),
            stateDescription = stringResource(Res.string.live_calc_warm_up_sets_state, sets),
            testTagPrefix = "warmup_sets_stepper",
        )
    }
}

@Composable
private fun WarmUpResults(tag: String, calculator: CalculatorUiState) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_results"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        calculator.warmUp.forEachIndexed { index, set ->
            val weight = GhostData.weightText(set.weightKg)
            val description = stringResource(Res.string.live_calc_warm_up_row_cd, index + 1, weight, set.reps)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .testTag("${tag}_row_$index"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SetOrdinal((index + 1).toString())
                SetTypeBadge(SetType.WarmUp, Modifier.clearAndSetSemantics {})
                Text(
                    text = stringResource(Res.string.live_calc_warm_up_row, weight, set.reps),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                )
            }
        }
    }
}
