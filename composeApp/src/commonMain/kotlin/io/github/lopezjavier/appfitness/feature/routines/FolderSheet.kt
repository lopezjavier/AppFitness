package io.github.lopezjavier.appfitness.feature.routines

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
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_name_blank
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_save
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_sheet_title_create
import io.github.lopezjavier.appfitness.composeapp.resources.routines_folder_sheet_title_rename
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import org.jetbrains.compose.resources.stringResource

/**
 * Create / rename folder sheet: a name field and a pinned "Guardar" (disabled while blank). Closing it (X, back,
 * scrim, swipe) discards the draft; the draft itself lives in the ViewModel (ADR-40).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun FolderSheet(sheet: FolderSheetState, onEvent: (RoutinesEvent) -> Unit) {
    val dismiss = { onEvent(RoutinesEvent.DismissFolderSheet) }
    AppBottomSheet(onDismissRequest = dismiss, testTagPrefix = "routines_folder_sheet") {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (sheet.mode == FolderSheetMode.Create) {
                            Res.string.routines_folder_sheet_title_create
                        } else {
                            Res.string.routines_folder_sheet_title_rename
                        },
                    ),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.routines_folder_close_cd),
                    icon = Res.drawable.ic_close,
                    testTag = "routines_folder_sheet_dismiss",
                    onClick = dismiss,
                )
            }
            AppTextField(
                value = sheet.name,
                onValueChange = { onEvent(RoutinesEvent.FolderNameChanged(it)) },
                label = stringResource(Res.string.routines_folder_name_label),
                modifier = Modifier.fillMaxWidth(),
                maxLength = RoutineBounds.NAME_MAX_LENGTH,
                errorText = if (sheet.showBlankError) stringResource(Res.string.routines_folder_name_blank) else null,
                testTagPrefix = "routines_folder_name_field",
            )
            PrimaryButton(
                text = stringResource(Res.string.routines_folder_save),
                onClick = { onEvent(RoutinesEvent.SaveFolder) },
                modifier = Modifier.fillMaxWidth().testTag("routines_folder_save_button"),
                enabled = sheet.canSave,
            )
        }
    }
}
