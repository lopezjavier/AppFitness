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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_message
import io.github.lopezjavier.appfitness.composeapp.resources.template_delete_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_edit
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_item_amount
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_rename_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_manage_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_submit
import io.github.lopezjavier.appfitness.composeapp.resources.template_rename_title
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import org.jetbrains.compose.resources.stringResource

private const val LOADING_ROWS = 4
private val SkeletonHeight = Spacing.xl * 2

/**
 * The details of a comida (US-21): its name with the rename and delete icons, the total kcal and one row per food with
 * its quantity, and "Editar" pinned at the bottom. Rename and delete are disabled offline; the top bar (title and
 * back) belongs to the shell.
 */
@Composable
fun TemplateDetailsScreen(
    state: TemplateDetailsUiState,
    onEvent: (TemplateDetailsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
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

                is TemplateDetailsUiState.Content -> DetailsContent(state, onEvent, onEdit, actionFailed)
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
    onEdit: () -> Unit,
    actionFailed: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("template_details_list"),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { DetailsHeader(content, onEvent) }
            if (content.isOffline) {
                item { OfflineBanner(Modifier.fillMaxWidth(), testTagPrefix = "template_details_offline") }
            }
            if (actionFailed) {
                item {
                    Text(
                        text = stringResource(Res.string.template_manage_failed),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.error,
                        modifier = Modifier.testTag("template_details_action_failed"),
                    )
                }
            }
            items(content.rows, key = { it.index }) { row -> DetailsRowItem(row) }
        }
        PrimaryButton(
            text = stringResource(Res.string.template_details_edit),
            onClick = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .testTag("template_details_edit"),
        )
    }
    DetailsSheets(content, onEvent)
}

/** The name with the pencil and the trash icons, and the total kcal under it. */
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
                enabled = !content.isOffline && !content.busy,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.template_details_delete_cd, content.name),
                icon = Res.drawable.ic_delete,
                testTag = "template_details_delete",
                onClick = { onEvent(TemplateDetailsEvent.Delete) },
                enabled = !content.isOffline && !content.busy,
                tint = AppTheme.colors.error,
            )
        }
        Text(
            text = stringResource(Res.string.diary_meal_kcal, wholeHalfUp(content.kcal)),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("template_details_kcal"),
        )
    }
}

/** One food: its name (and portion, if any) and the amount in the base unit. */
@Composable
private fun DetailsRowItem(row: DetailsRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .semantics(mergeDescendants = true) {}
            .testTag("template_details_item_${row.index}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** The rename sheet and the delete confirmation, each only while its state is set. */
@Composable
private fun DetailsSheets(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    content.rename?.let { draft ->
        TemplateNameSheet(
            copy = NameSheetCopy(
                title = stringResource(Res.string.template_rename_title),
                label = stringResource(Res.string.template_rename_name_label),
                submitText = stringResource(Res.string.template_rename_submit),
                fieldTag = "rename_template_name",
                submitTag = "rename_template_submit",
            ),
            field = NameSheetField(
                draft.name,
                draft.nameError,
                canSubmit = draft.canSubmit && !content.isOffline && !content.busy,
            ),
            onNameChange = { onEvent(TemplateDetailsEvent.RenameNameChanged(it)) },
            onSubmit = { onEvent(TemplateDetailsEvent.SubmitRename) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissRename) },
        )
    }
    if (content.confirmDelete) {
        AppConfirmDialog(
            title = stringResource(Res.string.template_delete_title),
            message = stringResource(Res.string.template_delete_message, content.name),
            confirmLabel = stringResource(Res.string.template_delete_confirm),
            cancelLabel = stringResource(Res.string.template_delete_cancel),
            onConfirm = { onEvent(TemplateDetailsEvent.ConfirmDelete) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissDelete) },
            testTagPrefix = "template_delete",
        )
    }
}
