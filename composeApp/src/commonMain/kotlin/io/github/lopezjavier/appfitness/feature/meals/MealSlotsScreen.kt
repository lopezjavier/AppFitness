package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.app.InterceptTopBarBack
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.meals_add
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.meals_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.meals_none_enabled
import io.github.lopezjavier.appfitness.composeapp.resources.meals_offline_message
import io.github.lopezjavier.appfitness.composeapp.resources.meals_save
import io.github.lopezjavier.appfitness.composeapp.resources.meals_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.meals_save_rejected
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.ReorderableColumn
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 4
private val SkeletonRowHeight = Spacing.xl * 5

/**
 * Meals editor (US-23): one card per meal (name, switch, Subir / Bajar, drag handle), "Añadir comida" at the end and
 * "Guardar" pinned at the bottom. Back with unsaved edits asks before discarding. [viewedDayIsToday] only picks the
 * wording of the "has entries" hint. The top bar belongs to the shell (`meals_back`, `meals_title`).
 */
@Composable
fun MealSlotsScreen(
    state: MealSlotsUiState,
    onEvent: (MealSlotsEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewedDayIsToday: Boolean = true,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { onEvent(MealSlotsEvent.Back) },
    )
    InterceptTopBarBack(enabled = true) { onEvent(MealSlotsEvent.Back) }
    Box(modifier.fillMaxSize().testTag("meals_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    MealSlotsUiState.Loading -> MealsLoading()
                    MealSlotsUiState.LoadError -> MealsLoadError { onEvent(MealSlotsEvent.Retry) }
                    is MealSlotsUiState.Editing -> MealsForm(state, viewedDayIsToday, onEvent)
                }
            }
            if (state is MealSlotsUiState.Editing) MealsFooter(state, onEvent)
        }
    }
    if (state is MealSlotsUiState.Editing && state.showDiscardConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.meals_discard_title),
            message = stringResource(Res.string.meals_discard_message),
            confirmLabel = stringResource(Res.string.meals_discard_confirm),
            cancelLabel = stringResource(Res.string.meals_discard_cancel),
            onConfirm = { onEvent(MealSlotsEvent.ConfirmDiscard) },
            onDismiss = { onEvent(MealSlotsEvent.DismissDiscard) },
            testTagPrefix = "meals_discard",
        )
    }
}

@Composable
private fun MealsForm(state: MealSlotsUiState.Editing, viewedDayIsToday: Boolean, onEvent: (MealSlotsEvent) -> Unit) {
    val context = MealRowContext(interactive = !state.isSaving, viewedDayIsToday = viewedDayIsToday)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        ReorderableColumn(
            items = state.rows,
            key = { it.key },
            onMove = { from, to -> onEvent(MealSlotsEvent.MoveTo(from, to)) },
            modifier = Modifier.fillMaxWidth(),
        ) { row, index, _ ->
            MealSlotRow(row, index, context, onEvent)
        }
        SecondaryButton(
            text = stringResource(Res.string.meals_add),
            onClick = { onEvent(MealSlotsEvent.Add()) },
            enabled = context.interactive,
            modifier = Modifier.fillMaxWidth().testTag("meals_add"),
        )
    }
}

/** The save error or the "enable one" reason, the offline banner and "Guardar", pinned at the bottom. */
@Composable
private fun MealsFooter(state: MealSlotsUiState.Editing, onEvent: (MealSlotsEvent) -> Unit) {
    Column {
        if (!state.isOnline) {
            OfflineBanner(
                modifier = Modifier.fillMaxWidth(),
                message = stringResource(Res.string.meals_offline_message),
                testTagPrefix = "meals_offline_banner",
            )
        }
        val message =
            when {
                state.saveError != null -> saveErrorText(state.saveError)
                state.noneEnabled -> stringResource(Res.string.meals_none_enabled)
                else -> null
            }
        if (message != null) {
            Text(
                text = message,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(if (state.saveError != null) "meals_save_error" else "meals_none_enabled"),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.meals_save),
            onClick = { onEvent(MealSlotsEvent.Save) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("meals_save"),
        )
    }
}

@Composable
private fun saveErrorText(error: MealSlotsSaveError): String = stringResource(
    when (error) {
        MealSlotsSaveError.Offline -> Res.string.meals_offline_message
        MealSlotsSaveError.Rejected -> Res.string.meals_save_rejected
        MealSlotsSaveError.Failed -> Res.string.meals_save_failed
    },
)

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
