package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.session_edit_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.session_edit_invalid
import io.github.lopezjavier.appfitness.composeapp.resources.session_edit_reps
import io.github.lopezjavier.appfitness.composeapp.resources.session_edit_save
import io.github.lopezjavier.appfitness.composeapp.resources.session_edit_set_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_edit_weight
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SetRowDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Correction of one set: weight and reps, "Guardar" and "Cancelar". Typed values stay when a save fails. */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun EditSetSheet(edit: EditSetState, busy: Boolean, onEvent: (SessionDetailEvent) -> Unit) {
    // Plain local state, updated synchronously, so the number fields keep their cursor (NumberInput contract).
    var weight by remember(edit.position, edit.setIndex) { mutableStateOf(edit.weight) }
    var reps by remember(edit.position, edit.setIndex) { mutableStateOf(edit.reps) }
    AppBottomSheet(onDismissRequest = { onEvent(SessionDetailEvent.DismissEdit) }, testTagPrefix = "session_edit") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.session_edit_set_title, edit.setIndex),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            EditFields(weight, reps, onWeight = {
                weight = it
                onEvent(SessionDetailEvent.WeightChanged(it))
            }, onReps = {
                reps =
                    it
                onEvent(SessionDetailEvent.RepsChanged(it))
            })
            if (edit.invalid) {
                Text(
                    text = stringResource(Res.string.session_edit_invalid),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    }.testTag("session_edit_invalid"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.session_edit_save),
                onClick = { onEvent(SessionDetailEvent.SaveSet) },
                modifier = Modifier.fillMaxWidth().testTag("session_edit_save"),
                enabled = !busy,
                loading = busy,
            )
            SecondaryButton(
                text = stringResource(Res.string.session_edit_cancel),
                onClick = { onEvent(SessionDetailEvent.DismissEdit) },
                modifier = Modifier.fillMaxWidth().testTag("session_edit_cancel"),
                enabled = !busy,
            )
        }
    }
}

@Composable
private fun EditFields(weight: String, reps: String, onWeight: (String) -> Unit, onReps: (String) -> Unit) {
    // NumberInput fills its width, so each field needs an equal weight or the first one takes the whole row.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NumberInput(
            value = weight,
            onValueChange = onWeight,
            label = stringResource(Res.string.session_edit_weight),
            modifier = Modifier.weight(1f).testTag("session_edit_weight"),
            maxDecimals = SetRowDefaults.WEIGHT_MAX_DECIMALS,
            maxIntegerDigits = SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS,
        )
        NumberInput(
            value = reps,
            onValueChange = onReps,
            label = stringResource(Res.string.session_edit_reps),
            modifier = Modifier.weight(1f).testTag("session_edit_reps"),
            maxDecimals = SetRowDefaults.REPS_MAX_DECIMALS,
            maxIntegerDigits = SetRowDefaults.REPS_MAX_INTEGER_DIGITS,
        )
    }
}
