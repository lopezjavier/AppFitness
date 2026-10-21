package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_message
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_menu_delete
import io.github.lopezjavier.appfitness.composeapp.resources.template_menu_edit
import io.github.lopezjavier.appfitness.composeapp.resources.template_menu_rename
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_submit
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_title
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.feature.food.FoodSearchEvent
import io.github.lopezjavier.appfitness.feature.food.FoodSearchUiState
import io.github.lopezjavier.appfitness.feature.food.RenameDraft
import io.github.lopezjavier.appfitness.feature.food.TemplatesResults
import io.github.lopezjavier.appfitness.feature.routines.ActionsSheet
import io.github.lopezjavier.appfitness.feature.routines.SheetActionRow
import org.jetbrains.compose.resources.stringResource

/**
 * The sheets and dialog opened from the rows of the Comidas tab, each only while its state is set: the options of the
 * row ([FoodSearchUiState.manage] `menuFor`), the rename sheet and the delete confirmation. The names come from the
 * loaded rows; a comida that left the list closes nothing and shows nothing.
 */
@Composable
internal fun TemplateManageSheets(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    val rows = (state.templates as? TemplatesResults.Content)?.rows.orEmpty()
    state.manage.menuFor?.let { id ->
        rows.firstOrNull { it.template.id == id }?.let { row ->
            TemplateMenuSheet(id, row.template.name, state.isOffline, onEvent)
        }
    }
    state.manage.rename?.let { draft ->
        RenameTemplateSheet(draft, state.isOffline, onEvent)
    }
    state.manage.deleteFor?.let { id ->
        rows.firstOrNull { it.template.id == id }?.let { row ->
            AppConfirmDialog(
                title = stringResource(Res.string.template_delete_title),
                message = stringResource(Res.string.template_delete_message, row.template.name),
                confirmLabel = stringResource(Res.string.template_delete_confirm),
                cancelLabel = stringResource(Res.string.template_delete_cancel),
                onConfirm = { onEvent(FoodSearchEvent.ConfirmDeleteTemplate) },
                onDismiss = { onEvent(FoodSearchEvent.DismissDeleteTemplate) },
                testTagPrefix = "template_delete",
            )
        }
    }
}

/**
 * The options of one comida: "Editar", "Renombrar" and "Eliminar" (destructive). Renaming and deleting need the server,
 * so those two rows are disabled offline.
 */
@Composable
internal fun TemplateMenuSheet(
    templateId: String,
    name: String,
    isOffline: Boolean,
    onEvent: (FoodSearchEvent) -> Unit,
) {
    ActionsSheet(
        title = name,
        onDismiss = { onEvent(FoodSearchEvent.DismissTemplateMenu) },
        testTagPrefix = "food_search_template_menu_sheet",
    ) {
        SheetActionRow(
            label = stringResource(Res.string.template_menu_edit),
            onClick = { onEvent(FoodSearchEvent.EditTemplate(templateId)) },
            testTag = "food_search_template_menu_edit",
        )
        SheetActionRow(
            label = stringResource(Res.string.template_menu_rename),
            onClick = { onEvent(FoodSearchEvent.RenameTemplate(templateId)) },
            testTag = "food_search_template_menu_rename",
            enabled = !isOffline,
        )
        SheetActionRow(
            label = stringResource(Res.string.template_menu_delete),
            onClick = { onEvent(FoodSearchEvent.DeleteTemplate(templateId)) },
            testTag = "food_search_template_menu_delete",
            enabled = !isOffline,
            destructive = true,
        )
    }
}

/** The rename sheet: the name prefilled, the duplicate error inline, "Renombrar" disabled when it cannot be saved. */
@Composable
internal fun RenameTemplateSheet(draft: RenameDraft, isOffline: Boolean, onEvent: (FoodSearchEvent) -> Unit) {
    TemplateNameSheet(
        copy = NameSheetCopy(
            title = stringResource(Res.string.template_rename_title),
            label = stringResource(Res.string.template_rename_name_label),
            submitText = stringResource(Res.string.template_rename_submit),
            fieldTag = "rename_template_name",
            submitTag = "rename_template_submit",
        ),
        field = NameSheetField(draft.name, draft.nameError, canSubmit = draft.canSubmit && !isOffline),
        onNameChange = { onEvent(FoodSearchEvent.RenameNameChanged(it)) },
        onSubmit = { onEvent(FoodSearchEvent.SubmitRename) },
        onDismiss = { onEvent(FoodSearchEvent.DismissRename) },
    )
}
