package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** What leaving an editor should do. */
enum class BackResult { Leave, AskDiscard }

/**
 * Pure logic of `AppEditorScreen` (R-10..R-14): discard dialog, save gating and the single close after a save.
 * The composable updates [isDirty], [isSaving] and [online] on every recomposition; [showDiscardDialog] and the
 * close latch survive recreation through [Saver].
 */
class EditorDiscardState(showDiscardDialog: Boolean = false, closed: Boolean = false) {
    var isDirty: Boolean = false
    var isSaving: Boolean = false
    var online: Boolean = true

    /** The draft can be saved as it is (valid fields); false keeps "Guardar" disabled while it is still dirty. */
    var valid: Boolean = true

    var showDiscardDialog: Boolean by mutableStateOf(showDiscardDialog)
        private set

    var closed: Boolean = closed
        private set

    /**
     * Save is available when something changed (or [forceEnabled]), the draft is [valid], nothing is saving and the
     * device is online.
     */
    fun canSave(forceEnabled: Boolean = false): Boolean = (isDirty || forceEnabled) && valid && !isSaving && online

    /** Clean: leave silently. Dirty: open the discard dialog and stay. */
    fun onBack(): BackResult {
        if (!isDirty) return BackResult.Leave
        showDiscardDialog = true
        return BackResult.AskDiscard
    }

    /** "Descartar": closes the dialog and leaves. */
    fun confirmDiscard(): BackResult {
        showDiscardDialog = false
        return BackResult.Leave
    }

    /** "Seguir editando": closes the dialog and stays. */
    fun keepEditing() {
        showDiscardDialog = false
    }

    /** Returns true the first time (the caller closes the editor), false for any repeat. */
    fun onSaveSuccess(): Boolean {
        if (closed) return false
        closed = true
        return true
    }

    companion object {
        val Saver: Saver<EditorDiscardState, Any> =
            listSaver(
                save = { listOf(it.showDiscardDialog, it.closed) },
                restore = { EditorDiscardState(showDiscardDialog = it[0], closed = it[1]) },
            )
    }
}
