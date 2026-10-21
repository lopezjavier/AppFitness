@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_taken
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_too_long
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

/** The texts and test tags of a [TemplateNameSheet]. [fieldTag] tags the field (its error is `<fieldTag>_error`). */
internal data class NameSheetCopy(
    val title: String,
    val label: String,
    val submitText: String,
    val fieldTag: String,
    val submitTag: String,
)

/** What the [TemplateNameSheet] shows of the typed name: its [error] and whether the submit is [canSubmit]. */
internal data class NameSheetField(val name: String, val error: TemplateNameError?, val canSubmit: Boolean)

/**
 * A bottom sheet that asks for one comida name (rename, or the name of a new comida): an input with its inline error,
 * and a full-width submit button at the bottom. The IME "Done" action submits when the field can submit.
 */
@Composable
internal fun TemplateNameSheet(
    copy: NameSheetCopy,
    field: NameSheetField,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss, testTagPrefix = "${copy.fieldTag}_sheet") {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = copy.title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            AppTextField(
                value = field.name,
                onValueChange = onNameChange,
                label = copy.label,
                errorText = field.error?.let { stringResource(errorText(it)) },
                onDone = { if (field.canSubmit) onSubmit() },
                testTagPrefix = copy.fieldTag,
            )
            PrimaryButton(
                text = copy.submitText,
                onClick = onSubmit,
                enabled = field.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag(copy.submitTag),
            )
        }
    }
}

private fun errorText(error: TemplateNameError) = when (error) {
    TemplateNameError.Taken -> Res.string.save_template_error_taken
    TemplateNameError.TooLong -> Res.string.save_template_error_too_long
}
