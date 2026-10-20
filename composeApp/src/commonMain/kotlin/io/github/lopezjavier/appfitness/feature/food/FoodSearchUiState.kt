package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.model.Food

/** What the search box shows when it is empty. Hidden while a query is typed. */
enum class FoodSearchTab { Recent, MostEaten, Favourites }

/** The list under the search box. [Error] also covers being offline (see [FoodSearchUiState.isOffline]). */
sealed interface FoodResults {
    data object Loading : FoodResults

    data class Content(val foods: List<Food>) : FoodResults

    /** Nothing found for the query, or the selected tab has no foods yet. */
    data object Empty : FoodResults

    data object Error : FoodResults
}

/**
 * State of the food search. With a non-blank [query] [results] are the search results and [tab] is ignored; with a
 * blank one they are the content of [tab]. [favouriteIds] drives the stars; [isOffline] disables the search box.
 */
data class FoodSearchUiState(
    val query: String = "",
    val tab: FoodSearchTab = FoodSearchTab.Recent,
    val results: FoodResults = FoodResults.Loading,
    val favouriteIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
    val notFoundBarcode: String? = null,
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
}
