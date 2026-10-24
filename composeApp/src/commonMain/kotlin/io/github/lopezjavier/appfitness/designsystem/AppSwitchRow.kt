package io.github.lopezjavier.appfitness.designsystem

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.switch_row_off
import io.github.lopezjavier.appfitness.composeapp.resources.switch_row_on
import org.jetbrains.compose.resources.stringResource

/**
 * A label with a one-line [helper] and a switch: the whole row (at least 48 dp tall) toggles, so there is a single
 * focus target. The switch is drawn inside but hidden from semantics; the row exposes `Role.Switch` and "Activado" /
 * "Desactivado". The caller's [modifier] (tag included) lands on the toggleable row.
 */
@Composable
fun AppSwitchRow(
    label: String,
    helper: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateText = stringResource(if (checked) Res.string.switch_row_on else Res.string.switch_row_off)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text(text = helper, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = label,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Preview
@Composable
private fun AppSwitchRowPreview() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AppSwitchRow("Incluir calentamiento", "Cuentan en volumen y récords.", checked = true, onCheckedChange = {})
            AppSwitchRow("Actualizar automáticamente", "Se guarda sin preguntar.", checked = false, onCheckedChange = {
            })
        }
    }
}
