package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
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
private const val SEARCH_DEBOUNCE_MS = 400L

/** What to load: a text search or the content of an empty-box tab; [attempt] changes on every retry. */
private data class Request(val query: String, val tab: FoodSearchTab, val attempt: Int, val online: Boolean)

/**
 * Food search (US-17): a 400 ms debounced text search, or the recent / most eaten / favourite foods when the box is
 * empty. Query and tab live in [savedState]. The star flips optimistically and rolls back when the server refuses.
 * Searching and the tabs need the server, so offline the results are an [FoodResults.Error] and the box is disabled.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodSearchViewModel(
    private val foodRepository: FoodRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val tab = MutableStateFlow(
        savedState.get<String>(KEY_TAB)?.let { name -> FoodSearchTab.entries.firstOrNull { it.name == name } }
            ?: FoodSearchTab.Recent,
    )
    private val attempt = MutableStateFlow(0)
    private val favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    private val online = MutableStateFlow(true)

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
        combine(query, tab, results, favouriteIds, online) { q, t, r, f, o ->
            FoodSearchUiState(query = q, tab = t, results = r, favouriteIds = f, isOffline = !o)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FoodSearchUiState(query = query.value, tab = tab.value))

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        viewModelScope.launch { foodRepository.favouriteIds().collect { favouriteIds.value = it } }
        viewModelScope.launch { foodRepository.favourites() }
    }

    fun onEvent(event: FoodSearchEvent) {
        when (event) {
            is FoodSearchEvent.QueryChanged -> {
                query.value = event.text
                savedState[KEY_QUERY] = event.text
            }

            is FoodSearchEvent.TabSelected -> {
                tab.value = event.tab
                savedState[KEY_TAB] = event.tab.name
            }

            is FoodSearchEvent.ToggleFavourite -> toggleFavourite(event.foodId)

            is FoodSearchEvent.SelectFood -> effectChannel.trySend(FoodSearchEffect.OpenFood(event.food))

            FoodSearchEvent.Retry -> attempt.update { it + 1 }
        }
    }

    private fun load(request: Request): Flow<FoodResults> = flow {
        emit(FoodResults.Loading)
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
