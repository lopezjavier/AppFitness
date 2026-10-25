package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.RecalculateEntry
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 19)
    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val slots = FakeMealSlotRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val eggPortion = FoodPortion("p1", "huevo", 55.0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        foods.known["chicken"] = food("chicken", "Pechuga de pollo", kcal = 110.0, portions = listOf(eggPortion))
        diary.server[today] = listOf(chickenEntry())
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** 220 g of chicken: 242 kcal (04-business-rules.md section 2). */
    private fun chickenEntry(portionLabel: String? = null, amount: Double = 220.0) = FoodLogEntry(
        id = "e1",
        logDate = today,
        mealSlotId = "snack",
        foodId = "chicken",
        foodName = "Pechuga de pollo",
        amount = amount,
        portionLabel = portionLabel,
        baseUnit = "g",
        kcal = 242.0,
        proteinG = 22.0,
        carbsG = 44.0,
        fatG = 11.0,
    )

    private fun viewModel(state: SavedStateHandle = savedState, entryId: String = "e1") = EditEntryViewModel(
        entryId = entryId,
        date = today,
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = slots,
        recalculateEntry = RecalculateEntry(ResolvePortion(), ScaleNutrition(), ValidateQuantity()),
        connectivity = connectivity,
        savedState = state,
    )

    private fun EditEntryViewModel.content() = assertIs<EditEntryUiState.Content>(uiState.value).content

    @Test
    fun startsLoading_thenShowsTheEntryWithItsCurrentAmountAndMeal() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(EditEntryUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals("220", content.amountText)
        assertNull(content.selectedPortionId)
        assertEquals("snack", content.selectedSlotId)
        assertEquals(242.0, content.preview?.kcal)
        assertTrue(content.canSave)
    }

    @Test
    fun anEntryLoggedWithAPortion_reopensWithThePortionAndItsCount() = runTest(dispatcher) {
        diary.server[today] = listOf(chickenEntry(portionLabel = "2 × huevo", amount = 110.0))
        val model = viewModel()
        advanceUntilIdle()

        val content = model.content()
        assertEquals("p1", content.selectedPortionId)
        assertEquals("2", content.amountText)
        assertEquals(110.0, content.resolvedAmount)
    }

    @Test
    fun changingTheAmountTo150Grams_updatesThePreviewTo165Kcal() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(EditEntryEvent.AmountChanged("150"))
        advanceUntilIdle()

        assertEquals(150.0, model.content().resolvedAmount)
        assertEquals(165.0, model.content().preview?.kcal)
    }

    @Test
    fun editingTheAmountOfAGriddledEntry_keepsItsRetainedOilInThePreviewAndTheSave() = runTest(dispatcher) {
        diary.server[today] = listOf(
            chickenEntry().copy(cookingMethod = CookingMethod.Griddle, cookingOilRetainedG = 2.5),
        )
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(EditEntryEvent.AmountChanged("150"))
        advanceUntilIdle()

        // 150 g raw = 165 kcal / 7.5 g fat, plus the stored 2.5 g of oil (+22.5 kcal / +2.5 g fat).
        assertEquals(187.5, model.content().preview?.kcal)
        assertEquals(10.0, model.content().preview?.fatG)
        model.onEvent(EditEntryEvent.Save)
        advanceUntilIdle()
        assertEquals(NutritionSnapshot(187.5, 15.0, 30.0, 10.0), diary.updated.single().second.snapshot)
    }

    @Test
    fun saving_sendsTheRecalculatedValuesAndClosesOnSuccess() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(EditEntryEvent.AmountChanged("150"))

        model.effects.test {
            model.onEvent(EditEntryEvent.Save)
            advanceUntilIdle()
            assertEquals(EditEntryEffect.Saved, awaitItem())
        }

        assertEquals(
            listOf("e1" to UpdatedFoodEntry("snack", 150.0, null, NutritionSnapshot(165.0, 15.0, 30.0, 7.5))),
            diary.updated,
        )
    }

    @Test
    fun movingTheEntryFromMeriendaToCena_sendsTheNewMeal() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(EditEntryEvent.SlotSelected("dinner"))
        model.onEvent(EditEntryEvent.Save)
        advanceUntilIdle()

        assertEquals("dinner", diary.updated.single().second.mealSlotId)
        assertEquals(220.0, diary.updated.single().second.amount)
    }

    @Test
    fun savingWithAPortion_keepsTheCountInTheLabel() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(EditEntryEvent.PortionSelected("p1"))
        model.onEvent(EditEntryEvent.AmountChanged("2"))
        model.onEvent(EditEntryEvent.Save)
        advanceUntilIdle()

        val sent = diary.updated.single().second
        assertEquals("2 × huevo", sent.portionLabel)
        assertEquals(110.0, sent.amount)
    }

    @Test
    fun invalidAmounts_disableSaveAndNeverReachTheRepository() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        for ((text, error) in listOf(
            "0" to QuantityCheck.TooLow,
            "-3" to QuantityCheck.TooLow,
            "" to QuantityCheck.TooLow,
            "5001" to QuantityCheck.TooHigh,
        )) {
            model.onEvent(EditEntryEvent.AmountChanged(text))
            advanceUntilIdle()
            assertEquals(error, model.content().quantityError, text)
            assertFalse(model.content().canSave, text)
            model.onEvent(EditEntryEvent.Save)
        }
        advanceUntilIdle()

        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun offline_disablesSaveAndFlagsIt() = runTest(dispatcher) {
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().isOffline)
        assertFalse(model.content().canSave)
        model.onEvent(EditEntryEvent.Save)
        advanceUntilIdle()
        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun aFailedUpdate_keepsTheDraftAndReportsTheError() = runTest(dispatcher) {
        diary.failUpdate()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(EditEntryEvent.AmountChanged("150"))

        model.effects.test {
            model.onEvent(EditEntryEvent.Save)
            advanceUntilIdle()
            assertEquals(EditEntryEffect.SaveFailed(AppError.Network), awaitItem())
        }

        assertEquals("150", model.content().amountText)
        assertFalse(model.content().saving)
    }

    @Test
    fun deleting_asksTheDiaryToDeleteTheEntry() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(EditEntryEvent.Delete)
            advanceUntilIdle()
            assertEquals(EditEntryEffect.DeleteRequested("e1"), awaitItem())
        }
        assertTrue(diary.deleted.isEmpty())
    }

    @Test
    fun anEntryThatNoLongerExists_isAnErrorAndRetryReloads() = runTest(dispatcher) {
        val model = viewModel(entryId = "gone")
        advanceUntilIdle()
        assertEquals(EditEntryUiState.Error, model.uiState.value)

        diary.server[today] = listOf(chickenEntry().copy(id = "gone"))
        model.onEvent(EditEntryEvent.Retry)
        advanceUntilIdle()

        assertIs<EditEntryUiState.Content>(model.uiState.value)
    }

    @Test
    fun aFoodThatCannotBeLoaded_isAnErrorAndLeavesTheEntryUntouched() = runTest(dispatcher) {
        foods.known.clear()
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(EditEntryUiState.Error, model.uiState.value)
        assertTrue(diary.updated.isEmpty())
    }

    @Test
    fun aFailedDayRefresh_isAnError() = runTest(dispatcher) {
        diary.failRefresh()
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(EditEntryUiState.Error, model.uiState.value)
    }

    @Test
    fun theDraft_survivesARecreatedViewModel() = runTest(dispatcher) {
        val first = viewModel()
        advanceUntilIdle()
        first.onEvent(EditEntryEvent.PortionSelected("p1"))
        first.onEvent(EditEntryEvent.AmountChanged("3"))
        first.onEvent(EditEntryEvent.SlotSelected("dinner"))

        val second = viewModel(state = savedState)
        advanceUntilIdle()

        val content = second.content()
        assertEquals("3", content.amountText)
        assertEquals("p1", content.selectedPortionId)
        assertEquals("dinner", content.selectedSlotId)
    }
}
