package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FoodSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)
    private val rice = food("rice", "Arroz cocido", kcal = 130.0)
    private val milk = food("milk", "Leche", kcal = 45.0, baseUnit = "ml")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) = FoodSearchViewModel(repository, connectivity, state)

    private fun FoodSearchViewModel.type(text: String) = onEvent(FoodSearchEvent.QueryChanged(text))

    @Test
    fun emptyQuery_showsTheRecentFoodsWithoutSearching() = runTest(dispatcher) {
        repository.recentFoods = Result.success(listOf(rice, chicken))
        val model = viewModel()
        assertEquals(FoodResults.Loading, model.uiState.value.results)

        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(rice, chicken)), model.uiState.value.results)
        assertEquals(FoodSearchTab.Recent, model.uiState.value.tab)
        assertTrue(repository.searches.isEmpty())
    }

    @Test
    fun noRecentFoods_isEmpty() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(FoodResults.Empty, model.uiState.value.results)
    }

    @Test
    fun typing_searchesOnceFourHundredMillisAfterTheLastKeystroke() = runTest(dispatcher) {
        repository.searchResults["pechuga pollo"] = Result.success(listOf(chicken))
        val model = viewModel()
        advanceUntilIdle()

        model.type("pech")
        advanceTimeBy(300)
        model.type("pechuga pollo")
        advanceTimeBy(399)
        runCurrent()
        assertTrue(repository.searches.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("pechuga pollo"), repository.searches)
        assertEquals(FoodResults.Content(listOf(chicken)), model.uiState.value.results)
    }

    @Test
    fun whileTheSearchRuns_resultsAreLoading() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.type("arroz")
        advanceTimeBy(400)

        // The debounce fired but the search has not been dispatched yet: the list is Loading, not stale.
        model.uiState.test {
            assertIs<FoodResults>(awaitItem().results)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("arroz"), repository.searches)
    }

    @Test
    fun unknownQuery_isEmpty() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.type("xyzxyz")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(FoodResults.Empty, model.uiState.value.results)
        assertEquals("xyzxyz", model.uiState.value.query)
    }

    @Test
    fun failedSearch_isAnError_andRetryTriesAgain() = runTest(dispatcher) {
        repository.failSearch("arroz")
        val model = viewModel()
        advanceUntilIdle()

        model.type("arroz")
        advanceTimeBy(400)
        advanceUntilIdle()
        assertEquals(FoodResults.Error, model.uiState.value.results)

        repository.searchResults["arroz"] = Result.success(listOf(rice))
        model.onEvent(FoodSearchEvent.Retry)
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(rice)), model.uiState.value.results)
        assertEquals(listOf("arroz", "arroz"), repository.searches)
    }

    @Test
    fun clearingTheBox_returnsToTheTabImmediately() = runTest(dispatcher) {
        repository.recentFoods = Result.success(listOf(rice))
        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        val model = viewModel()
        advanceUntilIdle()
        model.type("pollo")
        advanceTimeBy(400)
        advanceUntilIdle()

        model.type("")
        runCurrent()
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(rice)), model.uiState.value.results)
        assertEquals(listOf("pollo"), repository.searches)
    }

    @Test
    fun mostEatenTab_listsFoodsInTheRepositoryOrder() = runTest(dispatcher) {
        repository.mostEatenFoods = Result.success(listOf(milk, chicken, rice))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.MostEaten))
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(milk, chicken, rice)), model.uiState.value.results)
        assertEquals(FoodSearchTab.MostEaten, model.uiState.value.tab)
    }

    @Test
    fun favouritesTab_listsTheFavouriteFoods() = runTest(dispatcher) {
        repository.favouriteFoods = Result.success(listOf(rice))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.Favourites))
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(rice)), model.uiState.value.results)
        assertEquals(setOf("rice"), model.uiState.value.favouriteIds)
    }

    @Test
    fun switchingTabWhileSearching_doesNotRepeatTheSearch() = runTest(dispatcher) {
        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        val model = viewModel()
        advanceUntilIdle()
        model.type("pollo")
        advanceTimeBy(400)
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.Favourites))
        advanceUntilIdle()

        assertEquals(listOf("pollo"), repository.searches)
        assertEquals(FoodResults.Content(listOf(chicken)), model.uiState.value.results)
    }

    @Test
    fun star_addsAndRemovesAFavourite() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.ToggleFavourite("rice"))
        advanceUntilIdle()
        assertEquals(setOf("rice"), model.uiState.value.favouriteIds)

        model.onEvent(FoodSearchEvent.ToggleFavourite("rice"))
        advanceUntilIdle()

        assertEquals(emptySet(), model.uiState.value.favouriteIds)
        assertEquals(listOf("rice" to true, "rice" to false), repository.favouriteCalls)
    }

    @Test
    fun star_isOptimistic_andRollsBackWhenTheServerFails() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        repository.setFavouriteResult = Result.failure(IllegalStateException("boom"))
        val gate = CompletableDeferred<Unit>()
        repository.favouriteGate = gate

        model.effects.test {
            model.onEvent(FoodSearchEvent.ToggleFavourite("rice"))
            runCurrent()
            assertEquals(setOf("rice"), model.uiState.value.favouriteIds)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(emptySet(), model.uiState.value.favouriteIds)
            assertEquals(FoodSearchEffect.FavouriteFailed, awaitItem())
        }
    }

    @Test
    fun selectingAResult_opensTheFood() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.SelectFood(chicken))
            assertEquals(FoodSearchEffect.OpenFood(chicken), awaitItem())
        }
    }

    @Test
    fun offline_resultsAreAnErrorWithoutCallingTheServer_andRecoverWhenOnlineAgain() = runTest(dispatcher) {
        connectivity.state.value = false
        repository.recentFoods = Result.success(listOf(rice))
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(FoodResults.Error, model.uiState.value.results)
        assertTrue(model.uiState.value.isOffline)

        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(rice)), model.uiState.value.results)
        assertEquals(false, model.uiState.value.isOffline)
    }

    @Test
    fun queryAndTab_surviveARecreatedViewModel_withoutRepeatingTheSearchAtOnce() = runTest(dispatcher) {
        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        val first = viewModel()
        advanceUntilIdle()
        first.type("pollo")
        first.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.MostEaten))
        advanceTimeBy(400)
        advanceUntilIdle()

        val restored = viewModel(savedState)
        runCurrent()

        assertEquals("pollo", restored.uiState.value.query)
        assertEquals(FoodSearchTab.MostEaten, restored.uiState.value.tab)
        advanceUntilIdle()
        assertEquals(FoodResults.Content(listOf(chicken)), restored.uiState.value.results)
    }
}
