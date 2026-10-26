package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTemplate

/**
 * What the search box shows when it is empty. Hidden while a query is typed. [Templates] is the "Comidas" tab: its
 * list is [FoodSearchUiState.templates], not food results.
 */
enum class FoodSearchTab { Recent, MostEaten, Favourites, Templates }

/** The list under the search box. [Error] also covers being offline (see [FoodSearchUiState.isOffline]). */
sealed interface FoodResults {
    data object Loading : FoodResults

    /**
     * [foods] lists the user's manual foods first (the first [manualCount]), then the Open Food Facts results; the
     * manual rows are never reordered. [remoteLoading] means Open Food Facts has not answered yet, [remoteFailed]
     * that it failed (the manual rows stay and the screen shows an inline error with "Reintentar").
     */
    data class Content(
        val foods: List<Food>,
        val manualCount: Int = 0,
        val remoteLoading: Boolean = false,
        val remoteFailed: Boolean = false,
    ) : FoodResults

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
 * State of the food search. With a non-blank [query] [results] are the search results and [tab] is ignored; with a
 * blank one they are the content of [tab] (or [templates] on the Comidas tab). [favouriteIds] drives the stars;
 * [isOffline] disables the search box. In [pick] mode (choosing a food for a comida) the Comidas tab is not offered.
 */
data class FoodSearchUiState(
    val query: String = "",
    val tab: FoodSearchTab = FoodSearchTab.Recent,
    val results: FoodResults = FoodResults.Loading,
    val favouriteIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
    val notFoundBarcode: String? = null,
    val templates: TemplatesResults = TemplatesResults.Loading,
    val pick: Boolean = false,
    /**
     * The meal the foods are added to (its [MealSlot.code] and label give the title "Añadir al desayuno"); null when
     * no meal was preselected, in [pick] mode, or while it is not known yet (the title then falls back to the
     * generic "Añadir alimento").
     */
    val mealSlot: MealSlot? = null,
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

    /** A comida row was tapped: opens its read-only details. */
    data class OpenTemplate(val templateId: String) : FoodSearchEvent

    /** A comida row was swiped to the end: deletes it at once, no confirmation, no undo (ignored offline). */
    data class DeleteTemplate(val templateId: String) : FoodSearchEvent

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

    /** Open the details of the comida [templateId]. */
    data class OpenTemplateDetails(val templateId: String) : FoodSearchEffect

    /** A delete of a comida was refused by the server (generic message); the row is back in the list. */
    data object TemplateManageFailed : FoodSearchEffect
}
