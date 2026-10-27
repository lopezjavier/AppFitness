package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_previous_mode_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_previous_mode_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_auto_update_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_auto_update_label
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_previous_mode_overall
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_previous_mode_routine
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_previous_values_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_label
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppSwitchRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import org.jetbrains.compose.resources.stringResource

/**
 * "Entrenamiento" editor content: "Incluir calentamiento" and "Actualizar automáticamente" rows, the
 * previous-values selector
 * and the "Calculadoras" block. The switch is drawn inside but hidden from semantics, so there is a single focus
 * target (the row). Nothing is applied until "Guardar".
 */
@Suppress("LongParameterList") // One value and callback per control of the card.
@Composable
internal fun TrainingSection(
    warmUpIncluded: Boolean,
    onChange: (Boolean) -> Unit,
    autoUpdateRoutine: Boolean,
    onAutoUpdateRoutine: (Boolean) -> Unit,
    previousValuesMode: PreviousValuesMode,
    onPreviousValuesMode: (PreviousValuesMode) -> Unit,
    calculators: CalculatorSettings,
    platesError: Boolean,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    run {
        Column(
            modifier.fillMaxWidth().testTag("preferences_training_card"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AppSwitchRow(
                label = stringResource(Res.string.preferences_warm_up_label),
                helper = stringResource(Res.string.preferences_warm_up_helper),
                checked = warmUpIncluded,
                onCheckedChange = onChange,
                modifier = Modifier.testTag("preferences_warm_up_switch"),
            )
            AppSwitchRow(
                label = stringResource(Res.string.preferences_auto_update_label),
                helper = stringResource(Res.string.preferences_auto_update_helper),
                checked = autoUpdateRoutine,
                onCheckedChange = onAutoUpdateRoutine,
                modifier = Modifier.testTag("preferences_auto_update_switch"),
            )
            PreviousValuesSelector(previousValuesMode, onPreviousValuesMode)
            CalculatorsBlock(calculators, platesError, onEvent)
        }
    }
}

/** "Valores anteriores": where the live session takes last time's values from; only a draft until "Guardar". */
@Composable
private fun PreviousValuesSelector(mode: PreviousValuesMode, onSelect: (PreviousValuesMode) -> Unit) {
    val modes = listOf(PreviousValuesMode.Routine, PreviousValuesMode.Overall)
    val options = listOf(
        AppOption(stringResource(Res.string.preferences_previous_mode_routine), "preferences_previous_mode_routine"),
        AppOption(stringResource(Res.string.preferences_previous_mode_overall), "preferences_previous_mode_overall"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.preferences_previous_values_title),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            HelpButton(
                contentDescription = stringResource(Res.string.help_previous_mode_cd),
                title = stringResource(Res.string.preferences_previous_values_title),
                body = stringResource(Res.string.help_previous_mode_body),
                testTag = "previous_mode_help",
            )
        }
        SegmentedSelector(
            options = options,
            selectedIndex = modes.indexOf(mode),
            onSelect = { onSelect(modes[it]) },
            modifier = Modifier.testTag("preferences_previous_mode"),
        )
    }
}
