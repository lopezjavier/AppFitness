@file:Suppress("MatchingDeclarationName") // The file holds the component and its defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_set_type_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_set_type_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_set_normal
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_picker_title
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The choices of the picker in display order. */
object SetTypePickerDefaults {
    val Options: List<SetType> = listOf(SetType.WarmUp, SetType.Working, SetType.Failure, SetType.Drop)

    /** "set_type_picker_option_warm_up" etc.; matches the DB value of the type. */
    fun optionTag(type: SetType): String = "set_type_picker_option_" + when (type) {
        SetType.Working -> "working"
        SetType.WarmUp -> "warm_up"
        SetType.Failure -> "failure"
        SetType.Drop -> "drop"
    }
}

/**
 * Bottom sheet to choose a set type (single choice, ADR-55): a tap on an option reports it through [onSelect] and
 * the caller applies it and closes the sheet (the builder edits its draft, saved by its own "Guardar"). The close
 * button, back, scrim and a swipe call [onDismiss] without changing anything. "Normal" removes the type.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun SetTypePickerSheet(
    current: SetType,
    onSelect: (SetType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPickerSheet(
        title = stringResource(Res.string.set_type_picker_title),
        onDismiss = onDismiss,
        modifier = modifier,
        help = {
            HelpButton(
                contentDescription = stringResource(Res.string.help_set_type_cd),
                title = stringResource(Res.string.set_type_picker_title),
                body = stringResource(Res.string.help_set_type_body),
                testTag = "set_type_help",
            )
        },
        testTagPrefix = "set_type_picker",
    ) {
        Column(Modifier.selectableGroup()) {
            SetTypePickerOptions(current, onSelect)
        }
    }
}

@Composable
private fun SetTypePickerOptions(staged: SetType, onSelect: (SetType) -> Unit) {
    SetTypePickerDefaults.Options.forEach { type ->
        val selected = type == staged
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(type) })
                .testTag(SetTypePickerDefaults.optionTag(type)),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (type == SetType.Working) {
                // Decorative: the "Normal" label sits right next to it.
                Icon(
                    painter = painterResource(Res.drawable.ic_set_normal),
                    contentDescription = null,
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(SetRowDefaults.IndexBadgeSize).testTag("set_type_picker_normal_icon"),
                )
            } else {
                SetTypeBadge(type = type, number = "")
            }
            Text(
                text = stringResource(SetTypeBadgeDefaults.labelResource(type)),
                style = AppTheme.typography.bodyLarge,
                color = if (selected) AppTheme.colors.neonLime else AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                // Decorative: the selected state is conveyed by the selectable semantics.
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = AppTheme.colors.neonLime,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
        }
    }
}
