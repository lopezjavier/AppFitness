@file:Suppress("MatchingDeclarationName") // The file holds the component and its defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_clear
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_done
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

    /** "Quitar tipo" only makes sense while the staged type is not Normal. */
    fun showsClear(staged: SetType): Boolean = staged != SetType.Working
}

/**
 * Bottom sheet to choose a set type. A tap only stages the choice; "Listo" reports it through [onDone] and the
 * caller applies it (the builder stages it into its draft, saved by its own "Guardar"). The close button, back,
 * scrim and a swipe call [onDismiss] without changing anything. "Quitar tipo" stages Normal.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun SetTypePickerSheet(
    current: SetType,
    onDone: (SetType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stagedOrdinal by rememberSaveable(current) { mutableIntStateOf(current.ordinal) }
    val staged = SetType.entries[stagedOrdinal]
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "set_type_picker") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.set_type_picker_title),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.set_type_close_cd),
                    icon = Res.drawable.ic_close,
                    testTag = "set_type_picker_close",
                    onClick = onDismiss,
                )
            }
            Column(Modifier.selectableGroup()) {
                SetTypePickerOptions(staged) { stagedOrdinal = it.ordinal }
            }
            if (SetTypePickerDefaults.showsClear(staged)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouchTarget)
                        .clickable(role = Role.Button) { stagedOrdinal = SetType.Working.ordinal }
                        .testTag("set_type_picker_clear"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(Res.string.set_type_clear),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            PrimaryButton(
                text = stringResource(Res.string.set_type_done),
                onClick = { onDone(staged) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("set_type_picker_done"),
            )
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
                Box(Modifier.size(SetRowDefaults.IndexBadgeSize))
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
