package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.EditableFood
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.UpdatedFood
import io.github.lopezjavier.appfitness.domain.port.BarcodeScanner
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

internal fun food(
    id: String,
    name: String = "Food $id",
    kcal: Double = 100.0,
    baseUnit: String = "g",
    servingAmount: Double? = null,
    servingLabel: String? = null,
    portions: List<FoodPortion> = emptyList(),
    isManual: Boolean = false,
) = Food(
    id = id,
    name = name,
    brand = null,
    baseUnit = baseUnit,
    nutrition = NutritionPer100(kcal, 10.0, 20.0, 5.0),
    servingLabel = servingLabel,
    servingAmount = servingAmount,
    portions = portions,
    imageUrl = null,
    isManual = isManual,
)

/** Test double of [BarcodeScanner]: answers [result] and counts the scans. */
internal class FakeBarcodeScanner(var result: ScanResult = ScanResult.Cancelled) : BarcodeScanner {
    var scans = 0

    override suspend fun scan(): ScanResult {
        scans++
        return result
    }
}

/**
 * Test double of [FoodRepository]: [searchResults] answers by query (an unknown query finds nothing), the
 * recents / most eaten lists are plain properties and the favourites behave like the real cache. Every search is
 * recorded in [searches].
 */
internal class FakeFoodRepository : FoodRepository {
    val searchResults = mutableMapOf<String, Result<List<Food>>>()
    val searches = mutableListOf<String>()
    var recentFoods: Result<List<Food>> = Result.success(emptyList())
    var mostEatenFoods: Result<List<Food>> = Result.success(emptyList())
    var favouriteFoods: Result<List<Food>> = Result.success(emptyList())
    var setFavouriteResult: Result<Unit> = Result.success(Unit)
    val known = mutableMapOf<String, Food>()

    /** When set, [setFavourite] suspends until it completes. */
    var favouriteGate: CompletableDeferred<Unit>? = null
    val favouriteCalls = mutableListOf<Pair<String, Boolean>>()
    private val ids = MutableStateFlow<Set<String>?>(null)

    override suspend fun search(query: String, page: Int): Result<List<Food>> {
        searches += query
        return searchResults[query] ?: Result.success(emptyList())
    }

    override suspend fun recent(days: Int): Result<List<Food>> = recentFoods

    override suspend fun mostEaten(limit: Int): Result<List<Food>> = mostEatenFoods

    override suspend fun favourites(): Result<List<Food>> {
        favouriteFoods.getOrNull()?.let { foods -> ids.value = foods.mapTo(mutableSetOf()) { it.id } }
        return favouriteFoods
    }

    override fun favouriteIds(): Flow<Set<String>> = ids.filterNotNull()

    override suspend fun setFavourite(foodId: String, favourite: Boolean): Result<Unit> {
        favouriteCalls += foodId to favourite
        favouriteGate?.await()
        if (setFavouriteResult.isSuccess) {
            ids.value = ids.value.orEmpty().let { if (favourite) it + foodId else it - foodId }
        }
        return setFavouriteResult
    }

    override suspend fun getById(id: String): Result<Food> =
        known[id]?.let { Result.success(it) } ?: Result.failure(AppException(AppError.NotFound))

    /** Answers of [findByBarcode] by code; an unknown code finds nothing. */
    val barcodeResults = mutableMapOf<String, Result<Food?>>()
    val barcodeLookups = mutableListOf<String>()

    override suspend fun findByBarcode(code: String): Result<Food?> {
        barcodeLookups += code
        return barcodeResults[code] ?: Result.success(null)
    }

    /** Answers of [micronutrients] by food id; a food without an entry has none. */
    val micronutrientResults = mutableMapOf<String, Result<Micronutrients>>()

    /** When set, [micronutrients] suspends until it completes. */
    var micronutrientsGate: CompletableDeferred<Unit>? = null

    override suspend fun micronutrients(food: Food): Result<Micronutrients> {
        micronutrientsGate?.await()
        return micronutrientResults[food.id] ?: Result.success(Micronutrients.Empty)
    }

    /** The foods passed to [createFood]. */
    val created = mutableListOf<NewFood>()

    /** When set, [createFood] fails with it; otherwise it stores and returns a food with id "new-<n>". */
    var createFailure: Throwable? = null

    /** When set, [createFood] suspends until it completes. */
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun createFood(food: NewFood): Result<Food> {
        created += food
        createGate?.await()
        createFailure?.let { return Result.failure(it) }
        val stored = Food(
            id = "new-${created.size}",
            name = food.name,
            brand = food.brand,
            baseUnit = food.baseUnit,
            nutrition = food.nutrition,
            servingLabel = null,
            servingAmount = null,
            portions = food.portions.mapIndexed { i, p -> FoodPortion("p$i", p.label, p.amount) },
            imageUrl = null,
        )
        known[stored.id] = stored
        return Result.success(stored)
    }

    /** The stored label values (grams per 100) of the manual foods, by id. */
    val storedMicronutrients = mutableMapOf<String, Map<MicronutrientKind, Double>>()

    /** Ids referenced by an entry or a comida: their unit cannot change (the fake server refuses it). */
    val inUse = mutableSetOf<String>()

    /** How many following [editableFood] calls fail with a network error. */
    var editableFailures = 0
    val editableLoads = mutableListOf<String>()

    override suspend fun editableFood(id: String): Result<EditableFood> {
        editableLoads += id
        val manual = known[id]?.takeIf { it.isManual }
        return when {
            editableFailures > 0 -> {
                editableFailures--
                Result.failure(AppException(AppError.Network))
            }

            manual == null -> Result.failure(AppException(AppError.NotFound))

            else -> Result.success(EditableFood(manual, storedMicronutrients[id].orEmpty(), id in inUse))
        }
    }

    /** The foods passed to [updateFood]. */
    val updates = mutableListOf<UpdatedFood>()

    /** When set, [updateFood] fails with it; otherwise it replaces the stored food and returns it. */
    var updateFailure: Throwable? = null

    override suspend fun updateFood(food: UpdatedFood): Result<Food> {
        updates += food
        val unitRefused = food.id in inUse && known[food.id]?.baseUnit != food.baseUnit
        val failure = updateFailure
            ?: if (food.id !in known) {
                AppException(AppError.NotFound)
            } else if (unitRefused) {
                AppException(AppError.FoodInUse)
            } else {
                null
            }
        if (failure != null) return Result.failure(failure)
        val stored = known.getValue(food.id).copy(
            name = food.name,
            brand = food.brand,
            baseUnit = food.baseUnit,
            nutrition = food.nutrition,
            portions = food.portions.mapIndexed { i, p -> FoodPortion("u${updates.size}-$i", p.label, p.amount) },
        )
        known[stored.id] = stored
        storedMicronutrients[stored.id] =
            food.micronutrients.values.mapValues { (kind, v) -> kind.labelUnit.toGrams(v) }
        return Result.success(stored)
    }

    fun failSearch(query: String) {
        searchResults[query] = Result.failure(AppException(AppError.Network))
    }
}

/**
 * A [FoodSearchViewModel] with the template collaborators faked by default: an empty comidas list.
 */
internal fun foodSearchModel(
    repository: FoodRepository,
    connectivity: ConnectivityObserver,
    scanner: BarcodeScanner,
    state: SavedStateHandle = SavedStateHandle(),
    templates: MealTemplateRepository = FakeMealTemplateRepository(),
    pick: Boolean = false,
) = FoodSearchViewModel(
    pick = pick,
    foodRepository = repository,
    connectivity = connectivity,
    barcodeScanner = scanner,
    mealTemplates = templates,
    scaleNutrition = ScaleNutrition(),
    savedState = state,
)
