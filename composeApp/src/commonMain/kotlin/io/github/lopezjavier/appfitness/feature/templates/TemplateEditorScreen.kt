package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_remove
import io.github.lopezjavier.appfitness.composeapp.resources.name_sheet_duplicate
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_items_header
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_edit_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_swipe_remove_cd
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRow
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import org.jetbrains.compose.resources.stringResource

private const val TAG = "template_edit"

/**
 * The editor of a comida on [AppEditorScreen] (R-10..R-14): the name field, the totals of the draft, one row per food
 * (tap opens its sheet with "Listo" and "Quitar de la comida"; swipe "Quitar" stages the removal) and "Añadir
 * alimento". Nothing is stored until "Guardar"; back asks "¿Descartar los cambios?" when something changed. [onClose]
 * leaves the editor (after discarding or saving: [saved] is true once the save went through).
 */
@Composable
fun TemplateEditorScreen(
    state: TemplateDetailsUiState,
    onEvent: (TemplateDetailsEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
) {
    val content = state as? TemplateDetailsUiState.Content
    AppEditorScreen(
        title = stringResource(Res.string.template_edit_title),
        isDirty = content?.isDirty == true,
        isSaving = content?.busy == true,
        isOnline = content?.isOffline != true,
        onSave = { onEvent(TemplateDetailsEvent.SaveClicked) },
        onClose = onClose,
        modifier = modifier.testTag("template_edit_screen"),
        saved = saved,
        canSave = content?.canSave ?: true,
        testTagPrefix = TAG,
    ) {
        when (state) {
            TemplateDetailsUiState.Loading -> DetailsLoading(TAG)
            TemplateDetailsUiState.Error -> DetailsLoadError(TAG) { onEvent(TemplateDetailsEvent.Retry) }
            is TemplateDetailsUiState.Content -> EditorList(state, onEvent)
        }
    }
    if (content != null) EditorSheets(content, onEvent)
}

@Composable
private fun EditorList(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("${TAG}_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "name") {
            AppTextField(
                value = content.name,
                onValueChange = { onEvent(TemplateDetailsEvent.NameChanged(it)) },
                label = stringResource(Res.string.template_details_name_label),
                errorText = content.nameError?.let { nameErrorText(it) },
                testTagPrefix = "${TAG}_name",
                imeAction = ImeAction.Done,
            )
        }
        item(key = "totals") { TotalsCard(content, TAG) }
        item(key = "header") {
            ListSectionHeader(
                title = stringResource(Res.string.template_details_items_header),
                modifier = Modifier.testTag("${TAG}_items_header"),
            )
        }
        items(content.rows, key = { it.index }) { row ->
            SwipeToDeleteRow(
                deleteLabel = stringResource(Res.string.template_swipe_remove_cd, row.name),
                enabled = row.removable,
                onDelete = { onEvent(TemplateDetailsEvent.RemoveFood(row.index)) },
                backgroundTag = "${TAG}_remove_${row.index}",
                label = stringResource(Res.string.action_remove),
            ) {
                FoodEntryRow(
                    model = row.toRowModel(),
                    onClick = { onEvent(TemplateDetailsEvent.OpenFood(row.index)) },
                    tagPrefix = "${TAG}_item",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "add") {
            AddItemButton(
                objectLabel = "alimento",
                onClick = { onEvent(TemplateDetailsEvent.AddFoodClicked) },
                modifier = Modifier.testTag("${TAG}_add_item"),
                enabled = !content.busy,
            )
        }
    }
}

@Composable
private fun nameErrorText(error: TemplateNameError): String = when (error) {
    TemplateNameError.Taken -> stringResource(Res.string.name_sheet_duplicate, "una comida")
    TemplateNameError.TooLong -> stringResource(Res.string.save_template_error_too_long)
}

/** The food sheet, only while a food is open. "Listo" applies to the draft; the editor's "Guardar" persists it. */
@Composable
private fun EditorSheets(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    content.openFood?.let { open ->
        TemplateFoodSheet(
            open = open,
            onAmountChange = { onEvent(TemplateDetailsEvent.FoodAmountChanged(it)) },
            onMethodChange = { onEvent(TemplateDetailsEvent.MethodChanged(it)) },
            onDone = { onEvent(TemplateDetailsEvent.SaveFood) },
            onRemove = { onEvent(TemplateDetailsEvent.RemoveFood(open.row.index)) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissFood) },
        )
    }
}
