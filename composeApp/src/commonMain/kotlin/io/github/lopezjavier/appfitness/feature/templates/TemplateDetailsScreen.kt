package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_quantity_error
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_delete
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_item_amount
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_rename_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_save
import io.github.lopezjavier.appfitness.composeapp.resources.template_manage_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_submit
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_save_new_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_save_new_name_submit
import io.github.lopezjavier.appfitness.composeapp.resources.template_save_new_name_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_swipe_delete_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.food.KcalText
import io.github.lopezjavier.appfitness.feature.food.MacroTexts
import org.jetbrains.compose.resources.stringResource

private const val LOADING_ROWS = 4
private val SkeletonHeight = Spacing.xl * 2

/**
 * The details of a comida (US-21), which is also its editor: the name with the pencil, the total kcal and macros of the
 * draft, one swipeable row per food (tap opens its sheet), "Guardar" pinned at the bottom and "Eliminar comida".
 * Nothing is stored until "Guardar"; writes are disabled offline. The top bar (title and back) belongs to the shell.
 */
@Composable
fun TemplateDetailsScreen(
    state: TemplateDetailsUiState,
    onEvent: (TemplateDetailsEvent) -> Unit,
    modifier: Modifier = Modifier,
    actionFailed: Boolean = false,
) {
    Box(modifier.fillMaxSize().testTag("template_details_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            when (state) {
                TemplateDetailsUiState.Loading -> DetailsLoading()

                TemplateDetailsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.template_details_load_error),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(TemplateDetailsEvent.Retry)
                        },
                        testTagPrefix = "template_details_error_state",
                    )
                }

                is TemplateDetailsUiState.Content -> DetailsContent(state, onEvent, actionFailed)
            }
        }
    }
}

@Composable
private fun DetailsLoading() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin).testTag("template_details_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
private fun DetailsContent(
    content: TemplateDetailsUiState.Content,
    onEvent: (TemplateDetailsEvent) -> Unit,
    actionFailed: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(top = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DetailsHeader(content, onEvent)
            if (content.isOffline) OfflineBanner(Modifier.fillMaxWidth(), testTagPrefix = "template_details_offline")
            if (actionFailed) {
                Text(
                    text = stringResource(Res.string.template_manage_failed),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("template_details_action_failed"),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("template_details_list"),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(content.rows, key = { it.index }) { row -> DetailsRowItem(row, onEvent) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PrimaryButton(
                text = stringResource(Res.string.template_details_save),
                onClick = { onEvent(TemplateDetailsEvent.SaveClicked) },
                enabled = content.canSave,
                loading = content.busy,
                modifier = Modifier.fillMaxWidth().testTag("template_details_save"),
            )
            GhostButton(
                text = stringResource(Res.string.template_details_delete),
                onClick = { onEvent(TemplateDetailsEvent.Delete) },
                enabled = !content.isOffline && !content.busy,
                tone = GhostTone.Destructive,
                modifier = Modifier.fillMaxWidth().testTag("template_details_delete"),
            )
        }
    }
    DetailsSheets(content, onEvent)
}

/** The name with the pencil, and the total kcal and macros of the draft under it. */
@Composable
private fun DetailsHeader(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = content.name,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("template_details_name"),
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.template_details_rename_cd, content.name),
                icon = Res.drawable.ic_edit,
                testTag = "template_details_rename",
                onClick = { onEvent(TemplateDetailsEvent.OpenRename) },
                enabled = !content.busy,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            KcalText(content.totalKcal, Modifier.testTag("template_details_kcal"))
            Box(Modifier.testTag("template_details_macros")) {
                MacroTexts(content.totalProteinG, content.totalCarbsG, content.totalFatG)
            }
        }
    }
}

/** One food, swipeable to the start to remove it: tap opens its sheet. */
@Composable
private fun DetailsRowItem(row: DetailsRow, onEvent: (TemplateDetailsEvent) -> Unit) {
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.template_swipe_delete_cd, row.name),
        enabled = row.removable,
        onDelete = { onEvent(TemplateDetailsEvent.RemoveFood(row.index)) },
        backgroundTag = "template_details_item_${row.index}_delete_bg",
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button) { onEvent(TemplateDetailsEvent.OpenFood(row.index)) }
                .padding(vertical = Spacing.sm)
                .testTag("template_details_item_${row.index}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    row.portionLabel?.let {
                        Text(text = it, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                    }
                }
                Text(
                    text = stringResource(Res.string.template_details_item_amount, row.amountText, row.unit),
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KcalText(row.kcal)
                MacroTexts(row.proteinG, row.carbsG, row.fatG)
            }
            if (row.quantityError != null) {
                Text(
                    text = stringResource(Res.string.add_food_quantity_error),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("template_details_item_${row.index}_error"),
                )
            }
        }
    }
}

/** The food sheet, the rename sheet, the "Guardar" choice and the new-name sheet, each only while its state is set. */
@Composable
private fun DetailsSheets(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    content.openFood?.let { open ->
        TemplateFoodSheet(
            open = open,
            onAmountChange = { onEvent(TemplateDetailsEvent.AmountChanged(open.row.index, it)) },
            onRemove = { onEvent(TemplateDetailsEvent.RemoveFood(open.row.index)) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissFood) },
        )
    }
    content.rename?.let { draft ->
        TemplateNameSheet(
            copy = NameSheetCopy(
                title = stringResource(Res.string.template_rename_title),
                label = stringResource(Res.string.template_rename_name_label),
                submitText = stringResource(Res.string.template_rename_submit),
                fieldTag = "rename_template_name",
                submitTag = "rename_template_submit",
            ),
            field = NameSheetField(draft.name, draft.nameError, canSubmit = draft.canSubmit && !content.busy),
            onNameChange = { onEvent(TemplateDetailsEvent.RenameNameChanged(it)) },
            onSubmit = { onEvent(TemplateDetailsEvent.SubmitRename) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissRename) },
        )
    }
    when (content.sheet) {
        DetailsSheet.Choose -> SaveChoiceSheet(
            onOverwrite = { onEvent(TemplateDetailsEvent.Overwrite) },
            onSaveAsNew = { onEvent(TemplateDetailsEvent.SaveAsNew) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissSheet) },
        )

        DetailsSheet.NewName -> content.newName?.let { draft ->
            TemplateNameSheet(
                copy = NameSheetCopy(
                    title = stringResource(Res.string.template_save_new_name_title),
                    label = stringResource(Res.string.template_save_new_name_label),
                    submitText = stringResource(Res.string.template_save_new_name_submit),
                    fieldTag = "new_template_name",
                    submitTag = "new_template_submit",
                ),
                field = NameSheetField(draft.name, draft.nameError, canSubmit = draft.canSubmit && !content.busy),
                onNameChange = { onEvent(TemplateDetailsEvent.NewNameChanged(it)) },
                onSubmit = { onEvent(TemplateDetailsEvent.SubmitNewName) },
                onDismiss = { onEvent(TemplateDetailsEvent.DismissSheet) },
            )
        }

        DetailsSheet.None, DetailsSheet.Rename -> Unit
    }
}
