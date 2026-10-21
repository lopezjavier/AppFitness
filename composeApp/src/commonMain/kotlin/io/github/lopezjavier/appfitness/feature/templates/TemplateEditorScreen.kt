@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_quantity_error
import io.github.lopezjavier.appfitness.composeapp.resources.ic_remove
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_amount_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_choice_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_new_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_new_name_submit
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_new_name_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_overwrite
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_save
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_save_as_new
import io.github.lopezjavier.appfitness.composeapp.resources.template_editor_save_failed
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

private val SkeletonHeight = Spacing.xl * 2
private val AmountWidth = Spacing.xl * 3

/**
 * The full-screen editor of a comida (US-21): the foods with their amounts, "Guardar" pinned at the bottom and the
 * choice between overwriting and saving as a new comida. [saveFailed] shows the generic failure above the button.
 * Writes are disabled offline; the top bar (title and back) belongs to the shell.
 */
@Composable
fun TemplateEditorScreen(
    state: TemplateEditorUiState,
    onEvent: (TemplateEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    Box(modifier.fillMaxSize().testTag("template_editor_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            when (state) {
                TemplateEditorUiState.Loading -> EditorLoading()

                TemplateEditorUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.template_editor_load_error),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(TemplateEditorEvent.Retry)
                        },
                        testTagPrefix = "template_editor_error_state",
                    )
                }

                is TemplateEditorUiState.Content -> EditorContent(state.content, onEvent, saveFailed)
            }
        }
    }
}

@Composable
private fun EditorLoading() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin).testTag("template_editor_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
private fun EditorContent(
    content: TemplateEditorContent,
    onEvent: (TemplateEditorEvent) -> Unit,
    saveFailed: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("template_editor_list"),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    text = content.name,
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("template_editor_name_hint"),
                )
            }
            if (content.isOffline) {
                item { OfflineBanner(Modifier.fillMaxWidth(), testTagPrefix = "template_editor_offline") }
            }
            items(content.rows, key = { it.index }) { row -> EditorRowItem(row, onEvent) }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (saveFailed) {
                Text(
                    text = stringResource(Res.string.template_editor_save_failed),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("template_editor_save_failed"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.template_editor_save),
                onClick = { onEvent(TemplateEditorEvent.SaveClicked) },
                enabled = content.canSave,
                loading = content.saving,
                modifier = Modifier.fillMaxWidth().testTag("template_editor_save"),
            )
        }
    }
    EditorSheets(content, onEvent)
}

/** One food: its name, the amount in the base unit, the remove button and the amount error, if any. */
@Composable
private fun EditorRowItem(row: EditorRow, onEvent: (TemplateEditorEvent) -> Unit) {
    val id = row.index
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            NumberInput(
                value = row.amountText,
                onValueChange = { onEvent(TemplateEditorEvent.AmountChanged(id, it)) },
                label = stringResource(Res.string.template_editor_amount_cd, row.name),
                modifier = Modifier.width(AmountWidth).testTag("template_editor_item_${id}_amount"),
                maxDecimals = 1,
                showLabel = false,
            )
            Text(
                text = row.unit,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.template_editor_remove_cd, row.name),
                icon = Res.drawable.ic_remove,
                testTag = "template_editor_item_${id}_remove",
                onClick = { onEvent(TemplateEditorEvent.RemoveFood(id)) },
                enabled = row.removable,
            )
        }
        if (row.quantityError != null) {
            Text(
                text = stringResource(Res.string.add_food_quantity_error),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("template_editor_item_${id}_error"),
            )
        }
    }
}

/** The "Guardar" choice (a bottom sheet) and the new-name sheet of "Guardar como nueva", when open. */
@Composable
private fun EditorSheets(content: TemplateEditorContent, onEvent: (TemplateEditorEvent) -> Unit) {
    when (content.sheet) {
        EditorSheet.None -> Unit

        EditorSheet.Choose -> ChoiceSheet(
            onOverwrite = { onEvent(TemplateEditorEvent.Overwrite) },
            onSaveAsNew = { onEvent(TemplateEditorEvent.SaveAsNew) },
            onDismiss = { onEvent(TemplateEditorEvent.DismissSheet) },
        )

        EditorSheet.NewName -> TemplateNameSheet(
            copy = NameSheetCopy(
                title = stringResource(Res.string.template_editor_new_name_title),
                label = stringResource(Res.string.template_editor_new_name_label),
                submitText = stringResource(Res.string.template_editor_new_name_submit),
                fieldTag = "template_editor_new_name",
                submitTag = "template_editor_new_name_submit",
            ),
            field = NameSheetField(content.newName, content.newNameError, content.canSubmitNewName),
            onNameChange = { onEvent(TemplateEditorEvent.NewNameChanged(it)) },
            onSubmit = { onEvent(TemplateEditorEvent.SubmitNewName) },
            onDismiss = { onEvent(TemplateEditorEvent.DismissSheet) },
        )
    }
}

/** Two stacked full-width buttons: "Sobrescribir" (primary) and "Guardar como nueva". */
@Composable
private fun ChoiceSheet(onOverwrite: () -> Unit, onSaveAsNew: () -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss, testTagPrefix = "template_editor_choice_sheet") {
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
                text = stringResource(Res.string.template_editor_choice_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            PrimaryButton(
                text = stringResource(Res.string.template_editor_overwrite),
                onClick = onOverwrite,
                modifier = Modifier.fillMaxWidth().testTag("template_editor_overwrite"),
            )
            SecondaryButton(
                text = stringResource(Res.string.template_editor_save_as_new),
                onClick = onSaveAsNew,
                modifier = Modifier.fillMaxWidth().testTag("template_editor_save_as_new"),
            )
        }
    }
}

private const val LOADING_ROWS = 3
