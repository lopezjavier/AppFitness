package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck

/**
 * The rename sheet of a comida: the typed [name], its [nameCheck] and whether it is [nameTaken] by another comida.
 * Renaming needs the server, so the sheet is disabled offline by the screen.
 */
data class RenameDraft(val name: String, val nameTaken: Boolean, val nameCheck: TemplateNameCheck) {
    /** The inline error under the field, if any. */
    val nameError: TemplateNameError?
        get() = when {
            nameTaken -> TemplateNameError.Taken
            nameCheck == TemplateNameCheck.TooLong -> TemplateNameError.TooLong
            else -> null
        }

    /** The name is valid and not taken: "Renombrar" is enabled (when online). */
    val canSubmit: Boolean
        get() = nameCheck == TemplateNameCheck.Valid && !nameTaken
}

/** One item of the comida: the food [name], its [amountText] in the base [unit] and the [portionLabel], if any. */
data class DetailsRow(
    val index: Int,
    val name: String,
    val amountText: String,
    val unit: String,
    val portionLabel: String?,
)

/** What the details of a comida show. */
sealed interface TemplateDetailsUiState {
    data object Loading : TemplateDetailsUiState

    /** The comida could not be read (or no longer exists); the screen offers a retry. */
    data object Error : TemplateDetailsUiState

    /**
     * The comida with its total [kcal] and [rows]. [rename] is the open rename sheet and [confirmDelete] the open
     * delete dialog (both survive process death, ADR-40). [isOffline] disables rename and delete; [busy] is true while
     * one of them is running.
     */
    data class Content(
        val name: String,
        val kcal: Double,
        val rows: List<DetailsRow>,
        val isOffline: Boolean,
        val rename: RenameDraft?,
        val confirmDelete: Boolean,
        val busy: Boolean,
    ) : TemplateDetailsUiState
}

sealed interface TemplateDetailsEvent {
    /** The pencil: opens the rename sheet prefilled with the current name. */
    data object OpenRename : TemplateDetailsEvent

    data class RenameNameChanged(val text: String) : TemplateDetailsEvent

    data object SubmitRename : TemplateDetailsEvent

    data object DismissRename : TemplateDetailsEvent

    /** "Eliminar": asks for confirmation first. */
    data object Delete : TemplateDetailsEvent

    data object ConfirmDelete : TemplateDetailsEvent

    data object DismissDelete : TemplateDetailsEvent

    /** Reload after an error (shows the loading state). */
    data object Retry : TemplateDetailsEvent

    /** Silent reload, e.g. when the editor comes back with changes. */
    data object Refresh : TemplateDetailsEvent
}

sealed interface TemplateDetailsEffect {
    /** The comida was deleted: leave the details. */
    data object Deleted : TemplateDetailsEffect

    /** A rename or delete was refused by the server for another reason than a taken name. */
    data class ActionFailed(val error: AppError?) : TemplateDetailsEffect
}
