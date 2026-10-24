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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_failed
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_title
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_too_many_sets
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.SaveWorkoutAsRoutine
import org.jetbrains.compose.resources.stringResource

/**
 * "Guardar como rutina" (US-37): the routine name (prefilled with the workout's) and a pinned "Guardar". Closing it
 * (X, scrim, swipe, back) discards the name. An exercise with more than 10 sets is explained inline and blocks Guardar.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun SaveAsRoutineSheet(state: SaveAsRoutineState, onEvent: (SessionDetailEvent) -> Unit) {
    val dismiss = { onEvent(SessionDetailEvent.DismissSaveAsRoutine) }
    AppBottomSheet(onDismissRequest = dismiss, testTagPrefix = "save_as_routine_sheet") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.save_as_routine_title),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.save_as_routine_close_cd),
                    icon = Res.drawable.ic_close,
                    testTag = "save_as_routine_close",
                    onClick = dismiss,
                )
            }
            AppTextField(
                value = state.name,
                onValueChange = { onEvent(SessionDetailEvent.SaveAsRoutineNameChanged(it)) },
                label = stringResource(Res.string.save_as_routine_name_label),
                modifier = Modifier.fillMaxWidth(),
                maxLength = SaveWorkoutAsRoutine.NAME_MAX_LENGTH,
                testTagPrefix = "save_as_routine_name",
            )
            if (state.tooManySets) {
                Text(
                    text = stringResource(Res.string.save_as_routine_too_many_sets),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("save_as_routine_too_many_sets"),
                )
            }
            if (state.failed) {
                Text(
                    text = stringResource(Res.string.save_as_routine_failed),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("save_as_routine_failed"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.save_as_routine_confirm),
                onClick = { onEvent(SessionDetailEvent.ConfirmSaveAsRoutine) },
                modifier = Modifier.fillMaxWidth().testTag("save_as_routine_confirm"),
                enabled = state.canConfirm,
                loading = state.busy,
            )
        }
    }
}
