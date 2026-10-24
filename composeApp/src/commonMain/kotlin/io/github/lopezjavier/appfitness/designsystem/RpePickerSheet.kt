@file:Suppress("MatchingDeclarationName") // The file holds the component and its defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_cd
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_hint
import io.github.lopezjavier.appfitness.composeapp.resources.rpe_picker_title
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.jetbrains.compose.resources.stringResource

/** Choices and tags of the RPE picker. */
object RpePickerDefaults {
    /** The ten options in display order: 10 (hardest) at the top down to 1. */
    val Options: List<Double> = RpeScale.values.reversed()

    /** "rpe_picker_option_8" for 8 and "rpe_picker_option_10" for 10 (whole numbers only). */
    fun optionTag(value: Double): String = "rpe_picker_option_" + RpeScale.text(value)
}

/**
 * Bottom sheet to choose the RPE (whole numbers 1 to 10) of set [setNumber] (single choice, ADR-55): a tap reports
 * the value through [onSelect] and the caller applies it and closes the sheet. The close button, back, scrim and a
 * swipe call [onDismiss] without changing anything. [completesSet] shows the hint that picking also completes the set.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun RpePickerSheet(
    current: Double?,
    setNumber: Int,
    onSelect: (Double) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    completesSet: Boolean = false,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "rpe_picker") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            RpePickerHeader(setNumber, onDismiss)
            if (completesSet) {
                Text(
                    text = stringResource(Res.string.rpe_picker_hint),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RpePickerDefaults.Options.forEach { value ->
                    RpeOptionRow(value = value, selected = value == current, onClick = { onSelect(value) })
                }
            }
        }
    }
}

@Composable
private fun RpePickerHeader(setNumber: Int, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.rpe_picker_title, AppNumberFormat.integer(setNumber)),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        HelpButton(
            contentDescription = stringResource(Res.string.help_rpe_cd),
            title = stringResource(Res.string.help_rpe_title),
            body = stringResource(Res.string.help_rpe_body),
            testTag = "live_rpe_help",
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.rpe_picker_close_cd),
            icon = Res.drawable.ic_close,
            testTag = "rpe_picker_close",
            onClick = onDismiss,
        )
    }
}
