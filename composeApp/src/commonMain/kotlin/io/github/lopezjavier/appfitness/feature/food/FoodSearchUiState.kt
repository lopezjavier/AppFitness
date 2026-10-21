package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck
import io.github.lopezjavier.appfitness.feature.templates.TemplateNameError

/**
 * What the search box shows when it is empty. Hidden while a query is typed. [Templates] is the "Comidas" tab: its
 * list is [FoodSearchUiState.templates], not food results.
 */
enum class FoodSearchTab { Recent, MostEaten, Favourites, Templates }

/** The list under the search box. [Error] also covers being offline (see [FoodSearchUiState.isOffline]). */
sealed interface FoodResults {
    data object Loading : FoodResults

    data class Content(val foods: List<Food>) : FoodResults

    /** Nothing found for the query, or the selected tab has no foods yet. */
    data object Empty : FoodResults

    data object Error : FoodResults
}

/** One comida of the "Comidas" tab with its total kcal (the sum of its items at their current food values). */
data class TemplateSummary(val template: MealTemplate, val kcal: Double)

/** The "Comidas" tab list. [Error] also covers being offline. */
sealed interface TemplatesResults {
    data object Loading : TemplatesResults

    data class Content(val rows: List<TemplateSummary>) : TemplatesResults

    data object Empty : TemplatesResults

    data object Error : TemplatesResults
}

/**
 * The rename sheet of a comida: the typed [name], its [nameCheck] and whether it is [nameTaken] by another comida.
 * Renaming needs the server, so the sheet is disabled offline by the screen.
 */
data class RenameDraft(
    val templateId: String,
    val name: String,
    val nameTaken: Boolean,
    val nameCheck: TemplateNameCheck,
) {
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

/**
 * The open sheets and dialogs of the Comidas tab: the options sheet of [menuFor], the rename sheet [rename] and the
 * delete confirmation of [deleteFor] (each null when closed).
 */
data class TemplateManageState(
    val menuFor: String? = null,
    val rename: RenameDraft? = null,
    val deleteFor: String? = null,
)

/**
 * State of the food search. With a non-blank [query] [results] are the search results and [tab] is ignored; with a
 * blank one they are the content of [tab] (or [templates] on the Comidas tab). [favouriteIds] drives the stars;
 * [isOffline] disables the search box. [manage] holds the comida sheets opened from the Comidas rows.
 */
data class FoodSearchUiState(
    val query: String = "",
    val tab: FoodSearchTab = FoodSearchTab.Recent,
    val results: FoodResults = FoodResults.Loading,
    val favouriteIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
    val notFoundBarcode: String? = null,
    val templates: TemplatesResults = TemplatesResults.Loading,
    val manage: TemplateManageState = TemplateManageState(),
)

sealed interface FoodSearchEvent {
    data class QueryChanged(val text: String) : FoodSearchEvent

    data class TabSelected(val tab: FoodSearchTab) : FoodSearchEvent

    /** The star of a result was tapped: flips the favourite state, optimistically. */
    data class ToggleFavourite(val foodId: String) : FoodSearchEvent

    /** A result row was tapped. */
    data class SelectFood(val food: Food) : FoodSearchEvent

    /** Reload after an error. */
    data object Retry : FoodSearchEvent

    /** The scan button was tapped (ignored offline). */
    data object ScanClicked : FoodSearchEvent

    /** "Crear alimento" of the not-found card: opens the form with the scanned code. */
    data object CreateFromNotFound : FoodSearchEvent

    /** "Crear alimento" of the empty state: opens the form with the typed query as the name. */
    data object CreateFromEmpty : FoodSearchEvent

    /** "Añadir" of a comida of the Comidas tab: logs all its foods to the meal of this search. */
    data class AddTemplate(val templateId: String) : FoodSearchEvent

    /** The overflow button of a comida row: opens its options sheet. */
    data class OpenTemplateMenu(val templateId: String) : FoodSearchEvent

    data object DismissTemplateMenu : FoodSearchEvent

    /** "Editar" of the options sheet: opens the editor of the comida. */
    data class EditTemplate(val templateId: String) : FoodSearchEvent

    /** "Renombrar" of the options sheet: opens the rename sheet prefilled with the current name. */
    data class RenameTemplate(val templateId: String) : FoodSearchEvent

    data class RenameNameChanged(val text: String) : FoodSearchEvent

    data object SubmitRename : FoodSearchEvent

    data object DismissRename : FoodSearchEvent

    /** "Eliminar" of the options sheet: asks for confirmation first. */
    data class DeleteTemplate(val templateId: String) : FoodSearchEvent

    data object ConfirmDeleteTemplate : FoodSearchEvent

    data object DismissDeleteTemplate : FoodSearchEvent

    /** Reloads the Comidas list, e.g. when the editor comes back with changes. */
    data object RefreshTemplates : FoodSearchEvent
}

sealed interface FoodSearchEffect {
    /** Open the add-food sheet for [food]. */
    data class OpenFood(val food: Food) : FoodSearchEffect

    /** A favourite change was rolled back because the server refused it. */
    data object FavouriteFailed : FoodSearchEffect

    /** Open the create-food form, prefilled with [barcode] and/or [name] when not null. */
    data class OpenCreateFood(val barcode: String?, val name: String?) : FoodSearchEffect

    /** The scanner failed or the product lookup failed (generic retry message). */
    data object ScanFailed : FoodSearchEffect

    /** The comida's foods were logged to the Diary: give haptic feedback and go back to the Diary. */
    data object TemplateAdded : FoodSearchEffect

    /** The comida could not be added (null when the cause is not known); nothing was logged. */
    data class TemplateFailed(val error: AppError?) : FoodSearchEffect

    /** Open the editor of the comida [templateId]. */
    data class OpenTemplateEditor(val templateId: String) : FoodSearchEffect

    /** A rename or delete of a comida was refused by the server (generic message; the sheet stays open). */
    data object TemplateManageFailed : FoodSearchEffect
}
