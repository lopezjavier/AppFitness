package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.port.ScanResult
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNull
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

    private val scanner = FakeBarcodeScanner()

    private fun viewModel(state: SavedStateHandle = savedState) =
        foodSearchModel(repository, connectivity, scanner, state)

    private fun FoodSearchViewModel.type(text: String) = onEvent(FoodSearchEvent.QueryChanged(text))

    @Test
    fun pickMode_flagsTheState_andIgnoresTheComidasTab() = runTest(dispatcher) {
        val model = foodSearchModel(repository, connectivity, scanner, pick = true)
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.Templates))
        advanceUntilIdle()

        assertTrue(model.uiState.value.pick)
        assertEquals(FoodSearchTab.Recent, model.uiState.value.tab)
    }

    @Test
    fun pickMode_aRestoredComidasTabFallsBackToRecent() = runTest(dispatcher) {
        savedState["food_search_tab"] = FoodSearchTab.Templates.name
        val model = foodSearchModel(repository, connectivity, scanner, savedState, pick = true)
        advanceUntilIdle()

        assertEquals(FoodSearchTab.Recent, model.uiState.value.tab)
    }

    @Test
    fun theMealTheSearchAddsTo_isExposedForTheTitle() = runTest(dispatcher) {
        val model = foodSearchModel(
            repository,
            connectivity,
            scanner,
            slotId = "dinner",
            mealSlots = FakeMealSlotRepository(),
        )
        advanceUntilIdle()

        assertEquals("Cena", model.uiState.value.mealSlot?.label)
    }

    @Test
    fun withoutAPreselectedMealOrInPickMode_noMealIsExposed() = runTest(dispatcher) {
        val none = foodSearchModel(repository, connectivity, scanner, mealSlots = FakeMealSlotRepository())
        val picking = foodSearchModel(
            repository,
            connectivity,
            scanner,
            pick = true,
            slotId = "dinner",
            mealSlots = FakeMealSlotRepository(),
        )
        advanceUntilIdle()

        assertNull(none.uiState.value.mealSlot)
        assertNull(picking.uiState.value.mealSlot)
    }

    @Test
    fun normalMode_isNotPick() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(false, model.uiState.value.pick)
    }

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

    private val myBowl = food("my-bowl", "Bowl de pollo casero", kcal = 150.0)

    private fun TestScope.search(model: FoodSearchViewModel, text: String) {
        model.type(text)
        advanceTimeBy(400)
        runCurrent()
    }

    @Test
    fun manualFoods_showBeforeOpenFoodFactsAnswers_withTheRemoteStillLoading() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.success(listOf(myBowl))
        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        repository.searchGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        search(model, "pollo")

        assertEquals(
            FoodResults.Content(listOf(myBowl), manualCount = 1, remoteLoading = true),
            model.uiState.value.results,
        )
    }

    @Test
    fun openFoodFactsResults_areAppendedBelowTheManualFoods() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.success(listOf(myBowl))
        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        val gate = CompletableDeferred<Unit>()
        repository.searchGate = gate
        val model = viewModel()
        advanceUntilIdle()
        search(model, "pollo")

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(myBowl, chicken), manualCount = 1), model.uiState.value.results)
    }

    @Test
    fun aRemoteResultWithAManualFoodsId_isNotListedTwice_andTheManualRowStaysOnTop() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.success(listOf(myBowl))
        repository.searchResults["pollo"] = Result.success(listOf(chicken, myBowl.copy(name = "Dup")))
        val model = viewModel()
        advanceUntilIdle()

        search(model, "pollo")
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(myBowl, chicken), manualCount = 1), model.uiState.value.results)
    }

    @Test
    fun whenOpenFoodFactsFails_theManualFoodsStay_andTheStateFlagsTheRemoteFailure() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.success(listOf(myBowl))
        repository.failSearch("pollo")
        val model = viewModel()
        advanceUntilIdle()

        search(model, "pollo")
        advanceUntilIdle()

        assertEquals(
            FoodResults.Content(listOf(myBowl), manualCount = 1, remoteFailed = true),
            model.uiState.value.results,
        )
    }

    @Test
    fun retryAfterARemoteFailure_loadsTheRemoteResultsBelowTheManualFoods() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.success(listOf(myBowl))
        repository.failSearch("pollo")
        val model = viewModel()
        advanceUntilIdle()
        search(model, "pollo")
        advanceUntilIdle()

        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        model.onEvent(FoodSearchEvent.Retry)
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(myBowl, chicken), manualCount = 1), model.uiState.value.results)
    }

    @Test
    fun bothSourcesFailing_isAnError() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.failure(AppException(AppError.Network))
        repository.failSearch("pollo")
        val model = viewModel()
        advanceUntilIdle()

        search(model, "pollo")
        advanceUntilIdle()

        assertEquals(FoodResults.Error, model.uiState.value.results)
    }

    @Test
    fun manualSearchFailing_onlyShowsTheRemoteResults() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.failure(AppException(AppError.Network))
        repository.searchResults["pollo"] = Result.success(listOf(chicken))
        val model = viewModel()
        advanceUntilIdle()

        search(model, "pollo")
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(chicken)), model.uiState.value.results)
    }

    @Test
    fun noManualAndNoRemoteResults_isEmpty() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        search(model, "xyz")
        advanceUntilIdle()

        assertEquals(FoodResults.Empty, model.uiState.value.results)
    }

    @Test
    fun onlyManualResultsAndEmptyRemote_keepsTheManualContent() = runTest(dispatcher) {
        repository.manualResults["bowl"] = Result.success(listOf(myBowl))
        val model = viewModel()
        advanceUntilIdle()

        search(model, "bowl")
        advanceUntilIdle()

        assertEquals(FoodResults.Content(listOf(myBowl), manualCount = 1), model.uiState.value.results)
    }

    @Test
    fun switchingTabWhileSearching_doesNotRepeatTheManualSearch() = runTest(dispatcher) {
        repository.manualResults["pollo"] = Result.success(listOf(myBowl))
        val model = viewModel()
        advanceUntilIdle()
        search(model, "pollo")
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.Favourites))
        advanceUntilIdle()

        assertEquals(listOf("pollo"), repository.manualSearches)
    }

    @Test
    fun aBlankQuery_neverSearchesManualFoods() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(repository.manualSearches.isEmpty())
        assertEquals(FoodResults.Empty, model.uiState.value.results)
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

    @Test
    fun scan_foundProduct_opensIt() = runTest(dispatcher) {
        scanner.result = ScanResult.Code("111")
        repository.barcodeResults["111"] = Result.success(chicken)
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.ScanClicked)
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.OpenFood(chicken), awaitItem())
        }
        assertNull(model.uiState.value.notFoundBarcode)
    }

    @Test
    fun scan_drink_isPassedUntouched() = runTest(dispatcher) {
        scanner.result = ScanResult.Code("222")
        repository.barcodeResults["222"] = Result.success(milk)
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.ScanClicked)
            advanceUntilIdle()
            assertEquals("ml", assertIs<FoodSearchEffect.OpenFood>(awaitItem()).food.baseUnit)
        }
    }

    @Test
    fun scan_unknownProduct_setsTheNotFoundCode_andCreateOpensTheFormWithIt() = runTest(dispatcher) {
        scanner.result = ScanResult.Code("333")
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.ScanClicked)
        advanceUntilIdle()
        assertEquals("333", model.uiState.value.notFoundBarcode)

        model.effects.test {
            model.onEvent(FoodSearchEvent.CreateFromNotFound)
            assertEquals(FoodSearchEffect.OpenCreateFood(barcode = "333", name = null), awaitItem())
        }
    }

    @Test
    fun notFoundCode_survivesRecreation() = runTest(dispatcher) {
        scanner.result = ScanResult.Code("333")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(FoodSearchEvent.ScanClicked)
        advanceUntilIdle()

        val restored = viewModel(savedState)
        advanceUntilIdle()

        assertEquals("333", restored.uiState.value.notFoundBarcode)
    }

    @Test
    fun scan_cancelled_changesNothing() = runTest(dispatcher) {
        scanner.result = ScanResult.Cancelled
        val model = viewModel()
        advanceUntilIdle()
        val before = model.uiState.value

        model.effects.test {
            model.onEvent(FoodSearchEvent.ScanClicked)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(before, model.uiState.value)
        assertTrue(repository.barcodeLookups.isEmpty())
    }

    @Test
    fun scan_error_emitsTheFailureEffect() = runTest(dispatcher) {
        scanner.result = ScanResult.Error
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.ScanClicked)
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.ScanFailed, awaitItem())
        }
    }

    @Test
    fun scan_lookupFailure_emitsTheFailureEffect_andKeepsNoNotFound() = runTest(dispatcher) {
        scanner.result = ScanResult.Code("444")
        repository.barcodeResults["444"] = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.ScanClicked)
            advanceUntilIdle()
            assertEquals(FoodSearchEffect.ScanFailed, awaitItem())
        }
        assertNull(model.uiState.value.notFoundBarcode)
    }

    @Test
    fun scan_offline_isIgnored() = runTest(dispatcher) {
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodSearchEvent.ScanClicked)
        advanceUntilIdle()

        assertEquals(0, scanner.scans)
    }

    @Test
    fun emptyStateCreate_opensTheFormWithTheTrimmedQuery() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.type("  tortilla casera ")
        advanceTimeBy(400)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.CreateFromEmpty)
            assertEquals(FoodSearchEffect.OpenCreateFood(barcode = null, name = "tortilla casera"), awaitItem())
        }
    }

    @Test
    fun createIcon_withoutQuery_opensTheFormWithNoNameAndNoBarcode() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodSearchEvent.CreateFromEmpty)
            assertEquals(FoodSearchEffect.OpenCreateFood(barcode = null, name = null), awaitItem())
        }
    }

    @Test
    fun typing_clearsTheNotFoundCard() = runTest(dispatcher) {
        scanner.result = ScanResult.Code("333")
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(FoodSearchEvent.ScanClicked)
        advanceUntilIdle()

        model.type("a")
        advanceUntilIdle()

        assertNull(model.uiState.value.notFoundBarcode)
    }
}
