package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_discard
import io.github.lopezjavier.appfitness.composeapp.resources.action_keep_editing
import io.github.lopezjavier.appfitness.composeapp.resources.discard_changes_message
import io.github.lopezjavier.appfitness.composeapp.resources.discard_changes_title
import org.jetbrains.compose.resources.stringResource

/**
 * The shared "¿Descartar los cambios?" dialog (R-14): "Seguir editando" (neutral, first) and "Descartar"
 * (destructive). Every screen that asks before leaving unsaved work uses it. The root is tagged [testTagPrefix], the
 * buttons `<prefix>_confirm` and `<prefix>_cancel` unless [confirmTestTag] or [cancelTestTag] override them.
 */
@Composable
fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    confirmTestTag: String = AppConfirmDialogDefaults.confirmTag(testTagPrefix),
    cancelTestTag: String = AppConfirmDialogDefaults.cancelTag(testTagPrefix),
) {
    AppConfirmDialog(
        title = stringResource(Res.string.discard_changes_title),
        message = stringResource(Res.string.discard_changes_message),
        confirmLabel = stringResource(Res.string.action_discard),
        cancelLabel = stringResource(Res.string.action_keep_editing),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = testTagPrefix,
        confirmTestTag = confirmTestTag,
        cancelTestTag = cancelTestTag,
    )
}
