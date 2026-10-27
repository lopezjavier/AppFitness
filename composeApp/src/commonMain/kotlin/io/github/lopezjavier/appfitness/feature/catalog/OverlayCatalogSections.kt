package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_date_picker_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_dialog_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_edit_apply_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_edit_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_food_entry_detail
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_food_entry_name
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_food_sheet_brand
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_food_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_info_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_name_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_picker_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_bottom_sheet
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_confirm_dialog
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_date_picker
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_logo
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_edit_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_info_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_name_item
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_name_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_picker_option
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.profile_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_message
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_title
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.ActionRow
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppDatePickerDialog
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppLogo
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FoodSheet
import io.github.lopezjavier.appfitness.designsystem.FoodSheetContext
import io.github.lopezjavier.appfitness.designsystem.FoodSheetTotals
import io.github.lopezjavier.appfitness.designsystem.NameSheet
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Catalog sections of the overlay components: the bottom sheet and the dialogs (T-027). */
internal fun LazyListScope.overlayItems() {
    item { BottomSheetSection() }
    item { ConfirmDialogSection() }
    item { DatePickerDialogSection() }
    item { LogoSection() }
}

private const val SHEET_EDIT = "edit"
private const val SHEET_EDIT_APPLY = "edit_apply"
private const val SHEET_PICKER = "picker"
private const val SHEET_INFO = "info"
private const val SHEET_NAME = "name"
private const val SHEET_FOOD = "food"
private val CatalogTakenNames = listOf("Torso", "Pierna")

@Composable
private fun BottomSheetSection() {
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    CatalogSection(stringResource(Res.string.catalog_section_bottom_sheet), "catalog_section_bottom_sheet") {
        listOf(
            Triple(SHEET_EDIT, Res.string.catalog_edit_sheet_open, "catalog_edit_sheet_open"),
            Triple(SHEET_EDIT_APPLY, Res.string.catalog_edit_apply_sheet_open, "catalog_edit_apply_sheet_open"),
            Triple(SHEET_PICKER, Res.string.catalog_picker_sheet_open, "catalog_picker_sheet_open"),
            Triple(SHEET_INFO, Res.string.catalog_info_sheet_open, "catalog_info_sheet_open"),
            Triple(SHEET_NAME, Res.string.catalog_name_sheet_open, "catalog_name_sheet_open"),
            Triple(SHEET_FOOD, Res.string.catalog_food_sheet_open, "catalog_food_sheet_open"),
        ).forEach { (key, label, tag) ->
            SecondaryButton(
                text = stringResource(label),
                onClick = { active = key },
                modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        }
    }
    val close = { active = null }
    when (active) {
        SHEET_EDIT -> CatalogEditSheet(SheetCommit.Save, "catalog_edit_sheet", close)

        SHEET_EDIT_APPLY -> CatalogEditSheet(SheetCommit.Apply, "catalog_edit_apply_sheet", close)

        SHEET_PICKER -> AppPickerSheet(
            title = stringResource(Res.string.catalog_sheet_picker_title),
            onDismiss = close,
            testTagPrefix = "catalog_picker_sheet",
        ) {
            ActionRow(
                icon = Res.drawable.ic_check,
                title = stringResource(Res.string.catalog_sheet_picker_option),
                hint = null,
                onClick = close,
                testTag = "catalog_picker_sheet_option",
            )
        }

        SHEET_INFO -> AppInfoSheet(
            title = stringResource(Res.string.catalog_sheet_info_title),
            onDismiss = close,
            testTagPrefix = "catalog_info_sheet",
        ) {
            Text(
                text = stringResource(Res.string.wip_message),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
        }

        SHEET_FOOD -> CatalogFoodSheet(close)

        SHEET_NAME -> NameSheet(
            title = stringResource(Res.string.catalog_sheet_name_title),
            label = stringResource(Res.string.profile_name_label),
            itemLabel = stringResource(Res.string.catalog_sheet_name_item),
            existingNames = CatalogTakenNames,
            onSave = { close() },
            onDismiss = close,
            testTagPrefix = "catalog_name_sheet",
        )
    }
}

@Composable
private fun CatalogFoodSheet(close: () -> Unit) {
    FoodSheet(
        name = stringResource(Res.string.catalog_food_entry_name),
        brand = stringResource(Res.string.catalog_food_sheet_brand),
        quantity = {
            Text(stringResource(Res.string.catalog_food_entry_detail), style = AppTheme.typography.bodyLarge)
        },
        totals = FoodSheetTotals("165", "6", "28", "3,5"),
        details = {
            Text(
                text = stringResource(Res.string.wip_message),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        },
        extra = null,
        context = FoodSheetContext.Edit(
            canSave = true,
            saving = false,
            onSave = close,
            onDelete = close,
            canDelete = true,
            offlineHint = null,
        ),
        onDismiss = close,
        testTagPrefix = "catalog_food_sheet",
    )
}

@Composable
private fun CatalogEditSheet(commit: SheetCommit, prefix: String, close: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AppEditSheet(
        title = stringResource(Res.string.catalog_sheet_edit_title),
        commit = commit,
        onCommit = close,
        onDismiss = close,
        commitEnabled = text.isNotBlank(),
        testTagPrefix = prefix,
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(Res.string.profile_name_label),
            testTagPrefix = "${prefix}_input",
        )
    }
}

@Composable
private fun ConfirmDialogSection() {
    var open by rememberSaveable { mutableStateOf(false) }
    CatalogSection(stringResource(Res.string.catalog_section_confirm_dialog), "catalog_section_confirm_dialog") {
        SecondaryButton(
            text = stringResource(Res.string.catalog_dialog_open),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("catalog_dialog_open"),
        )
    }
    if (open) {
        AppConfirmDialog(
            title = stringResource(Res.string.sign_out_dialog_title),
            message = stringResource(Res.string.sign_out_dialog_message),
            confirmLabel = stringResource(Res.string.sign_out_dialog_confirm),
            cancelLabel = stringResource(Res.string.action_cancel),
            onConfirm = { open = false },
            onDismiss = { open = false },
            testTagPrefix = "catalog_dialog",
        )
    }
}

@Composable
private fun LogoSection() {
    CatalogSection(stringResource(Res.string.catalog_section_logo), "catalog_section_logo") {
        AppLogo(modifier = Modifier.testTag("catalog_logo"))
    }
}

@Composable
private fun DatePickerDialogSection() {
    var open by rememberSaveable { mutableStateOf(false) }
    CatalogSection(stringResource(Res.string.catalog_section_date_picker), "catalog_section_date_picker") {
        SecondaryButton(
            text = stringResource(Res.string.catalog_date_picker_open),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("catalog_date_picker_open"),
        )
    }
    if (open) {
        AppDatePickerDialog(
            selected = CatalogPickerDate,
            onConfirm = { open = false },
            onDismiss = { open = false },
            confirmLabel = stringResource(Res.string.action_done),
            dismissLabel = stringResource(Res.string.action_cancel),
            maxDate = CatalogPickerMaxDate,
            testTagPrefix = "catalog_date_picker",
        )
    }
}

private val CatalogPickerDate = LocalDate(2026, 10, 1)
private val CatalogPickerMaxDate = LocalDate(2026, 10, 20)
