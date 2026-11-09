package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Adding an Open Food Facts food: the energy check as a hard block, the correction and the personal copy. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddFoodOffViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(foodId: String, pick: Boolean = false, state: SavedStateHandle = savedState) =
        AddFoodViewModel(
            pick = pick,
            foodId = foodId,
            initialSlotId = "lunch",
            date = LocalDate(2026, 10, 19),
            foodRepository = foods,
            foodDiary = diary,
            mealSlots = FakeMealSlotRepository(),
            foodPreferencesRepository = FakeFoodPreferencesRepository(),
            resolvePortion = ResolvePortion(),
            scaleNutrition = ScaleNutrition(),
            validateQuantity = ValidateQuantity(),
            defaultMealSlot = DefaultMealSlot(),
            clock = MutableClock(Instant.parse("2026-10-19T13:00:00Z")),
            connectivity = connectivity,
            savedState = state,
            timeZone = TimeZone.UTC,
        )

    private fun AddFoodViewModel.content() = assertIs<AddFoodUiState.Content>(uiState.value).content

    private fun off(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        id: String = "off1",
        baseUnit: String = "g",
    ): Food =
        food(id, "OFF $id", kcal = kcal, protein = protein, carbs = carbs, fat = fat, isOff = true, baseUnit = baseUnit)
            .also { foods.known[id] = it }

    private val failing get() = off(500.0, 10.0, 30.0, 10.0)

    private val passing get() = off(100.0, 5.0, 10.0, 3.0)

    @Test
    fun addingAnOffFood_writesTheCopyIdAndKeepsTheSnapshot() = runTest(dispatcher) {
        passing
        val model = viewModel("off1")
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()
            assertEquals(AddFoodEffect.Added, awaitItem())
        }

        val entry = diary.added.single()
        assertEquals("copy-off1", entry.foodId)
        assertEquals(100.0, entry.snapshot.kcal)
        assertEquals(listOf("off1"), foods.copyCalls)
    }

    @Test
    fun addingTheSameOffFoodAgain_reusesTheSameCopy() = runTest(dispatcher) {
        passing
        repeat(2) {
            val model = viewModel("off1", state = SavedStateHandle())
            advanceUntilIdle()
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()
        }

        assertEquals(listOf("off1", "off1"), foods.copyCalls)
        assertEquals(listOf("copy-off1", "copy-off1"), diary.added.map { it.foodId })
    }

    @Test
    fun aFoodThatIsNotOff_neverCopies_andNeverReadsAlcohol() = runTest(dispatcher) {
        foods.known["mine"] = food("mine", isManual = true)
        val model = viewModel("mine")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertTrue(foods.copyCalls.isEmpty())
        assertTrue(foods.alcoholReads.isEmpty())
        assertEquals("mine", diary.added.single().foodId)
    }

    @Test
    fun aFailingOffFood_showsTheWarningAtOnce_blocksSubmit_andLogsNothing() = runTest(dispatcher) {
        failing
        val model = viewModel("off1")
        advanceUntilIdle()

        val warning = assertIs<EnergyWarning>(model.content().energyWarning)
        assertEquals(EnergyCheckLevel.ClearlyWrong, warning.level)
        assertEquals(500.0, warning.kcal)
        assertEquals(250.0, warning.computedKcal)
        assertFalse(model.content().canSubmit)

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertTrue(diary.added.isEmpty())
        assertTrue(foods.copyCalls.isEmpty())
        assertTrue(foods.updates.isEmpty())
    }

    @Test
    fun correctOff_emitsThePrefilledFormWithTheOffValuesAndBarcode() = runTest(dispatcher) {
        off(500.0, 10.0, 30.0, 10.0).also { foods.known["off1"] = it.copy(barcode = "8410000000001") }
        foods.offAlcohol["off1"] = 1.0
        foods.micronutrientResults["off1"] =
            Result.success(Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.0, MicronutrientUnit.G))))
        val model = viewModel("off1", state = SavedStateHandle())
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(AddFoodEvent.CorrectOff)
            advanceUntilIdle()
            val effect = assertIs<AddFoodEffect.CorrectOffFood>(awaitItem())
            assertEquals("8410000000001", effect.barcode)
            val draft = assertNotNull(NewFoodDraftCodec.decode(effect.prefill))
            assertEquals("OFF off1", draft.name)
            assertEquals(500.0, draft.kcal)
            assertEquals(10.0, draft.protein)
            assertEquals(30.0, draft.carbs)
            assertEquals(10.0, draft.fat)
            assertEquals(1.0, draft.alcohol)
            assertEquals(mapOf(MicronutrientKind.Fiber to 2.0), draft.micronutrients.values)
            assertEquals("8410000000001", draft.barcode)
        }
        assertTrue(foods.copyCalls.isEmpty())
    }

    @Test
    fun correctOff_ofAPassingFood_emitsNothing() = runTest(dispatcher) {
        passing
        val model = viewModel("off1")
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(AddFoodEvent.CorrectOff)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun aPassingFood_addsWithoutWarning_andWritesNoUpdate() = runTest(dispatcher) {
        passing
        val model = viewModel("off1")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertTrue(foods.updates.isEmpty())
        assertEquals(1, diary.added.size)
    }

    @Test
    fun redWineWithItsAlcohol_passes() = runTest(dispatcher) {
        off(85.0, 0.1, 2.6, 0.0, baseUnit = "ml")
        foods.offAlcohol["off1"] = 9.5
        val model = viewModel("off1")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertEquals(1, diary.added.size)
    }

    @Test
    fun theSameWineWithoutAlcohol_isBlocked() = runTest(dispatcher) {
        off(85.0, 0.1, 2.6, 0.0, baseUnit = "ml")
        val model = viewModel("off1")
        advanceUntilIdle()

        assertIs<EnergyWarning>(model.content().energyWarning)
        assertFalse(model.content().canSubmit)
        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun fibreFromTheExtras_countsInTheCheck() = runTest(dispatcher) {
        // 4*5 + 4*40 + 2*20 = 220 vs 200 declared: only passes with the fibre (without it 180 vs 200 also passes).
        off(200.0, 5.0, 40.0, 0.0)
        foods.micronutrientResults["off1"] =
            Result.success(Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 20.0, MicronutrientUnit.G))))
        val model = viewModel("off1")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertNull(model.content().energyWarning)
        assertEquals(1, diary.added.size)
    }

    @Test
    fun aFailedAlcoholRead_meansNoWarning_andStillAdds() = runTest(dispatcher) {
        failing
        foods.alcoholFailure = AppException(AppError.Network)
        val model = viewModel("off1")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertEquals(1, diary.added.size)
    }

    @Test
    fun aFailedMicronutrientRead_meansNoWarning_andStillAdds() = runTest(dispatcher) {
        failing
        foods.micronutrientResults["off1"] = Result.failure(AppException(AppError.Network))
        val model = viewModel("off1")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertEquals(1, diary.added.size)
    }

    @Test
    fun theCheckBoundaries_are15PercentPlus5_and30PercentPlus5() = runTest(dispatcher) {
        // kcal 100: Ok up to a gap of 20, Mismatch above it, ClearlyWrong above 35.
        val cases = listOf(30.0 to null, 30.25 to false, 33.75 to false, 34.0 to true)
        cases.forEach { (protein, wrong) ->
            off(100.0, protein, 0.0, 0.0)
            val model = viewModel("off1", state = SavedStateHandle())
            advanceUntilIdle()
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()
            assertEquals(wrong, (model.content().energyWarning)?.clearlyWrong, "protein $protein")
        }
    }

    @Test
    fun aCopyFailure_isSubmitFailed_andLogsNothing() = runTest(dispatcher) {
        passing
        foods.copyFailure = AppException(AppError.Network)
        val model = viewModel("off1")
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()
            assertEquals(AddFoodEffect.SubmitFailed(AppError.Network), awaitItem())
        }
        assertTrue(diary.added.isEmpty())
        assertEquals(false, model.content().submitting)
    }

    @Test
    fun aServerEnergyCheckRefusal_isNotShownAsAFailure_andLogsNothing() = runTest(dispatcher) {
        passing
        foods.copyFailure = AppException(AppError.FoodEnergyCheckFailed)
        val model = viewModel("off1")
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(diary.added.isEmpty())
        assertEquals(false, model.content().submitting)
    }

    @Test
    fun pickMode_handsOverTheCopyId() = runTest(dispatcher) {
        passing
        val model = viewModel("off1", pick = true)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()
            val picked = assertIs<AddFoodEffect.Picked>(awaitItem())
            assertEquals("copy-off1", picked.foodId)
            assertEquals(100.0, picked.amount)
        }
        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun pickMode_failingCheckBlocksBeforeCopying() = runTest(dispatcher) {
        failing
        val model = viewModel("off1", pick = true)
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertIs<EnergyWarning>(model.content().energyWarning)
        assertFalse(model.content().canSubmit)
        assertTrue(foods.copyCalls.isEmpty())
    }

    @Test
    fun offline_anOffFoodCannotBeSubmitted() = runTest(dispatcher) {
        passing
        connectivity.state.value = false
        val model = viewModel("off1")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertEquals(false, model.content().canSubmit)
        assertTrue(foods.copyCalls.isEmpty())
    }

    @Test
    fun theWarning_isRecomputedOnRecreate_notPersisted() = runTest(dispatcher) {
        failing
        val first = viewModel("off1")
        advanceUntilIdle()
        assertIs<EnergyWarning>(first.content().energyWarning)
        foods.alcoholFailure = AppException(AppError.Network)

        val second = viewModel("off1", state = savedState)
        advanceUntilIdle()

        assertNull(second.content().energyWarning)
        assertNull(savedState.get<Boolean>("add_food_energy_warning"))
    }

    @Test
    fun theWarning_andTheAmount_surviveARecreatedViewModel() = runTest(dispatcher) {
        failing
        val first = viewModel("off1")
        advanceUntilIdle()
        first.onEvent(AddFoodEvent.AmountChanged("150"))

        val second = viewModel("off1", state = savedState)
        advanceUntilIdle()

        assertIs<EnergyWarning>(second.content().energyWarning)
        assertEquals("150", second.content().amountText)
    }
}
