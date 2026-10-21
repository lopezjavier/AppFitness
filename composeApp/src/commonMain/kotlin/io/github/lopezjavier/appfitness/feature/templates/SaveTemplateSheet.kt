@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_failed
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_taken
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_submit
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

private val SkeletonHeight = Spacing.xl * 2

/**
 * Save-as-comida bottom sheet (US-21): names the meal section. The name field is the only input; a taken name shows
 * inline under it. [saveFailed] shows the generic failure under the button. [onDismiss] runs when the sheet closes.
 */
@Composable
fun SaveTemplateSheet(
    state: SaveTemplateUiState,
    onEvent: (SaveTemplateEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "save_template_sheet") {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.save_template_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            when (state) {
                SaveTemplateUiState.Loading -> SheetLoading()

                SaveTemplateUiState.Error -> ErrorState(
                    message = stringResource(Res.string.save_template_load_error),
                    action = AppStateAction(stringResource(Res.string.state_retry)) {
                        onEvent(SaveTemplateEvent.Retry)
                    },
                    testTagPrefix = "save_template_error_state",
                )

                is SaveTemplateUiState.Content -> SheetContent(state.content, onEvent, saveFailed)
            }
        }
    }
}

@Composable
private fun SheetLoading() {
    SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight).testTag("save_template_loading"))
}

@Composable
private fun SheetContent(content: SaveTemplateContent, onEvent: (SaveTemplateEvent) -> Unit, saveFailed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        val errorText = content.nameError?.let {
            stringResource(
                when (it) {
                    TemplateNameError.Taken -> Res.string.save_template_error_taken
                    TemplateNameError.TooLong -> Res.string.save_template_error_too_long
                },
            )
        }
        AppTextField(
            value = content.name,
            onValueChange = { onEvent(SaveTemplateEvent.NameChanged(it)) },
            label = stringResource(Res.string.save_template_name_label),
            errorText = errorText,
            enabled = !content.isOffline && !content.saving,
            onDone = { onEvent(SaveTemplateEvent.Submit) },
            testTagPrefix = "save_template_name",
        )
        if (content.isOffline) {
            Text(
                text = stringResource(Res.string.save_template_offline_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("save_template_offline"),
            )
        }
        if (saveFailed) {
            Text(
                text = stringResource(Res.string.save_template_error_failed),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("save_template_failed"),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.save_template_submit),
            onClick = { onEvent(SaveTemplateEvent.Submit) },
            enabled = content.canSave,
            loading = content.saving,
            modifier = Modifier.fillMaxWidth().testTag("save_template_submit"),
        )
    }
}
