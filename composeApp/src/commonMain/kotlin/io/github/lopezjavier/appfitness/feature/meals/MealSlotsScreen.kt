package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_open
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_open_hint
import io.github.lopezjavier.appfitness.composeapp.resources.meals_add_object
import io.github.lopezjavier.appfitness.composeapp.resources.meals_item_label
import io.github.lopezjavier.appfitness.composeapp.resources.meals_label_field
import io.github.lopezjavier.appfitness.composeapp.resources.meals_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.meals_offline_message
import io.github.lopezjavier.appfitness.composeapp.resources.meals_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.meals_save_rejected
import io.github.lopezjavier.appfitness.composeapp.resources.meals_sheet_title_create
import io.github.lopezjavier.appfitness.composeapp.resources.meals_sheet_title_rename
import io.github.lopezjavier.appfitness.composeapp.resources.meals_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.NameSheet
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.ReorderableColumn
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MealSlotBounds
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 4
private const val TAG = "meals"
private val SkeletonRowHeight = Spacing.xl * 5

/**
 * Meals editor (US-23, US-56) on [AppEditorScreen]: one configuration row per meal (drag handle, name, "Quitar"),
 * "Añadir comida del día" and the "Comidas por defecto" entry. Nothing is applied until "Guardar" (removals included).
 * Create and rename go through the Name sheet, which only stages the change in the draft. The template owns the
 * discard dialog, the offline hint and the close once [MealSlotsUiState.Editing.saved]; a failed save is a one-shot
 * message (R-28).
 */
@Composable
fun MealSlotsScreen(
    state: MealSlotsUiState,
    onEvent: (MealSlotsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDefaults: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val title = stringResource(Res.string.meals_title)
    if (state !is MealSlotsUiState.Editing) {
        AppScreen(title = title, modifier = modifier, onBack = onClose, testTagPrefix = TAG) {
            Box(Modifier.fillMaxSize().testTag("meals_screen")) {
                when (state) {
                    MealSlotsUiState.LoadError -> MealsLoadError { onEvent(MealSlotsEvent.Retry) }
                    else -> MealsLoading()
                }
            }
        }
        return
    }
    SaveErrorMessage(state.saveError)
    AppEditorScreen(
        title = title,
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        isOnline = state.isOnline,
        onSave = { onEvent(MealSlotsEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = state.saved,
        canSave = state.canSave,
        testTagPrefix = TAG,
    ) {
        Box(Modifier.fillMaxSize().testTag("meals_screen")) { MealsForm(state, onEvent, onOpenDefaults) }
    }
    state.sheet?.let { MealNameSheetHost(it, state.rows, onEvent) }
}

/** Turns a failed save into one message each time it happens (R-28); the edits stay on screen. */
@Composable
private fun SaveErrorMessage(error: MealSlotsSaveError?) {
    val messages = LocalAppMessages.current
    val text = error?.let {
        stringResource(
            when (it) {
                MealSlotsSaveError.Offline -> Res.string.meals_offline_message
                MealSlotsSaveError.Rejected -> Res.string.meals_save_rejected
                MealSlotsSaveError.Failed -> Res.string.meals_save_failed
            },
        )
    }
    LaunchedEffect(error) { if (text != null) messages.show(text) }
}

/** The Name sheet: it only stages a change in the draft, so it reads "Añadir" / "Listo" and is not gated by network. */
@Composable
private fun MealNameSheetHost(sheet: MealNameSheet, rows: List<MealSlotRowUi>, onEvent: (MealSlotsEvent) -> Unit) {
    val rename = sheet as? MealNameSheet.Rename
    NameSheet(
        title = stringResource(
            if (rename == null) Res.string.meals_sheet_title_create else Res.string.meals_sheet_title_rename,
        ),
        label = stringResource(Res.string.meals_label_field),
        itemLabel = stringResource(Res.string.meals_item_label),
        existingNames = rows.map { it.label },
        onSave = { onEvent(MealSlotsEvent.SubmitName(it)) },
        onDismiss = { onEvent(MealSlotsEvent.DismissSheet) },
        initialName = rename?.currentName.orEmpty(),
        maxLength = MealSlotBounds.LABEL_MAX_LENGTH,
        testTagPrefix = "meal_name_sheet",
        commit = if (rename == null) SheetCommit.Add else SheetCommit.Apply,
    )
}

@Composable
private fun MealsForm(state: MealSlotsUiState.Editing, onEvent: (MealSlotsEvent) -> Unit, onOpenDefaults: () -> Unit) {
    val context = MealRowContext(interactive = !state.isSaving)
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ReorderableColumn(
            items = state.rows,
            key = { it.key },
            onMove = { from, to -> onEvent(MealSlotsEvent.MoveTo(from, to)) },
            modifier = Modifier.fillMaxWidth(),
            onAutoScroll = { scrollState.scrollBy(it) },
        ) { row, index, _ ->
            MealSlotRow(row, index, context, onEvent)
        }
        AddItemButton(
            objectLabel = stringResource(Res.string.meals_add_object),
            onClick = { onEvent(MealSlotsEvent.Add) },
            enabled = context.interactive,
            modifier = Modifier.testTag("meals_add"),
        )
        DefaultsEntry(enabled = state.canNavigateToDefaults, onOpenDefaults)
    }
}

/** "Comidas por defecto": not clickable while the meals have unsaved changes, and it says why. */
@Composable
private fun DefaultsEntry(enabled: Boolean, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        NavigationRow(
            label = stringResource(Res.string.meal_defaults_open),
            onClick = onOpen,
            modifier = Modifier.testTag("meal_slots_defaults_button"),
            enabled = enabled,
        )
        if (!enabled) {
            Text(
                text = stringResource(Res.string.meal_defaults_open_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("meal_slots_defaults_hint"),
            )
        }
    }
}

@Composable
private fun MealsLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("meals_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.lg) }
    }
}

@Composable
private fun MealsLoadError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.meals_load_error),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "meals_error",
        )
    }
}
