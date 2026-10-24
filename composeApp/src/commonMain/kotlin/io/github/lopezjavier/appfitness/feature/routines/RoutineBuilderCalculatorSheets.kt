package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_warm_up_cap_hint
import io.github.lopezjavier.appfitness.composeapp.resources.builder_warm_up_cap_hint_superset
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.feature.workout.CalculatorActions
import io.github.lopezjavier.appfitness.feature.workout.CalculatorKind
import io.github.lopezjavier.appfitness.feature.workout.DiscLoaderSheet
import io.github.lopezjavier.appfitness.feature.workout.WarmUpCalculatorSheet
import org.jetbrains.compose.resources.stringResource

/**
 * The open warm-up or plate calculator of the builder. Their state lives in the
 * view model (it survives leaving the app); the sheets are the ones the live session uses, tagged
 * `routine_builder_<row>_...`.
 */
@Composable
internal fun BuilderOptionSheets(state: RoutineBuilderUiState.Editing, onEvent: (RoutineBuilderEvent) -> Unit) {
    state.calculator?.let { calculator ->
        val index = calculator.draft.position
        val prefix = "routine_builder_$index"
        val actions = CalculatorActions(
            onWeight = { onEvent(RoutineBuilderEvent.CalculatorWeightChanged(it)) },
            onDiscCount = { kg, count -> onEvent(RoutineBuilderEvent.DiscCountChanged(kg, count)) },
            onWarmUpSets = { onEvent(RoutineBuilderEvent.CalculatorWarmUpSetsChanged(it)) },
            onAddWarmUp = { onEvent(RoutineBuilderEvent.AddWarmUpSets(index, it)) },
            onDismiss = { onEvent(RoutineBuilderEvent.DismissCalculator) },
        )
        when (calculator.draft.kind) {
            CalculatorKind.WarmUp -> WarmUpCalculatorSheet(
                tagPrefix = prefix,
                calculator = calculator,
                actions = actions,
                capHint = capHint(state, index),
            )

            CalculatorKind.Discs -> DiscLoaderSheet(prefix, calculator, actions)
        }
    }
}

@Composable
private fun capHint(state: RoutineBuilderUiState.Editing, index: Int): String? = when {
    !state.warmUpBlockedByCap -> null

    state.rows.getOrNull(index)?.supersetLabel != null ->
        stringResource(Res.string.builder_warm_up_cap_hint_superset, RoutineBounds.SETS_MAX)

    else -> stringResource(Res.string.builder_warm_up_cap_hint, RoutineBounds.SETS_MAX)
}
