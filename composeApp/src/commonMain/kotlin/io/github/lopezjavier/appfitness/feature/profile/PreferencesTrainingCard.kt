package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_previous_mode_overall
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_previous_mode_routine
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_previous_values_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_label
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_off
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_on
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import org.jetbrains.compose.resources.stringResource

/**
 * "Entrenamiento" card: one row whose whole 48 dp area toggles "Incluir calentamiento". The switch is drawn inside
 * but hidden from semantics, so there is a single focus target (the row). Nothing is applied until "Guardar".
 */
@Composable
internal fun TrainingCard(
    warmUpIncluded: Boolean,
    onChange: (Boolean) -> Unit,
    previousValuesMode: PreviousValuesMode,
    onPreviousValuesMode: (PreviousValuesMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText =
        stringResource(if (warmUpIncluded) Res.string.preferences_warm_up_on else Res.string.preferences_warm_up_off)
    AppCard(modifier.fillMaxWidth().testTag("preferences_training_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(Res.string.preferences_training_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .toggleable(value = warmUpIncluded, role = Role.Switch, onValueChange = onChange)
                    .semantics(mergeDescendants = true) { stateDescription = stateText }
                    .testTag("preferences_warm_up_switch"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.preferences_warm_up_label),
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.preferences_warm_up_helper),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
                AppSwitch(
                    checked = warmUpIncluded,
                    onCheckedChange = onChange,
                    contentDescription = stringResource(Res.string.preferences_warm_up_label),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            PreviousValuesSelector(previousValuesMode, onPreviousValuesMode)
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
        Text(
            text = stringResource(Res.string.preferences_previous_values_title),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        SegmentedSelector(
            options = options,
            selectedIndex = modes.indexOf(mode),
            onSelect = { onSelect(modes[it]) },
            modifier = Modifier.testTag("preferences_previous_mode"),
        )
    }
}
