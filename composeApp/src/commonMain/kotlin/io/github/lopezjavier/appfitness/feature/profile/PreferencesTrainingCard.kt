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
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_helper
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_label
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_off
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_warm_up_on
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * "Entrenamiento" card: one row whose whole 48 dp area toggles "Incluir calentamiento". The switch is drawn inside
 * but hidden from semantics, so there is a single focus target (the row). Nothing is applied until "Guardar".
 */
@Composable
internal fun TrainingCard(warmUpIncluded: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
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
        }
    }
}
