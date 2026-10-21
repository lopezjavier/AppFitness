package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck

/** Which sheet of the editor is open: none, the "Guardar" choice, or the name of a new comida. */
enum class EditorSheet { None, Choose, NewName }

/** What the editor shows. */
sealed interface TemplateEditorUiState {
    /** Reading the comida. */
    data object Loading : TemplateEditorUiState

    /** The comida could not be read (or no longer exists); the editor offers a retry. */
    data object Error : TemplateEditorUiState

    data class Content(val content: TemplateEditorContent) : TemplateEditorUiState
}

/**
 * One item of the comida, identified by its [index] in the saved comida (the same food may appear twice):
 * its typed amount in the base [unit] and its [quantityError], if any.
 */
data class EditorRow(
    val index: Int,
    val foodId: String,
    val name: String,
    val unit: String,
    val amountText: String,
    val quantityError: QuantityCheck?,
    val removable: Boolean,
)

/**
 * The editor of a comida. [rows] are the foods left in it. [newName] is checked by [newNameCheck] and may be
 * [newNameTaken]. [isOffline] and [saving] disable the writes.
 */
data class TemplateEditorContent(
    val name: String,
    val rows: List<EditorRow>,
    val sheet: EditorSheet,
    val newName: String,
    val newNameCheck: TemplateNameCheck,
    val newNameTaken: Boolean,
    val isOffline: Boolean,
    val saving: Boolean,
) {
    /** Every amount is valid and at least one food is left; the writes need the server. */
    val canSave: Boolean
        get() = rows.isNotEmpty() && rows.all { it.quantityError == null } && !isOffline && !saving

    /** The inline error under the new-name field, if any. */
    val newNameError: TemplateNameError?
        get() = when {
            newNameTaken -> TemplateNameError.Taken
            newNameCheck == TemplateNameCheck.TooLong -> TemplateNameError.TooLong
            else -> null
        }

    /** "Guardar como nueva" is enabled: a valid, untaken name and a savable comida. */
    val canSubmitNewName: Boolean
        get() = canSave && newNameCheck == TemplateNameCheck.Valid && !newNameTaken
}

sealed interface TemplateEditorEvent {
    data class AmountChanged(val index: Int, val text: String) : TemplateEditorEvent

    data class RemoveFood(val index: Int) : TemplateEditorEvent

    /** "Guardar": opens the choice between overwriting and saving as a new comida. */
    data object SaveClicked : TemplateEditorEvent

    /** "Sobrescribir": replaces the items of this comida, keeping its name. */
    data object Overwrite : TemplateEditorEvent

    /** "Guardar como nueva": asks for the name of the new comida. */
    data object SaveAsNew : TemplateEditorEvent

    data class NewNameChanged(val text: String) : TemplateEditorEvent

    data object SubmitNewName : TemplateEditorEvent

    data object DismissSheet : TemplateEditorEvent

    data object Retry : TemplateEditorEvent
}

sealed interface TemplateEditorEffect {
    /** The comida was saved: give haptic feedback and leave the editor. */
    data object Saved : TemplateEditorEffect

    /** Saving failed for another reason than a taken name; the editor stays open with the draft. */
    data class SaveFailed(val error: AppError?) : TemplateEditorEffect
}
