package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck

/** Why the name cannot be saved yet. A blank name disables the save without a message. */
enum class TemplateNameError { TooLong, Taken }

/** What the save-as-comida sheet shows. */
sealed interface SaveTemplateUiState {
    /** Reading the diary section. */
    data object Loading : SaveTemplateUiState

    /** The section could not be read; the sheet offers a retry. */
    data object Error : SaveTemplateUiState

    data class Content(val content: SaveTemplateContent) : SaveTemplateUiState
}

/**
 * The sheet for the section of a meal: the typed [name] and its [nameCheck], whether the name is [nameTaken] by an
 * existing comida, the [itemCount] of the section's entries, and the offline and saving flags.
 */
data class SaveTemplateContent(
    val name: String,
    val nameCheck: TemplateNameCheck,
    val nameTaken: Boolean,
    val itemCount: Int,
    val isOffline: Boolean,
    val saving: Boolean,
) {
    /** The inline error under the field, if any. */
    val nameError: TemplateNameError?
        get() = when {
            nameTaken -> TemplateNameError.Taken
            nameCheck == TemplateNameCheck.TooLong -> TemplateNameError.TooLong
            else -> null
        }

    /** The save button is enabled: a valid, untaken name, a non-empty section, and the server is reachable. */
    val canSave: Boolean
        get() = nameCheck == TemplateNameCheck.Valid && !nameTaken && itemCount > 0 && !isOffline && !saving
}

sealed interface SaveTemplateEvent {
    data class NameChanged(val text: String) : SaveTemplateEvent

    data object Submit : SaveTemplateEvent

    data object Retry : SaveTemplateEvent
}

sealed interface SaveTemplateEffect {
    /** The comida was created: give haptic feedback and close the sheet. */
    data object Saved : SaveTemplateEffect

    /** Saving failed for another reason than a taken name; the sheet stays open with the input. */
    data class SaveFailed(val error: AppError?) : SaveTemplateEffect
}
