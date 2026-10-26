package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_discard
import io.github.lopezjavier.appfitness.composeapp.resources.action_keep_editing
import io.github.lopezjavier.appfitness.composeapp.resources.action_save
import io.github.lopezjavier.appfitness.composeapp.resources.discard_changes_message
import io.github.lopezjavier.appfitness.composeapp.resources.discard_changes_title
import org.jetbrains.compose.resources.stringResource

/** The optional destructive button of an [AppEditorScreen] (for example "Eliminar"), under "Guardar". */
@Immutable
data class EditorDestructiveAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** Child tags of an [AppEditorScreen] with a given prefix. */
object AppEditorScreenDefaults {
    fun saveTag(prefix: String): String = "${prefix}_save"

    fun deleteTag(prefix: String): String = "${prefix}_delete"

    fun discardDialogTag(prefix: String): String = "${prefix}_discard_dialog"

    fun discardConfirmTag(prefix: String): String = "${prefix}_discard_confirm"

    fun keepEditingTag(prefix: String): String = "${prefix}_keep_editing"
}

/**
 * Editor template (R-10..R-14): an [AppScreen] with "Guardar" pinned in the footer. Editing never applies silently:
 *  - "Guardar" is enabled only when [isDirty] (or [saveEnabledWhenClean]), not [isSaving] and [isOnline]; offline it
 *    stays disabled with the "Sin conexión: no se puede guardar" hint, and tapping it vibrates one Reject.
 *  - Back (the bar's arrow and the system back) leaves silently when clean; when dirty it asks "¿Descartar los
 *    cambios?" ([Descartar] / [Seguir editando]). The dialog survives recreation.
 *  - When [saved] turns true the editor closes exactly once and vibrates one Confirm.
 * [canSave] false (an invalid draft) keeps "Guardar" disabled even while dirty. [handleSystemBack] false leaves the
 * system back to the host (a sample embedded in a larger screen).
 * The caller owns the state: this template renders it and reports events ([onSave], [onClose]).
 * Tags: `<prefix>_save`, `<prefix>_delete`, `<prefix>_discard_dialog`, `<prefix>_discard_confirm`,
 * `<prefix>_keep_editing` besides the [AppScreen] ones.
 */
@Suppress("LongParameterList", "LongMethod") // Template slots fixed by the T-118 contract.
@Composable
fun AppEditorScreen(
    title: String,
    isDirty: Boolean,
    isSaving: Boolean,
    isOnline: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    saved: Boolean = false,
    saveEnabledWhenClean: Boolean = false,
    canSave: Boolean = true,
    handleSystemBack: Boolean = true,
    destructiveAction: EditorDestructiveAction? = null,
    actions: List<AppTopBarAction> = emptyList(),
    maxContentWidth: Dp = WindowWidthClassDefaults.FormContentMaxWidth,
    testTagPrefix: String = AppScreenDefaults.TestTag,
    content: @Composable (PaddingValues) -> Unit,
) {
    val state = rememberSaveable(saver = EditorDiscardState.Saver) { EditorDiscardState() }
    state.isDirty = isDirty
    state.isSaving = isSaving
    state.online = isOnline
    state.valid = canSave
    val haptics = LocalAppHaptics.current
    val currentOnClose = rememberUpdatedState(onClose)
    val goBack: () -> Unit = {
        if (state.onBack() == BackResult.Leave) currentOnClose.value()
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = handleSystemBack,
        onBackCompleted = goBack,
    )
    LaunchedEffect(saved) {
        if (saved && state.onSaveSuccess()) {
            haptics.confirm()
            currentOnClose.value()
        }
    }
    AppScreen(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = goBack,
        actions = actions,
        maxContentWidth = maxContentWidth,
        testTagPrefix = testTagPrefix,
        footer = {
            EditorFooter(
                canSave = state.canSave(saveEnabledWhenClean),
                isSaving = isSaving,
                isOnline = isOnline,
                onSave = onSave,
                destructiveAction = destructiveAction,
                testTagPrefix = testTagPrefix,
            )
        },
        content = content,
    )
    if (state.showDiscardDialog) {
        AppConfirmDialog(
            title = stringResource(Res.string.discard_changes_title),
            message = stringResource(Res.string.discard_changes_message),
            confirmLabel = stringResource(Res.string.action_discard),
            cancelLabel = stringResource(Res.string.action_keep_editing),
            onConfirm = {
                state.confirmDiscard()
                currentOnClose.value()
            },
            onDismiss = state::keepEditing,
            testTagPrefix = AppEditorScreenDefaults.discardDialogTag(testTagPrefix),
            confirmTestTag = AppEditorScreenDefaults.discardConfirmTag(testTagPrefix),
            cancelTestTag = AppEditorScreenDefaults.keepEditingTag(testTagPrefix),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun EditorFooter(
    canSave: Boolean,
    isSaving: Boolean,
    isOnline: Boolean,
    onSave: () -> Unit,
    destructiveAction: EditorDestructiveAction?,
    testTagPrefix: String,
) {
    val haptics = LocalAppHaptics.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box {
            PrimaryButton(
                text = stringResource(Res.string.action_save),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().testTag(AppEditorScreenDefaults.saveTag(testTagPrefix)),
                enabled = canSave,
                loading = isSaving,
            )
            // Offline the button is inert: a transparent layer over it turns the refused tap into one Reject (R-29).
            if (!isOnline) {
                Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { haptics.reject() } })
            }
        }
        if (!isOnline) OfflineHint(action = stringResource(Res.string.action_save).lowercase())
        if (destructiveAction != null) {
            GhostButton(
                text = destructiveAction.label,
                onClick = destructiveAction.onClick,
                modifier = Modifier.fillMaxWidth().testTag(AppEditorScreenDefaults.deleteTag(testTagPrefix)),
                enabled = destructiveAction.enabled,
                tone = GhostTone.Destructive,
            )
        }
    }
}
