package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.FavoriteFoodDto
import io.github.lopezjavier.appfitness.data.remote.FavoriteInsertDto
import io.github.lopezjavier.appfitness.data.remote.FoodDto
import io.github.lopezjavier.appfitness.data.remote.FoodSearchResponseDto
import io.github.lopezjavier.appfitness.data.remote.LoggedFoodDto
import io.github.lopezjavier.appfitness.data.remote.MostEatenDto
import io.github.lopezjavier.appfitness.data.remote.MostEatenParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json

private const val RECENT_ENTRIES_LIMIT = 200
private const val RECENT_FOODS_LIMIT = 30

/**
 * Food catalogue over `food-search` (text search), `foods` (read through RLS) and the signed-in user's
 * `favorite_foods`. The favourite ids are cached so the UI can flip a star without re-reading the list.
 */
class SupabaseFoodRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
    private val clock: Clock,
) : FoodRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val favouriteIds = MutableStateFlow<Set<String>?>(null)

    /** Foods seen in a list, so opening one from a list needs no extra request. */
    private val known = MutableStateFlow<Map<String, Food>>(emptyMap())

    override suspend fun search(query: String, page: Int): Result<List<Food>> = auth.ownRow {
        val response = functions.invoke("food-search") {
            method = HttpMethod.Get
            url {
                parameters.append("q", query)
                parameters.append("page", page.toString())
            }
        }
        json.decodeFromString<FoodSearchResponseDto>(response.bodyAsText()).results.map { it.toDomain() }.remembered()
    }

    override suspend fun recent(days: Int): Result<List<Food>> = auth.ownRow {
        val since = clock.today().minus(days, DateTimeUnit.DAY)
        val ids = postgrest.from("food_log_entries")
            .select(Columns.raw("food_id")) {
                filter { gte("log_date", since.toString()) }
                order("created_at", Order.DESCENDING)
                limit(RECENT_ENTRIES_LIMIT.toLong())
            }
            .decodeList<LoggedFoodDto>()
            .map { it.foodId }
            .distinct()
            .take(RECENT_FOODS_LIMIT)
        foodsInOrder(ids)
    }

    override suspend fun mostEaten(limit: Int): Result<List<Food>> = auth.ownRow {
        val ids = postgrest.rpc("most_eaten_foods", MostEatenParams(limit)).decodeList<MostEatenDto>().map { it.foodId }
        foodsInOrder(ids)
    }

    override suspend fun favourites(): Result<List<Food>> = auth.ownRow {
        val foods = postgrest.from("favorite_foods")
            .select(Columns.raw(FavoriteFoodDto.COLUMNS)) { order("created_at", Order.DESCENDING) }
            .decodeList<FavoriteFoodDto>()
            .mapNotNull { it.foods?.toDomain() }
        favouriteIds.value = foods.mapTo(mutableSetOf()) { it.id }
        foods.remembered()
    }

    override fun favouriteIds(): Flow<Set<String>> = favouriteIds.filterNotNull()

    override suspend fun setFavourite(foodId: String, favourite: Boolean): Result<Unit> = auth.ownRow {
        if (favourite) {
            postgrest.from("favorite_foods").upsert(FavoriteInsertDto(foodId)) {
                onConflict = "user_id,food_id"
                ignoreDuplicates = true
            }
        } else {
            postgrest.from("favorite_foods").delete { filter { eq("food_id", foodId) } }
        }
        favouriteIds.update { (it.orEmpty().let { ids -> if (favourite) ids + foodId else ids - foodId }) }
    }

    override suspend fun getById(id: String): Result<Food> = auth.ownRow {
        known.value[id] ?: postgrest.from("foods")
            .select(Columns.raw(FoodDto.COLUMNS)) { filter { eq("id", id) } }
            .decodeList<FoodDto>()
            .firstOrNull()
            ?.toDomain()
            ?.also { listOf(it).remembered() }
            ?: throw AppException(AppError.NotFound)
    }

    private fun List<Food>.remembered(): List<Food> = also { foods ->
        known.update { it + foods.associateBy(Food::id) }
    }

    /** The foods with these [ids], in the order of [ids]; ids that are not readable are skipped. */
    private suspend fun foodsInOrder(ids: List<String>): List<Food> {
        if (ids.isEmpty()) return emptyList()
        val byId = postgrest.from("foods")
            .select(Columns.raw(FoodDto.COLUMNS)) { filter { isIn("id", ids) } }
            .decodeList<FoodDto>()
            .associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }.remembered()
    }
}
