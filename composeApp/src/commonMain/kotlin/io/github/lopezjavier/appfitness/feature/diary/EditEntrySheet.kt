@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_delete
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_save
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.food.MealSelector
import io.github.lopezjavier.appfitness.feature.food.Preview
import io.github.lopezjavier.appfitness.feature.food.QuantityInput
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private val SkeletonHeight = Spacing.xl * 2

/**
 * Edit-entry bottom sheet (US-19, US-20): change the quantity (base unit or portion) and the meal of a logged food,
 * with the live nutrition of the new quantity, "Guardar" and "Eliminar". Saving and deleting need the server, so an
 * offline hint is shown and both are disabled then. [saveFailed] shows the error under the button; [onDismiss] runs
 * when the sheet is closed without saving.
 */
@Composable
fun EditEntrySheet(
    state: EditEntryUiState,
    onEvent: (EditEntryEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "edit_entry_sheet") {
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
            when (state) {
                EditEntryUiState.Loading -> SheetLoading()

                EditEntryUiState.Error -> ErrorState(
                    message = stringResource(Res.string.edit_entry_load_error),
                    action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(EditEntryEvent.Retry) },
                    testTagPrefix = "edit_entry_error_state",
                )

                is EditEntryUiState.Content -> SheetContent(state.content, onEvent, onDismiss, saveFailed)
            }
        }
    }
}

@Composable
private fun SheetLoading() {
    Column(
        Modifier.fillMaxWidth().testTag("edit_entry_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
private fun SheetContent(
    content: EditEntryContent,
    onEvent: (EditEntryEvent) -> Unit,
    onDismiss: () -> Unit,
    saveFailed: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = content.food.name,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("edit_entry_name"),
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.edit_entry_close_cd),
                icon = Res.drawable.ic_close,
                testTag = "edit_entry_close",
                onClick = onDismiss,
            )
        }
        QuantityInput(
            food = content.food,
            amountText = content.amountText,
            selectedPortionId = content.selectedPortionId,
            hasError = content.quantityError != null,
            onAmountChange = { onEvent(EditEntryEvent.AmountChanged(it)) },
            onPortionSelect = { onEvent(EditEntryEvent.PortionSelected(it)) },
            testTagPrefix = "edit_entry",
        )
        content.preview?.let { Preview(it.kcal, it.proteinG, it.carbsG, it.fatG, "edit_entry_preview") }
        MealSelector(
            slots = content.slots,
            selectedSlotId = content.selectedSlotId,
            onSelect = { onEvent(EditEntryEvent.SlotSelected(it)) },
            containerTag = "edit_entry_slots",
            optionTagPrefix = "edit_entry_slot",
            emptyTag = "edit_entry_no_slot",
        )
        Notices(content, saveFailed)
        PrimaryButton(
            text = stringResource(Res.string.edit_entry_save),
            onClick = { onEvent(EditEntryEvent.Save) },
            enabled = content.canSave,
            loading = content.saving,
            modifier = Modifier.fillMaxWidth().testTag("edit_entry_save"),
        )
        GhostButton(
            text = stringResource(Res.string.edit_entry_delete),
            onClick = { onEvent(EditEntryEvent.Delete) },
            enabled = !content.isOffline && !content.saving,
            tone = GhostTone.Destructive,
            modifier = Modifier.fillMaxWidth().testTag("edit_entry_delete"),
        )
    }
}

/** The offline reason (not just a greyed button) and the failed-save message, as polite live regions. */
@Composable
private fun Notices(content: EditEntryContent, saveFailed: Boolean) {
    if (content.isOffline) {
        Notice(stringResource(Res.string.edit_entry_offline_hint), "edit_entry_offline")
    }
    if (saveFailed) {
        Notice(stringResource(Res.string.edit_entry_save_error), "edit_entry_save_error")
    }
}

@Composable
private fun Notice(text: String, tag: String) {
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
    )
}
