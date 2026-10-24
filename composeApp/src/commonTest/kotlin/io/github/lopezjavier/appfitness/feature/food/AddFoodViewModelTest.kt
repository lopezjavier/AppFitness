package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.LabelNutrientKey
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.UpdatedFood
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AddFoodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate(2026, 10, 19)
    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val slots = FakeMealSlotRepository()
    private val connectivity = FakeConnectivityObserver()
    private val clock = MutableClock(Instant.parse("2026-10-19T13:00:00Z"))
    private val savedState = SavedStateHandle()
    private val egg = food(
        "egg",
        "Huevo entero",
        kcal = 200.0,
        portions = listOf(FoodPortion("p1", "huevo", 55.0)),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        foodId: String,
        initialSlotId: String? = null,
        state: SavedStateHandle = savedState,
        pick: Boolean = false,
    ) = AddFoodViewModel(
        pick = pick,
        foodId = foodId,
        initialSlotId = initialSlotId,
        date = today,
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = slots,
        resolvePortion = ResolvePortion(),
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        defaultMealSlot = DefaultMealSlot(),
        clock = clock,
        connectivity = connectivity,
        savedState = state,
        timeZone = TimeZone.UTC,
    )

    private fun AddFoodViewModel.content() = assertIs<AddFoodUiState.Content>(uiState.value).content

    private fun withFood(food: io.github.lopezjavier.appfitness.domain.model.Food) {
        foods.known[food.id] = food
    }

    @Test
    fun startsLoading_thenShowsTheFoodWithTheBaseDefault() = runTest(dispatcher) {
        withFood(food("rice", "Arroz cocido", kcal = 130.0))
        val model = viewModel("rice")
        assertEquals(AddFoodUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals("100", content.amountText)
        assertNull(content.selectedPortionId)
        assertEquals(130.0, content.preview?.kcal)
    }

    @Test
    fun servingAmount_prefillsTheQuantity() = runTest(dispatcher) {
        withFood(food("chicken", servingAmount = 150.0, servingLabel = "1 filete"))
        val model = viewModel("chicken")
        advanceUntilIdle()

        assertEquals("150", model.content().amountText)
    }

    @Test
    fun changingTheAmountTo220Grams_scalesTheNutritionTo242Kcal() = runTest(dispatcher) {
        withFood(food("chicken", "Pechuga de pollo", kcal = 110.0))
        val model = viewModel("chicken")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.AmountChanged("220"))
        advanceUntilIdle()

        assertEquals(220.0, model.content().resolvedAmount)
        assertEquals(242.0, model.content().preview?.kcal)
    }

    @Test
    fun riceCustomAmount180Grams_is234Kcal() = runTest(dispatcher) {
        withFood(food("rice", "Arroz cocido", kcal = 130.0))
        val model = viewModel("rice")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.AmountChanged("180"))
        advanceUntilIdle()

        assertEquals(234.0, model.content().preview?.kcal)
    }

    @Test
    fun drink250Ml_storesHalfUpTenthAndDisplaysAs113() = runTest(dispatcher) {
        withFood(food("juice", "Zumo de naranja", kcal = 45.0, baseUnit = "ml"))
        val model = viewModel("juice")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.AmountChanged("250"))
        advanceUntilIdle()

        assertEquals(112.5, model.content().preview?.kcal)
        assertEquals(113, model.content().preview?.kcal?.roundToInt())
        assertEquals("ml", model.content().food.baseUnit)
    }

    @Test
    fun decimalComma_isAccepted() = runTest(dispatcher) {
        withFood(food("rice", kcal = 100.0))
        val model = viewModel("rice")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.AmountChanged("12,5"))
        advanceUntilIdle()

        assertEquals(12.5, model.content().resolvedAmount)
    }

    @Test
    fun selectingAPortion_defaultsToOne_andTwoEggsResolveTo110Grams() = runTest(dispatcher) {
        withFood(egg)
        val model = viewModel("egg")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.PortionSelected("p1"))
        advanceUntilIdle()
        assertEquals("1", model.content().amountText)
        assertEquals(55.0, model.content().resolvedAmount)

        model.onEvent(AddFoodEvent.AmountChanged("2"))
        advanceUntilIdle()

        assertEquals(110.0, model.content().resolvedAmount)
        assertEquals(220.0, model.content().preview?.kcal)
    }

    @Test
    fun deselectingThePortion_goesBackToTheBaseUnitDefault() = runTest(dispatcher) {
        withFood(egg)
        val model = viewModel("egg")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.PortionSelected("p1"))

        model.onEvent(AddFoodEvent.PortionSelected(null))
        advanceUntilIdle()

        assertNull(model.content().selectedPortionId)
        assertEquals("100", model.content().amountText)
    }

    @Test
    fun zeroAndAboveFiveThousand_showAFieldErrorAndDisableSubmit() = runTest(dispatcher) {
        withFood(food("rice"))
        val model = viewModel("rice")
        advanceUntilIdle()

        model.onEvent(AddFoodEvent.AmountChanged("0"))
        advanceUntilIdle()
        assertEquals(QuantityCheck.TooLow, model.content().quantityError)
        assertFalse(model.content().canSubmit)

        model.onEvent(AddFoodEvent.AmountChanged("5001"))
        advanceUntilIdle()
        assertEquals(QuantityCheck.TooHigh, model.content().quantityError)
        assertFalse(model.content().canSubmit)

        model.onEvent(AddFoodEvent.AmountChanged(""))
        advanceUntilIdle()
        assertEquals(QuantityCheck.TooLow, model.content().quantityError)

        model.onEvent(AddFoodEvent.AmountChanged("5000"))
        advanceUntilIdle()
        assertNull(model.content().quantityError)
        assertTrue(model.content().canSubmit)
    }

    @Test
    fun portionCountThatResolvesAboveTheLimit_isTooHigh() = runTest(dispatcher) {
        withFood(egg)
        val model = viewModel("egg")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.PortionSelected("p1"))

        model.onEvent(AddFoodEvent.AmountChanged("100"))
        advanceUntilIdle()

        assertEquals(QuantityCheck.TooHigh, model.content().quantityError)
    }

    @Test
    fun preselectedSlot_winsOverTheTimeOfDay() = runTest(dispatcher) {
        withFood(food("rice"))
        val model = viewModel("rice", initialSlotId = "dinner")
        advanceUntilIdle()

        assertEquals("dinner", model.content().selectedSlotId)
    }

    @Test
    fun withoutPreselection_theMealFollowsTheTimeOfDay() = runTest(dispatcher) {
        withFood(food("rice"))
        val model = viewModel("rice")
        advanceUntilIdle()

        assertEquals("lunch", model.content().selectedSlotId)
    }

    @Test
    fun submit_logsTheSnapshotInTheChosenMeal_andEmitsAdded() = runTest(dispatcher) {
        withFood(food("chicken", "Pechuga de pollo", kcal = 110.0))
        val model = viewModel("chicken")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.AmountChanged("220"))
        model.onEvent(AddFoodEvent.SlotSelected("lunch"))

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()

            assertEquals(AddFoodEffect.Added, awaitItem())
        }
        val entry = diary.added.single()
        assertEquals(today, entry.date)
        assertEquals("lunch", entry.mealSlotId)
        assertEquals("chicken", entry.foodId)
        assertEquals("Pechuga de pollo", entry.foodName)
        assertEquals(220.0, entry.amount)
        assertNull(entry.portionLabel)
        assertEquals(242.0, entry.snapshot.kcal)
    }

    @Test
    fun submitWithAPortion_storesTheResolvedAmountAndTheLabel() = runTest(dispatcher) {
        withFood(egg)
        val model = viewModel("egg", initialSlotId = "breakfast")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.PortionSelected("p1"))
        model.onEvent(AddFoodEvent.AmountChanged("2"))

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        val entry = diary.added.single()
        assertEquals(110.0, entry.amount)
        assertEquals("2 × huevo", entry.portionLabel)
        assertEquals("breakfast", entry.mealSlotId)
        assertEquals(220.0, entry.snapshot.kcal)
    }

    @Test
    fun submitWithOnePortion_usesThePlainLabel() = runTest(dispatcher) {
        withFood(egg)
        val model = viewModel("egg")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.PortionSelected("p1"))

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertEquals("huevo", diary.added.single().portionLabel)
    }

    @Test
    fun pickMode_submitEmitsPickedWithTheResolvedAmount_andNeverLogs() = runTest(dispatcher) {
        withFood(egg)
        val model = viewModel("egg", pick = true)
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.PortionSelected("p1"))
        model.onEvent(AddFoodEvent.AmountChanged("2"))

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()

            assertEquals(AddFoodEffect.Picked("egg", 110.0, "2 × huevo"), awaitItem())
        }
        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun pickMode_needsNoMealSlot() = runTest(dispatcher) {
        withFood(food("rice"))
        slots.result = Result.failure(AppException(AppError.Network))
        val model = viewModel("rice", pick = true)
        advanceUntilIdle()

        assertTrue(model.content().pick)
        assertTrue(model.content().canSubmit)
    }

    @Test
    fun pickMode_baseUnitAmountHasNoPortionLabel() = runTest(dispatcher) {
        withFood(food("rice"))
        val model = viewModel("rice", pick = true)
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.AmountChanged("180"))

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()

            assertEquals(AddFoodEffect.Picked("rice", 180.0, null), awaitItem())
        }
    }

    @Test
    fun pickMode_offlineStillAllowsPicking() = runTest(dispatcher) {
        withFood(food("rice"))
        connectivity.state.value = false
        val model = viewModel("rice", pick = true)
        advanceUntilIdle()

        assertTrue(model.content().canSubmit)
    }

    @Test
    fun submitWhenInvalid_doesNothing() = runTest(dispatcher) {
        withFood(food("rice"))
        val model = viewModel("rice")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.AmountChanged("0"))

        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertTrue(diary.added.isEmpty())
    }

    @Test
    fun submitFailure_emitsTheErrorAndKeepsTheSheetEditable() = runTest(dispatcher) {
        withFood(food("rice"))
        diary.failAdd()
        val model = viewModel("rice")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.AmountChanged("180"))

        model.effects.test {
            model.onEvent(AddFoodEvent.Submit)
            advanceUntilIdle()

            assertEquals(AddFoodEffect.SubmitFailed(AppError.Network), awaitItem())
        }
        assertEquals("180", model.content().amountText)
        assertFalse(model.content().submitting)
        assertTrue(model.content().canSubmit)
    }

    @Test
    fun offline_submitIsDisabled() = runTest(dispatcher) {
        withFood(food("rice"))
        connectivity.state.value = false
        val model = viewModel("rice")
        advanceUntilIdle()

        assertTrue(model.content().isOffline)
        assertFalse(model.content().canSubmit)
    }

    private val fiberOnly = Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.5, MicronutrientUnit.G)))

    @Test
    fun micronutrients_areLoadingWhileFetched_andNeverBlockTheSheet() = runTest(dispatcher) {
        withFood(food("rice"))
        foods.micronutrientsGate = CompletableDeferred()
        val model = viewModel("rice")
        advanceUntilIdle()

        assertEquals(MicronutrientsState.Loading, model.content().micronutrients)
        assertTrue(model.content().canSubmit)
    }

    @Test
    fun micronutrients_withData_areShown() = runTest(dispatcher) {
        withFood(food("rice"))
        foods.micronutrientResults["rice"] = Result.success(fiberOnly)
        val model = viewModel("rice")

        advanceUntilIdle()

        assertEquals(MicronutrientsState.Shown(fiberOnly), model.content().micronutrients)
    }

    @Test
    fun micronutrients_empty_areHidden() = runTest(dispatcher) {
        withFood(food("rice"))
        val model = viewModel("rice")

        advanceUntilIdle()

        assertEquals(MicronutrientsState.Hidden, model.content().micronutrients)
    }

    @Test
    fun micronutrients_failure_isSilentAndCanSubmitIsUnaffected() = runTest(dispatcher) {
        withFood(food("rice"))
        foods.micronutrientResults["rice"] = Result.failure(AppException(AppError.Network))
        val model = viewModel("rice")

        advanceUntilIdle()

        assertEquals(MicronutrientsState.Hidden, model.content().micronutrients)
        assertTrue(model.content().canSubmit)
    }

    @Test
    fun micronutrients_doNotChangeTheLoggedSnapshot() = runTest(dispatcher) {
        withFood(food("rice", kcal = 130.0))
        foods.micronutrientResults["rice"] = Result.success(fiberOnly)
        val model = viewModel("rice")
        advanceUntilIdle()

        assertEquals(130.0, model.content().preview?.kcal)
    }

    @Test
    fun unknownFood_isAnError_andRetryLoadsItOnceAvailable() = runTest(dispatcher) {
        val model = viewModel("late")
        advanceUntilIdle()
        assertEquals(AddFoodUiState.Error, model.uiState.value)

        withFood(food("late"))
        model.onEvent(AddFoodEvent.Retry)
        advanceUntilIdle()

        assertEquals("late", model.content().food.id)
    }

    @Test
    fun mealSlotsFailure_isAnError() = runTest(dispatcher) {
        withFood(food("rice"))
        slots.result = Result.failure(IllegalStateException("down"))
        val model = viewModel("rice")
        advanceUntilIdle()

        assertEquals(AddFoodUiState.Error, model.uiState.value)
    }

    @Test
    fun theDraft_survivesARecreatedViewModel() = runTest(dispatcher) {
        withFood(egg)
        val first = viewModel("egg")
        advanceUntilIdle()
        first.onEvent(AddFoodEvent.PortionSelected("p1"))
        first.onEvent(AddFoodEvent.AmountChanged("3"))
        first.onEvent(AddFoodEvent.SlotSelected("snack"))
        advanceUntilIdle()

        val restored = viewModel("egg", initialSlotId = "dinner", state = savedState)
        advanceUntilIdle()

        val content = restored.content()
        assertEquals("p1", content.selectedPortionId)
        assertEquals("3", content.amountText)
        assertEquals("snack", content.selectedSlotId)
        assertEquals(165.0, content.resolvedAmount)
    }

    @Test
    fun aDisabledMealInTheDraft_fallsBackToTheDefault() = runTest(dispatcher) {
        withFood(food("rice"))
        slots.result = Result.success(listOf(MealSlot("only", MealCode.Dinner, "Cena", 0)))
        savedState["add_food_slot"] = "gone"

        val model = viewModel("rice")
        advanceUntilIdle()

        assertEquals("only", model.content().selectedSlotId)
    }

    @Test
    fun mealPicker_listsMealsWithTheirCurrentLabelAndOmitsDeletedOnes() = runTest(dispatcher) {
        withFood(food("rice"))
        slots.save(
            listOf(
                MealSlotDraft("breakfast", MealCode.Breakfast, "Desayuno"),
                MealSlotDraft("lunch", MealCode.Lunch, "Comida"),
                MealSlotDraft("snack", MealCode.Snack, "Post-entreno"),
                MealSlotDraft("dinner", MealCode.Dinner, "Cena"),
            ),
        )
        slots.delete("lunch")

        val model = viewModel("rice")
        advanceUntilIdle()

        assertEquals(listOf("Desayuno", "Post-entreno", "Cena"), model.content().slots.map { it.label })
    }
    private val yogurt = food(
        "yogurt",
        "Yogur casero",
        kcal = 60.0,
        portions = listOf(FoodPortion("p1", "1 tarrina", 125.0)),
        isManual = true,
    )

    @Test
    fun aManualFoodCanBeEdited_anOffFoodCannot() = runTest(dispatcher) {
        withFood(yogurt)
        withFood(food("rice"))

        val manual = viewModel("yogurt")
        val off = viewModel("rice")
        advanceUntilIdle()

        assertTrue(manual.content().showEdit)
        assertTrue(manual.content().canEdit)
        assertFalse(off.content().showEdit)
        assertFalse(off.content().canEdit)
    }

    @Test
    fun offline_theEditActionStaysVisibleButIsDisabled() = runTest(dispatcher) {
        withFood(yogurt)
        connectivity.state.value = false
        val model = viewModel("yogurt")
        advanceUntilIdle()

        assertTrue(model.content().showEdit)
        assertFalse(model.content().canEdit)
    }

    @Test
    fun labelNutrients_listMacrosAndExtrasInLabelOrder() = runTest(dispatcher) {
        withFood(yogurt)
        foods.micronutrientResults["yogurt"] = Result.success(
            Micronutrients(
                listOf(
                    Micronutrient(MicronutrientKind.Sugars, 4.7, MicronutrientUnit.G),
                    Micronutrient(MicronutrientKind.VitaminC, 0.8, MicronutrientUnit.MG),
                ),
            ),
        )
        val model = viewModel("yogurt")
        advanceUntilIdle()

        assertEquals(
            listOf(
                LabelNutrientKey.Energy,
                LabelNutrientKey.Fat,
                LabelNutrientKey.Carbs,
                LabelNutrientKey.Extra(MicronutrientKind.Sugars),
                LabelNutrientKey.Protein,
                LabelNutrientKey.Extra(MicronutrientKind.VitaminC),
            ),
            model.content().labelNutrients.map { it.key },
        )
    }

    @Test
    fun refreshAfterAnEdit_showsTheNewValuesAndLogsWithThem() = runTest(dispatcher) {
        withFood(yogurt)
        val model = viewModel("yogurt")
        advanceUntilIdle()
        assertEquals(60.0, model.content().preview?.kcal)

        foods.updateFood(
            UpdatedFood(
                "yogurt",
                "Yogur casero",
                null,
                NutritionPer100(70.0, 3.5, 4.7, 3.0),
                listOf(NewPortion("1 tarrina", 120.0)),
                baseUnit = yogurt.baseUnit,
            ),
        )
        model.onEvent(AddFoodEvent.Refresh)
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.Submit)
        advanceUntilIdle()

        assertEquals(70.0, diary.added.single().snapshot.kcal)
    }

    @Test
    fun refreshAfterAnEdit_keepsTheSelectedPortionByLabelAndFallsBackWhenItIsGone() = runTest(dispatcher) {
        withFood(yogurt)
        val model = viewModel("yogurt")
        advanceUntilIdle()
        model.onEvent(AddFoodEvent.PortionSelected("p1"))
        advanceUntilIdle()

        foods.updateFood(
            UpdatedFood(
                "yogurt",
                "Yogur casero",
                null,
                yogurt.nutrition,
                listOf(NewPortion("1 tarrina", 120.0)),
                baseUnit = yogurt.baseUnit,
            ),
        )
        model.onEvent(AddFoodEvent.Refresh)
        advanceUntilIdle()
        val renewed = model.content().food.portions.single()
        assertEquals(renewed.id, model.content().selectedPortionId)
        assertEquals(120.0, model.content().resolvedAmount)

        foods.updateFood(
            UpdatedFood("yogurt", "Yogur casero", null, yogurt.nutrition, emptyList(), baseUnit = yogurt.baseUnit),
        )
        model.onEvent(AddFoodEvent.Refresh)
        advanceUntilIdle()
        assertNull(model.content().selectedPortionId)
        assertEquals("100", model.content().amountText)
    }

    @Test
    fun editingAFood_neverChangesAnAlreadyLoggedEntryEvenOutsideTheLoadedRange() = runTest(dispatcher) {
        withFood(yogurt)
        val oldDay = LocalDate(2024, 1, 5)
        val old = FoodLogEntry(
            "e1", oldDay, "lunch", "yogurt", "Yogur casero", 125.0, "1 tarrina", "g", 75.0, 4.0, 5.0, 4.0,
        )
        diary.server[oldDay] = listOf(old)
        diary.refresh(oldDay)

        foods.updateFood(
            UpdatedFood(
                "yogurt",
                "Yogur casero",
                null,
                NutritionPer100(70.0, 3.5, 4.7, 3.0),
                emptyList(),
                baseUnit = yogurt.baseUnit,
            ),
        )

        assertEquals(listOf(old), diary.entries(oldDay).first())
    }
}
