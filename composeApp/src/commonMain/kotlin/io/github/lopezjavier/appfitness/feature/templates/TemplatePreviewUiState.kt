package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError

/**
 * One food of the comida as shown read-only: [amount] is in the base [unit], [portionLabel] only a display snapshot,
 * [kcal] and the macros the nutrition of that amount.
 */
data class PreviewRow(
    val foodId: String,
    val name: String,
    val unit: String,
    val amount: Double,
    val portionLabel: String?,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** What the read-only details of a comida show (US-53). */
sealed interface TemplatePreviewUiState {
    data object Loading : TemplatePreviewUiState

    /** The comida could not be read (or no longer exists); the screen offers a retry. */
    data object Error : TemplatePreviewUiState

    /**
     * The comida with its totals and one of [rows] per food (empty: the comida has no foods). [canAdd] is false while
     * offline, while [adding] and for an empty comida; [addFailed] says the last "Añadir" did not log anything.
     */
    data class Content(
        val name: String,
        val rows: List<PreviewRow>,
        val totalKcal: Double,
        val totalProteinG: Double,
        val totalCarbsG: Double,
        val totalFatG: Double,
        val isOffline: Boolean,
        val adding: Boolean,
        val addFailed: Boolean,
    ) : TemplatePreviewUiState {
        val canAdd: Boolean
            get() = !isOffline && !adding && rows.isNotEmpty()
    }
}

sealed interface TemplatePreviewEvent {
    /** "Añadir": logs every food of the comida to the meal of the route (ignored offline or while adding). */
    data object Add : TemplatePreviewEvent

    /** "Editar": opens the editor of the comida. */
    data object Edit : TemplatePreviewEvent

    /** Reload after coming back (e.g. from the editor); keeps the content on screen meanwhile. */
    data object Refresh : TemplatePreviewEvent

    /** Reload after an error. */
    data object Retry : TemplatePreviewEvent
}

sealed interface TemplatePreviewEffect {
    /** The comida's foods were logged to the Diary: give haptic feedback and go back to the Diary. */
    data object Added : TemplatePreviewEffect

    /** The comida could not be added (null when the cause is not known); nothing was logged. */
    data class Failed(val error: AppError?) : TemplatePreviewEffect

    /** Open the editor of this comida. */
    data object OpenEditor : TemplatePreviewEffect
}
