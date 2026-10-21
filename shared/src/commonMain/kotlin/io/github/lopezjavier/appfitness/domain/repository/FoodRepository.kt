package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewFood
import kotlinx.coroutines.flow.Flow

/** The food catalogue and the signed-in user's favourites. A failure carries an `AppException`. */
interface FoodRepository {
    /** Text search through the `food-search` Edge Function (online only). */
    suspend fun search(query: String, page: Int = 1): Result<List<Food>>

    /** Foods logged in the last [days] days, most recently logged first, without repeats. */
    suspend fun recent(days: Int = DEFAULT_RECENT_DAYS): Result<List<Food>>

    /** The user's most logged foods, most logged first. */
    suspend fun mostEaten(limit: Int = DEFAULT_MOST_EATEN): Result<List<Food>>

    /** The user's favourite foods, most recently starred first; also refreshes [favouriteIds]. */
    suspend fun favourites(): Result<List<Food>>

    /** The ids of the favourite foods; emits once [favourites] or [setFavourite] has run, then every change. */
    fun favouriteIds(): Flow<Set<String>>

    suspend fun setFavourite(foodId: String, favourite: Boolean): Result<Unit>

    /** One food with its portions; fails with `AppError.NotFound` when it does not exist or is not readable. */
    suspend fun getById(id: String): Result<Food>

    /** The product with this barcode through `food-barcode`; `Result.success(null)` when no product has it. */
    suspend fun findByBarcode(code: String): Result<Food?>

    /**
     * The extra nutrients of [food] per 100 g / 100 ml (empty when it has none); fails with `AppError.NotFound` when
     * the food is not readable. Cached per food, so reopening a food needs no request.
     */
    suspend fun micronutrients(food: Food): Result<Micronutrients>

    /** Creates a personal food with its portions in one atomic call and returns it as stored. */
    suspend fun createFood(food: NewFood): Result<Food>

    companion object {
        const val DEFAULT_RECENT_DAYS = 14
        const val DEFAULT_MOST_EATEN = 20
    }
}
