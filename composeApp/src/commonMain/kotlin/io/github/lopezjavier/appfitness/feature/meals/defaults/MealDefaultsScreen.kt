package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.app.InterceptTopBarBack
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_add
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_all_taken
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_note
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_offline
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_save
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.meals_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.weekdayLetter
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_CARDS = 3
private val SkeletonCardHeight = Spacing.xl * 5

/**
 * Default comidas editor (US-51): one card per meal with its assigned saved comidas, "Añadir comida" per card, the
 * explanatory note and "Guardar" pinned at the bottom. Back with unsaved changes asks before discarding. The top bar
 * belongs to the shell (`meal_defaults_back`).
 */
@Composable
fun MealDefaultsScreen(
    state: MealDefaultsUiState,
    onEvent: (MealDefaultsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { onEvent(MealDefaultsEvent.Back) },
    )
    InterceptTopBarBack(enabled = true) { onEvent(MealDefaultsEvent.Back) }
    Box(modifier.fillMaxSize().testTag("meal_defaults_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    MealDefaultsUiState.Loading -> DefaultsLoading()
                    MealDefaultsUiState.LoadError -> DefaultsLoadError { onEvent(MealDefaultsEvent.Retry) }
                    is MealDefaultsUiState.Content -> DefaultsList(state, onEvent)
                }
            }
            if (state is MealDefaultsUiState.Content) DefaultsFooter(state, onEvent)
        }
    }
    if (state is MealDefaultsUiState.Content) DefaultsOverlays(state, onEvent)
}

@Composable
private fun DefaultsOverlays(state: MealDefaultsUiState.Content, onEvent: (MealDefaultsEvent) -> Unit) {
    state.picker?.let { DefaultComidaPickerSheet(it, state.comidas, state.hasSavedComidas, onEvent) }
    state.sheet?.let { AssignDefaultSheet(it, onEvent) }
    if (state.showDiscardConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.meals_discard_title),
            message = stringResource(Res.string.meals_discard_message),
            confirmLabel = stringResource(Res.string.meals_discard_confirm),
            cancelLabel = stringResource(Res.string.meals_discard_cancel),
            onConfirm = { onEvent(MealDefaultsEvent.ConfirmDiscard) },
            onDismiss = { onEvent(MealDefaultsEvent.DismissDiscard) },
            testTagPrefix = "meal_defaults_discard",
        )
    }
}

@Composable
private fun DefaultsList(state: MealDefaultsUiState.Content, onEvent: (MealDefaultsEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        state.meals.forEach { MealCard(it, interactive = !state.isSaving, onEvent) }
        Text(
            text = stringResource(Res.string.meal_defaults_note),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xl - Spacing.cardGap).testTag("meal_defaults_note"),
        )
    }
}

@Composable
private fun MealCard(card: MealDefaultsCardUi, interactive: Boolean, onEvent: (MealDefaultsEvent) -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag("meal_defaults_card_${card.slotId}")) {
        Text(
            text = card.label,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        card.assignments.forEach { AssignmentRow(card, it, interactive, onEvent) }
        GhostButton(
            text = stringResource(Res.string.meal_defaults_add),
            onClick = { onEvent(MealDefaultsEvent.OpenPicker(card.slotId)) },
            enabled = card.canAdd && interactive,
            modifier = Modifier.padding(top = Spacing.sm).testTag("meal_defaults_add_${card.slotId}"),
        )
        if (!card.canAdd) {
            Text(
                text = stringResource(Res.string.meal_defaults_all_taken),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs).testTag("meal_defaults_all_taken_${card.slotId}"),
            )
        }
    }
}

@Composable
private fun AssignmentRow(
    card: MealDefaultsCardUi,
    assignment: AssignmentUi,
    interactive: Boolean,
    onEvent: (MealDefaultsEvent) -> Unit,
) {
    val id = "${card.slotId}_${assignment.templateId}"
    val days = assignment.days.sorted().map { weekdayLetter(it) }.joinToString(" ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(enabled = interactive, role = Role.Button) {
                    onEvent(MealDefaultsEvent.OpenAssignment(card.slotId, assignment.templateId))
                }
                .testTag("meal_defaults_assignment_$id")
                .padding(vertical = Spacing.sm),
        ) {
            Text(assignment.name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text(
                text = comidaSummary(assignment.foodCount, assignment.kcal),
                style = AppTheme.typography.bodySmall.numeric(),
                color = AppTheme.colors.textSecondary,
            )
            Text(days, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
        AppIconButton(
            contentDescription = stringResource(Res.string.meal_defaults_remove_cd, assignment.name, card.label),
            icon = Res.drawable.ic_close,
            testTag = "meal_defaults_remove_$id",
            onClick = { onEvent(MealDefaultsEvent.Remove(card.slotId, assignment.templateId)) },
            enabled = interactive,
        )
    }
}

/** The offline banner, the save error and "Guardar", pinned at the bottom. */
@Composable
private fun DefaultsFooter(state: MealDefaultsUiState.Content, onEvent: (MealDefaultsEvent) -> Unit) {
    Column {
        if (!state.isOnline) {
            OfflineBanner(
                modifier = Modifier.fillMaxWidth(),
                message = stringResource(Res.string.meal_defaults_offline),
                testTagPrefix = "meal_defaults_offline_banner",
            )
        }
        state.saveError?.let { error ->
            Text(
                text = stringResource(
                    if (error == MealDefaultsSaveError.Offline) {
                        Res.string.meal_defaults_offline
                    } else {
                        Res.string.meal_defaults_save_failed
                    },
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("meal_defaults_save_error"),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.meal_defaults_save),
            onClick = { onEvent(MealDefaultsEvent.Save) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("meal_defaults_save"),
        )
    }
}

@Composable
private fun DefaultsLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("meal_defaults_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_CARDS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCardHeight), AppShapes.lg) }
    }
}

@Composable
private fun DefaultsLoadError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.meal_defaults_load_error),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "meal_defaults_error",
            actionTestTag = "meal_defaults_retry",
        )
    }
}
