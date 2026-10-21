@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.lopezjavier.appfitness.composeapp.resources.template_save_as_new
import io.github.lopezjavier.appfitness.composeapp.resources.template_save_choice_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_save_overwrite
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

/** Two stacked full-width buttons: "Sobrescribir" (primary) and "Guardar como nueva". */
@Composable
internal fun SaveChoiceSheet(onOverwrite: () -> Unit, onSaveAsNew: () -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss, testTagPrefix = "template_save_choice_sheet") {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.template_save_choice_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            PrimaryButton(
                text = stringResource(Res.string.template_save_overwrite),
                onClick = onOverwrite,
                modifier = Modifier.fillMaxWidth().testTag("template_save_overwrite"),
            )
            SecondaryButton(
                text = stringResource(Res.string.template_save_as_new),
                onClick = onSaveAsNew,
                modifier = Modifier.fillMaxWidth().testTag("template_save_as_new"),
            )
        }
    }
}
