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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_add_object
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_all_taken
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_note
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_offline
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.weekdayLetter
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_CARDS = 3
private const val TAG = "meal_defaults"
private val SkeletonCardHeight = Spacing.xl * 5

/**
 * Default comidas editor (US-51) on [AppEditorScreen]: one card per meal with its assigned saved comidas,
 * "Añadir comida por defecto" per card, the explanatory note and "Guardar" pinned at the bottom. The template owns
 * the discard dialog, the offline hint and the close once [MealDefaultsUiState.Content.saved]. A failed save is a
 * one-shot message (R-28).
 */
@Composable
fun MealDefaultsScreen(
    state: MealDefaultsUiState,
    onEvent: (MealDefaultsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val title = stringResource(Res.string.meal_defaults_title)
    if (state !is MealDefaultsUiState.Content) {
        AppScreen(title = title, modifier = modifier, onBack = onClose, testTagPrefix = TAG) {
            Box(Modifier.fillMaxSize().testTag("meal_defaults_screen")) {
                when (state) {
                    MealDefaultsUiState.Loading -> DefaultsLoading()
                    else -> DefaultsLoadError { onEvent(MealDefaultsEvent.Retry) }
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
        onSave = { onEvent(MealDefaultsEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = state.saved,
        testTagPrefix = TAG,
    ) {
        Box(Modifier.fillMaxSize().testTag("meal_defaults_screen")) { DefaultsList(state, onEvent) }
    }
    DefaultsOverlays(state, onEvent)
}

/** Turns a failed save into one message each time it happens (R-28); the draft stays on screen. */
@Composable
private fun SaveErrorMessage(error: MealDefaultsSaveError?) {
    val messages = LocalAppMessages.current
    val text = error?.let {
        stringResource(
            if (it ==
                MealDefaultsSaveError.Offline
            ) {
                Res.string.meal_defaults_offline
            } else {
                Res.string.meal_defaults_save_failed
            },
        )
    }
    LaunchedEffect(error) { if (text != null) messages.show(text) }
}

@Composable
private fun DefaultsOverlays(state: MealDefaultsUiState.Content, onEvent: (MealDefaultsEvent) -> Unit) {
    state.picker?.let { DefaultComidaPickerSheet(it, state.comidas, state.hasSavedComidas, onEvent) }
    state.sheet?.let { AssignDefaultSheet(it, onEvent) }
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
        AddItemButton(
            objectLabel = stringResource(Res.string.meal_defaults_add_object),
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
