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
 * A label with an optional one-line [helper] (a short fact only; explanations go in [help]) and a switch: the whole
 * row (at least 48 dp tall) toggles, so there is a single focus target. An optional [help] adds the row's "?" as a
 * sibling outside the toggleable area (title [helpTitle], [helpContentDescription], tag [helpTestTag]). The switch is
 * drawn inside but hidden from semantics; the row exposes `Role.Switch` and "Activado" / "Desactivado". The caller's
 * [modifier] (tag included) lands on the toggleable row. A disabled ([enabled] false) row is inert and dimmed (for
 * example offline, with an `offlineHint`).
 */
@Composable
@Suppress("ModifierNotUsedAtRoot") // The tagged, toggleable row is the node; the optional "?" sits beside it.
fun AppSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helper: String? = null,
    help: HelpContent? = null,
    helpTitle: String = label,
    helpContentDescription: String = "",
    helpTestTag: String = "",
) {
    val stateText = stringResource(if (checked) Res.string.switch_row_on else Res.string.switch_row_off)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier
                .weight(1f)
                .heightIn(min = Spacing.minTouchTarget)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics(mergeDescendants = true) { stateDescription = stateText },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = label, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                if (helper != null) {
                    Text(text = helper, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                }
            }
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                contentDescription = label,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        if (help != null) HelpButton(helpContentDescription, helpTitle, help, helpTestTag)
    }
}

@Preview
@Composable
private fun AppSwitchRowPreview() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AppSwitchRow("Incluir calentamiento", checked = true, onCheckedChange = {}, helper = "Si pasan 2 días.")
            AppSwitchRow(
                "Actualizar automáticamente",
                checked = false,
                onCheckedChange = {},
                help = HelpContent.fromBody("Se aplica al cambiarlo."),
                helpContentDescription = "Ayuda: actualizar",
                helpTestTag = "preview_help",
            )
        }
    }
}
