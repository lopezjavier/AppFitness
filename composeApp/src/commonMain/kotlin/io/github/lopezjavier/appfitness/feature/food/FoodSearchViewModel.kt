package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.port.BarcodeScanner
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_QUERY = "food_search_query"
private const val KEY_TAB = "food_search_tab"
private const val KEY_NOT_FOUND = "food_search_not_found"
private const val SEARCH_DEBOUNCE_MS = 400L

/** What to load: a text search or the content of an empty-box tab; [attempt] changes on every retry. */
private data class Request(val query: String, val tab: FoodSearchTab, val attempt: Int, val online: Boolean)

/**
 * Food search (US-17): a 400 ms debounced text search, or the recent / most eaten / favourite foods when the box is
 * empty. Query and tab live in [savedState]. The star flips optimistically and rolls back when the server refuses.
 * Searching and the tabs need the server, so offline the results are an [FoodResults.Error] and the box is disabled.
 *
 * The "Comidas" tab lists the saved meals; a row opens its details, where the comida is added to a meal.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port plus the add target; search and tabs.
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodSearchViewModel(
    private val foodRepository: FoodRepository,
    connectivity: ConnectivityObserver,
    private val barcodeScanner: BarcodeScanner,
    private val mealTemplates: MealTemplateRepository,
    private val foodPreferences: FoodPreferencesRepository,
    private val cookedItemNutrition: CookedItemNutrition,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val pick: Boolean = false,
) : ViewModel() {
    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val tab = MutableStateFlow(
        savedState.get<String>(KEY_TAB)?.let { name -> FoodSearchTab.entries.firstOrNull { it.name == name } }
            ?.takeUnless { pick && it == FoodSearchTab.Templates }
            ?: FoodSearchTab.Recent,
    )
    private val attempt = MutableStateFlow(0)
    private val favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    private val online = MutableStateFlow(true)
    private val notFoundBarcode = MutableStateFlow(savedState.get<String>(KEY_NOT_FOUND))
    private val templates = MutableStateFlow<TemplatesResults>(TemplatesResults.Loading)
    private val deleting = MutableStateFlow<Set<String>>(emptySet())
    private var scanning = false

    private val effectChannel = Channel<FoodSearchEffect>(Channel.BUFFERED)
    val effects: Flow<FoodSearchEffect> = effectChannel.receiveAsFlow()

    /** The query once typing paused for 400 ms; clearing the box is immediate. The restored query is not delayed. */
    private val settledQuery: Flow<String> = query.drop(1)
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .onStart { emit(query.value) }
        .map { it.trim() }
        .distinctUntilChanged()

    private val results: Flow<FoodResults> =
        combine(settledQuery, tab, attempt, online) { q, t, a, o -> Request(q, t, a, o) }
            // The tab is irrelevant while a query is typed: switching it must not repeat the search.
            .map { if (it.query.isBlank()) it else it.copy(tab = FoodSearchTab.Recent) }
            .distinctUntilChanged()
            .flatMapLatest { request -> load(request) }

    val uiState: StateFlow<FoodSearchUiState> =
        combine(
            combine(query, tab, results, favouriteIds, online) { q, t, r, f, o ->
                FoodSearchUiState(query = q, tab = t, results = r, favouriteIds = f, isOffline = !o)
            },
            notFoundBarcode,
            combine(templates, deleting, ::withoutDeleting),
        ) { state, code, rows -> state.copy(notFoundBarcode = code, templates = rows, pick = pick) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                FoodSearchUiState(
                    query = query.value,
                    tab = tab.value,
                    notFoundBarcode = notFoundBarcode.value,
                    templates = templates.value,
                    pick = pick,
                ),
            )

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        viewModelScope.launch { foodRepository.favouriteIds().collect { favouriteIds.value = it } }
        viewModelScope.launch { foodRepository.favourites() }
        if (tab.value == FoodSearchTab.Templates) loadTemplates()
    }

    fun onEvent(event: FoodSearchEvent) {
        when (event) {
            is FoodSearchEvent.QueryChanged -> {
                query.value = event.text
                savedState[KEY_QUERY] = event.text
                setNotFound(null)
            }

            is FoodSearchEvent.TabSelected -> if (!(pick && event.tab == FoodSearchTab.Templates)) {
                tab.value = event.tab
                savedState[KEY_TAB] = event.tab.name
                if (event.tab == FoodSearchTab.Templates) loadTemplates()
            }

            is FoodSearchEvent.ToggleFavourite -> toggleFavourite(event.foodId)

            is FoodSearchEvent.SelectFood -> effectChannel.trySend(FoodSearchEffect.OpenFood(event.food))

            FoodSearchEvent.Retry -> {
                attempt.update { it + 1 }
                if (tab.value == FoodSearchTab.Templates) loadTemplates()
            }

            FoodSearchEvent.ScanClicked -> scan()

            FoodSearchEvent.CreateFromNotFound ->
                effectChannel.trySend(FoodSearchEffect.OpenCreateFood(barcode = notFoundBarcode.value, name = null))

            FoodSearchEvent.CreateFromEmpty -> effectChannel.trySend(
                FoodSearchEffect.OpenCreateFood(barcode = null, name = query.value.trim().ifEmpty { null }),
            )

            else -> onTemplateEvent(event)
        }
    }

    /** The Comidas tab: adding a comida, opening its details, the swipe delete (no undo), and the reload. */
    private fun onTemplateEvent(event: FoodSearchEvent) {
        when (event) {
            is FoodSearchEvent.OpenTemplate -> effectChannel.trySend(
                FoodSearchEffect.OpenTemplateDetails(event.templateId),
            )

            is FoodSearchEvent.DeleteTemplate -> deleteTemplate(event.templateId)

            FoodSearchEvent.RefreshTemplates -> loadTemplates()

            else -> Unit
        }
    }

    /** The rows without the comidas whose delete is in flight; none left is the empty state. */
    private fun withoutDeleting(results: TemplatesResults, hidden: Set<String>): TemplatesResults {
        if (results !is TemplatesResults.Content || hidden.isEmpty()) return results
        val visible = results.rows.filter { it.template.id !in hidden }
        return if (visible.isEmpty()) TemplatesResults.Empty else TemplatesResults.Content(visible)
    }

    private fun deleteTemplate(templateId: String) {
        val known = (templates.value as? TemplatesResults.Content)?.rows?.any { it.template.id == templateId } == true
        if (!known || !online.value || templateId in deleting.value) return
        deleting.update { it + templateId }
        viewModelScope.launch {
            mealTemplates.delete(templateId).fold(
                onSuccess = {
                    // Drop the row for good before it stops being hidden, so it never flashes back.
                    (templates.value as? TemplatesResults.Content)?.let { content ->
                        val rows = content.rows.filter { it.template.id != templateId }
                        templates.value = if (rows.isEmpty()) TemplatesResults.Empty else TemplatesResults.Content(rows)
                    }
                    deleting.update { it - templateId }
                },
                onFailure = {
                    deleting.update { it - templateId }
                    effectChannel.trySend(FoodSearchEffect.TemplateManageFailed)
                },
            )
        }
    }

    private fun setNotFound(code: String?) {
        notFoundBarcode.value = code
        savedState[KEY_NOT_FOUND] = code
    }

    private fun scan() {
        if (scanning || !online.value) return
        scanning = true
        viewModelScope.launch {
            try {
                when (val scanned = barcodeScanner.scan()) {
                    is ScanResult.Code -> lookUp(scanned.value)
                    ScanResult.Cancelled -> Unit
                    ScanResult.Error -> effectChannel.trySend(FoodSearchEffect.ScanFailed)
                }
            } finally {
                scanning = false
            }
        }
    }

    private suspend fun lookUp(code: String) {
        setNotFound(null)
        foodRepository.findByBarcode(code).fold(
            onSuccess = { found ->
                if (found == null) setNotFound(code) else effectChannel.trySend(FoodSearchEffect.OpenFood(found))
            },
            onFailure = { effectChannel.trySend(FoodSearchEffect.ScanFailed) },
        )
    }

    private fun load(request: Request): Flow<FoodResults> = flow {
        emit(FoodResults.Loading)
        // The Comidas tab has its own list ([templates]): no food search runs for it.
        if (request.tab == FoodSearchTab.Templates && request.query.isBlank()) return@flow
        if (!request.online) {
            emit(FoodResults.Error)
            return@flow
        }
        val loaded = when {
            request.query.isNotBlank() -> foodRepository.search(request.query)
            request.tab == FoodSearchTab.Recent -> foodRepository.recent()
            request.tab == FoodSearchTab.MostEaten -> foodRepository.mostEaten()
            else -> foodRepository.favourites()
        }
        emit(loaded.fold(::contentOrEmpty) { FoodResults.Error })
    }

    private fun contentOrEmpty(foods: List<Food>): FoodResults =
        if (foods.isEmpty()) FoodResults.Empty else FoodResults.Content(foods)

    private fun loadTemplates() {
        templates.value = TemplatesResults.Loading
        if (!online.value) {
            templates.value = TemplatesResults.Error
            return
        }
        viewModelScope.launch {
            // Optional info: without the preferences the totals just omit the cooking oil.
            val prefs = foodPreferences.get().getOrNull()
            templates.value = mealTemplates.list().fold(
                onSuccess = { templatesOrEmpty(it, prefs) },
                onFailure = { TemplatesResults.Error },
            )
        }
    }

    private fun templatesOrEmpty(list: List<MealTemplate>, prefs: FoodPreferences?): TemplatesResults =
        if (list.isEmpty()) {
            TemplatesResults.Empty
        } else {
            TemplatesResults.Content(
                list.map { template ->
                    val kcal = cookedItemNutrition.total(template.items, prefs).kcal
                    TemplateSummary(template, kcal)
                },
            )
        }

    private fun toggleFavourite(foodId: String) {
        val wasFavourite = foodId in favouriteIds.value
        favouriteIds.update { if (wasFavourite) it - foodId else it + foodId }
        viewModelScope.launch {
            if (foodRepository.setFavourite(foodId, !wasFavourite).isFailure) {
                favouriteIds.update { if (wasFavourite) it + foodId else it - foodId }
                effectChannel.trySend(FoodSearchEffect.FavouriteFailed)
            }
        }
    }
}
