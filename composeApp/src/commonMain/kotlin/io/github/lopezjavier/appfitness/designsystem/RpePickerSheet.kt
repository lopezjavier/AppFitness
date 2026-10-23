@file:Suppress("MatchingDeclarationName") // The file holds the component and its defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_clear
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_done
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_hint
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_option_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_title
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.stringResource

/** Choices and tags of the RPE picker. */
object RpePickerDefaults {
    const val COLUMNS = 3

    /** The nine options in display order (ascending, row-major). */
    val Options: List<Double> = RpeScale.values

    /** "rpe_picker_option_7_5" for 7.5 and "rpe_picker_option_10" for 10. */
    fun optionTag(value: Double): String = "rpe_picker_option_" + RpeScale.text(value).replace(',', '_')

    /** "Quitar RPE" only makes sense while a value is staged. */
    fun showsClear(staged: Double?): Boolean = staged != null

    /** "Guardar" is enabled only when the staged value differs from the current one (including cleared). */
    fun canSave(current: Double?, staged: Double?): Boolean = current != staged
}

/**
 * Bottom sheet to choose the RPE (6 to 10 in half points) of set [setNumber]. A tap only stages the choice;
 * "Guardar" reports it through [onDone] (null = no RPE). "Quitar RPE" clears only the staged value. The close
 * button, back, scrim and a swipe call [onDismiss] without changing anything.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun RpePickerSheet(
    current: Double?,
    setNumber: Int,
    onDone: (Double?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var staged by rememberSaveable(current) { mutableStateOf(current) }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "rpe_picker") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.rpe_picker_title, AppNumberFormat.integer(setNumber)),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.rpe_picker_close_cd),
                    icon = Res.drawable.ic_close,
                    testTag = "rpe_picker_close",
                    onClick = onDismiss,
                )
            }
            Text(
                text = stringResource(Res.string.rpe_picker_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
            Column(Modifier.selectableGroup()) {
                RpePickerDefaults.Options.chunked(RpePickerDefaults.COLUMNS).forEach { rowValues ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        rowValues.forEach { value -> RpeOption(value, value == staged, { staged = value }) }
                    }
                }
            }
            if (RpePickerDefaults.showsClear(staged)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouchTarget)
                        .clickable(role = Role.Button) { staged = null }
                        .testTag("rpe_picker_clear"),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(Res.string.rpe_picker_clear),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            PrimaryButton(
                text = stringResource(Res.string.rpe_picker_done),
                onClick = { onDone(staged) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("rpe_picker_done"),
                enabled = RpePickerDefaults.canSave(current, staged),
            )
        }
    }
}

@Composable
private fun RowScope.RpeOption(value: Double, selected: Boolean, onClick: () -> Unit) {
    val text = RpeScale.text(value)
    val description = stringResource(Res.string.rpe_picker_option_cd, text)
    AppChip(
        label = text,
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .semantics { contentDescription = description }
            .testTag(RpePickerDefaults.optionTag(value)),
    )
}
