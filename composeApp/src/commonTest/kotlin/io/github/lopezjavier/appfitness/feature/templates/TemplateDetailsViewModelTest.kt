package io.github.lopezjavier.appfitness.feature.templates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.repository.FoodPreferencesRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.BuildCookedEntries
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.diary.DefaultMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.CookingImpactPreview
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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

/** Food preferences at their defaults, for tests that do not exercise cooking. */
internal object DefaultFoodPreferences : FoodPreferencesRepository {
    override suspend fun get(): Result<FoodPreferences> = Result.success(FoodPreferences())
}

/** US-21 / US-47 comida editor (ADR-45): the draft, its totals, the single "Guardar" and the immediate delete. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One method per scenario.
class TemplateDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val rice = food("rice", "Arroz cocido", kcal = 130.0)
    private val repository = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake, "rice" to rice))
    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val diary = FakeFoodDiaryRepository()
    private val day = LocalDate(2026, 10, 19)
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )
    private val eggsTwice = MealTemplate(
        id = "t1",
        name = "Doble huevo",
        mealSlotId = null,
        items = listOf(MealTemplateItem(eggs, 100.0, "1 ración"), MealTemplateItem(eggs, 50.0, "1 ración")),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        foods.known["rice"] = rice
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        state: SavedStateHandle = SavedStateHandle(),
        slotId: String? = "lunch",
        slots: FakeMealSlotRepository = FakeMealSlotRepository(),
        prefs: FoodPreferencesRepository = DefaultFoodPreferences,
    ) = TemplateDetailsViewModel(
        templateId = "t1",
        slotId = slotId,
        date = day,
        mealTemplates = repository,
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        validateTemplateName = ValidateTemplateName(),
        foodRepository = foods,
        addTemplateToDiary = AddTemplateToDiary(diary, prefs, BuildCookedEntries(ScaleNutrition())),
        foodPreferences = prefs,
        mealSlots = slots,
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = state,
        timeZone = TimeZone.UTC,
    )

    // --- Añadir (US-53, ADR-63): logs the draft as shown, never stores the comida ---

    @Test
    fun add_withAnUntouchedDraft_logsTheSavedFoodsToTheRouteMealAndDay_andEmitsAdded() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Add)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Added, awaitItem())
        }

        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), batch.map { it.foodId to it.amount })
        assertEquals(listOf(null, "1 vaso"), batch.map { it.portionLabel })
        assertTrue(batch.all { it.mealSlotId == "lunch" && it.date == day })
    }

    @Test
    fun add_withoutARouteMeal_usesTheMealOfTheTimeOfDay() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel(slotId = null)
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        val expected = DefaultMealSlot()(LocalTime(10, 0), DefaultMealSlots)?.id
        assertEquals(expected, diary.addedBatches.single().first().mealSlotId)
    }

    @Test
    fun add_logsTheDraftAsShown_editedAmount_removedFood_andAddedFood() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 80.0, "1 taza"))
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 200.0, "rice" to 80.0), batch.map { it.foodId to it.amount })
        assertEquals(listOf(null, "1 taza"), batch.map { it.portionLabel })
        assertEquals(listOf(200.0, 104.0), batch.map { it.snapshot.kcal })
    }

    @Test
    fun add_doesNotStoreTheComida_andKeepsTheDraft() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
        assertEquals(desayunoA, repository.templates.single())
        assertEquals("200", contentOf(model).rows.first().amountText)
    }

    @Test
    fun add_isNotBlockedByAnInvalidOrTakenName() = runTest(dispatcher) {
        val model = loaded()
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        model.onEvent(TemplateDetailsEvent.Refresh)
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.NameChanged("Comida 2"))

        val content = contentOf(model)
        assertEquals(TemplateNameError.Taken, content.nameError)
        assertTrue(content.canAdd)
        assertFalse(content.canSave)
        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        assertEquals(1, diary.addedBatches.size)
    }

    @Test
    fun add_offline_doesNothing_andIsNotAllowed() = runTest(dispatcher) {
        val model = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        assertFalse(contentOf(model).canAdd)
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun add_withAnInvalidAmount_isNotAllowed() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "abc"))

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        assertFalse(contentOf(model).canAdd)
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun add_ofAnEmptyComida_isNotAllowed() = runTest(dispatcher) {
        val model = loaded(desayunoA.copy(items = emptyList()))

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        assertFalse(contentOf(model).canAdd)
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun add_doubleTap_addsOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        diary.batchGate = gate
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.Add)
        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()
        assertTrue(contentOf(model).adding)
        assertFalse(contentOf(model).canAdd)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, diary.addedBatches.size)
        assertFalse(contentOf(model).adding)
    }

    @Test
    fun add_whenTheWriteFails_setsAddFailed_emitsActionFailed_keepsTheDraft_andAllowsRetry() = runTest(dispatcher) {
        diary.failBatch()
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Add)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        val failed = contentOf(model)
        assertTrue(failed.addFailed)
        assertFalse(failed.adding)
        assertTrue(failed.canAdd)
        assertEquals("200", failed.rows.first().amountText)
        assertTrue(diary.addedBatches.isEmpty())
    }

    @Test
    fun add_withNoRouteMealAndNoEnabledMeal_failsAndLogsNothing() = runTest(dispatcher) {
        repository.templates += desayunoA
        val model = viewModel(slotId = null, slots = FakeMealSlotRepository(Result.success(emptyList())))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Add)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(null), awaitItem())
        }

        assertTrue(diary.addedBatches.isEmpty())
        assertTrue(contentOf(model).addFailed)
    }

    @Test
    fun add_afterProcessDeath_logsTheRestoredDraft() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(0, "250"))
        first.onEvent(TemplateDetailsEvent.RemoveFood(1))

        val restored = viewModel(state)
        advanceUntilIdle()
        restored.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        assertEquals(listOf("eggs" to 250.0), diary.addedBatches.single().map { it.foodId to it.amount })
    }

    private fun TestScope.contentOf(model: TemplateDetailsViewModel): TemplateDetailsUiState.Content {
        advanceUntilIdle()
        val state = model.uiState.value
        assertIs<TemplateDetailsUiState.Content>(state)
        return state
    }

    private fun TestScope.loaded(template: MealTemplate = desayunoA): TemplateDetailsViewModel {
        repository.templates += template
        return viewModel().also { advanceUntilIdle() }
    }

    // --- Loading and totals ---

    @Test
    fun loading_showsTheFoodsWithTheirNutrition_andTheTotals() = runTest(dispatcher) {
        val content = contentOf(loaded())

        assertEquals("Desayuno A", content.name)
        assertEquals(listOf("Huevos revueltos", "Batido de proteína"), content.rows.map { it.name })
        assertEquals(listOf("120", "300"), content.rows.map { it.amountText })
        assertEquals(listOf(null, "1 vaso"), content.rows.map { it.portionLabel })
        assertEquals(listOf(0, 1), content.rows.map { it.index })
        assertEquals(listOf(120.0, 150.0), content.rows.map { it.kcal })
        assertEquals(listOf(12.0, 30.0), content.rows.map { it.proteinG })
        assertEquals(270.0, content.totalKcal, 0.001)
        assertEquals(42.0, content.totalProteinG, 0.001)
        assertEquals(84.0, content.totalCarbsG, 0.001)
        assertEquals(21.0, content.totalFatG, 0.001)
        assertFalse(content.isDirty)
        assertFalse(content.canSave)
    }

    @Test
    fun changingAGramsAmount_updatesTheRow_andTheTotals() = runTest(dispatcher) {
        val model = loaded(desayunoA.copy(items = listOf(MealTemplateItem(food("pasta", kcal = 130.0), 100.0, null))))
        repository.foods["pasta"] = food("pasta", kcal = 130.0)

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "180"))

        val content = contentOf(model)
        assertEquals(234.0, content.rows.single().kcal, 0.001)
        assertEquals(234.0, content.totalKcal, 0.001)
    }

    @Test
    fun totals_sumTheEditedAmountsOfTwoFoods() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "100"))

        assertEquals(200.0 + 50.0, contentOf(model).totalKcal, 0.001)
    }

    @Test
    fun removingAFood_dropsItsRow_andItsShareOfTheTotals() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.RemoveFood(1))

        val content = contentOf(model)
        assertEquals(listOf(0), content.rows.map { it.index })
        assertEquals(120.0, content.totalKcal, 0.001)
        assertEquals(12.0, content.totalProteinG, 0.001)
        assertTrue(content.isDirty)
    }

    @Test
    fun anInvalidAmount_setsTheError_countsAsZero_andDisablesSave() = runTest(dispatcher) {
        val model = loaded()

        listOf("" to QuantityCheck.TooLow, "0" to QuantityCheck.TooLow, "abc" to QuantityCheck.TooLow).forEach {
            model.onEvent(TemplateDetailsEvent.AmountChanged(0, it.first))
            val content = contentOf(model)
            assertEquals(it.second, content.rows.first().quantityError, "amount '${it.first}'")
            assertEquals(0.0, content.rows.first().kcal, 0.0)
            assertEquals(150.0, content.totalKcal, 0.001)
            assertFalse(content.canSave)
        }
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "5001"))
        assertEquals(QuantityCheck.TooHigh, contentOf(model).rows.first().quantityError)
    }

    @Test
    fun anEditedAmount_dropsThePortionLabel_ofThatFoodOnly() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "250"))

        assertEquals(listOf(null, null), contentOf(model).rows.map { it.portionLabel })
        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "300"))
        assertEquals("1 vaso", contentOf(model).rows[1].portionLabel)
    }

    @Test
    fun theLastFood_cannotBeRemoved() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.RemoveFood(0))

        model.onEvent(TemplateDetailsEvent.RemoveFood(1))

        val content = contentOf(model)
        assertEquals(listOf("shake"), content.rows.map { it.foodId })
        assertFalse(content.rows.single().removable)
    }

    @Test
    fun theSameFoodTwice_isTwoRows_withTheirOwnAmounts() = runTest(dispatcher) {
        val model = loaded(eggsTwice)

        model.onEvent(TemplateDetailsEvent.AmountChanged(1, "75"))

        val rows = contentOf(model).rows
        assertEquals(listOf(0, 1), rows.map { it.index })
        assertEquals(listOf("100", "75"), rows.map { it.amountText })
        model.onEvent(TemplateDetailsEvent.RemoveFood(0))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        assertEquals(listOf(1), contentOf(model).rows.map { it.index })
    }

    // --- Load states ---

    @Test
    fun aMissingComida_isAnError_andRetryLoadsItOnceItExists() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertIs<TemplateDetailsUiState.Error>(model.uiState.value)

        repository.templates += desayunoA
        model.onEvent(TemplateDetailsEvent.Retry)

        assertEquals("Desayuno A", contentOf(model).name)
    }

    @Test
    fun aFailedLoad_isAnError() = runTest(dispatcher) {
        repository.templates += desayunoA
        repository.listFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        assertIs<TemplateDetailsUiState.Error>(model.uiState.value)
    }

    @Test
    fun retryAndRefresh_doNotDiscardTheDraft() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        model.onEvent(TemplateDetailsEvent.NameChanged("Mi desayuno"))

        model.onEvent(TemplateDetailsEvent.Refresh)
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.Retry)
        val content = contentOf(model)

        assertEquals(listOf("150"), content.rows.map { it.amountText })
        assertEquals("Mi desayuno", content.name)
    }

    @Test
    fun refresh_resetsTheDraft_whenTheServerItemsChanged() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        repository.templates.clear()
        repository.templates += desayunoA.copy(items = listOf(MealTemplateItem(shake, 200.0, null)))

        model.onEvent(TemplateDetailsEvent.Refresh)
        val content = contentOf(model)

        assertEquals(listOf("200"), content.rows.map { it.amountText })
        assertFalse(content.isDirty)
    }

    // --- canSave ---

    @Test
    fun anAmountTypedInAnotherFormat_isNotDirty_whenItParsesToTheSavedValue() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "120.0"))

        val content = contentOf(model)
        assertFalse(content.isDirty)
        assertFalse(content.canSave)
    }

    @Test
    fun canSave_isTrueOnlyForAValidChangedDraft_online() = runTest(dispatcher) {
        val model = loaded()
        assertFalse(contentOf(model).canSave)

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        assertTrue(contentOf(model).canSave)

        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "120"))
        assertFalse(contentOf(model).isDirty)
        assertFalse(contentOf(model).canSave)
    }

    @Test
    fun offline_disablesSave_andIgnoresTheSaveClick() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))

        connectivity.state.value = false
        advanceUntilIdle()
        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        val content = contentOf(model)
        assertTrue(content.isOffline)
        assertFalse(content.canSave)
        assertEquals(120.0, repository.templates.single().items.first().amount, 0.0)
    }

    // --- Name ---

    @Test
    fun editingTheName_marksTheDraftDirty_andDoesNotTouchTheRepository() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.NameChanged("Desayuno B"))

        val content = contentOf(model)
        assertEquals("Desayuno B", content.name)
        assertNull(content.nameError)
        assertTrue(content.isDirty)
        assertTrue(content.canSave)
        assertEquals("Desayuno A", repository.templates.single().name)
    }

    @Test
    fun typingTheSameNameWithSpaces_isNotDirty() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.NameChanged("  Desayuno A "))

        assertFalse(contentOf(model).isDirty)
    }

    @Test
    fun anEmptyName_cannotBeSaved() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.NameChanged("   "))

        val content = contentOf(model)
        assertTrue(content.isDirty)
        assertFalse(content.canSave)
    }

    @Test
    fun aTooLongName_showsTheHint_andCannotBeSaved() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.NameChanged("x".repeat(61)))

        val content = contentOf(model)
        assertEquals(TemplateNameError.TooLong, content.nameError)
        assertFalse(content.canSave)
    }

    @Test
    fun aNameOfAnotherComida_ignoringCaseAndSpaces_showsTheHint_andCannotBeSaved() = runTest(dispatcher) {
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.NameChanged("  comida 2 "))

        val content = contentOf(model)
        assertEquals(TemplateNameError.Taken, content.nameError)
        assertFalse(content.canSave)
        model.onEvent(TemplateDetailsEvent.NameChanged("Comida 3"))
        assertNull(contentOf(model).nameError)
        assertTrue(contentOf(model).canSave)
    }

    @Test
    fun theOwnUnchangedName_isNotADuplicate() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.NameChanged("desayuno a"))
        val content = contentOf(model)

        assertNull(content.nameError)
    }

    // --- Save ---

    @Test
    fun save_storesNameRemovalsAdditionsAndAmounts_inOneCall() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.NameChanged("Desayuno B"))
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 80.0, "1 taza"))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.SaveClicked)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Saved, awaitItem())
        }

        assertEquals(1, repository.saveCalls)
        val saved = repository.templates.single()
        assertEquals("t1", saved.id)
        assertEquals("Desayuno B", saved.name)
        assertEquals("breakfast", saved.mealSlotId)
        assertEquals(listOf("eggs", "rice"), saved.items.map { it.food.id })
        assertEquals(listOf(200.0, 80.0), saved.items.map { it.amount })
        assertEquals(listOf(null, "1 taza"), saved.items.map { it.portionLabel })
    }

    @Test
    fun save_withAnUnparseableAmount_storesNothing() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "abc"))

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun save_keepsThePortionLabel_ofAnUntouchedAmount() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        val items = repository.templates.single().items
        assertEquals(150.0, items[0].amount, 0.0)
        assertEquals("1 vaso", items[1].portionLabel)
    }

    @Test
    fun save_withoutChanges_doesNothing() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun save_withAnInvalidName_doesNothing() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.NameChanged(""))

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun leavingWithoutGuardar_neverSaves() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.NameChanged("Otro"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(0))
        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 80.0, null))
        advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
        assertEquals(desayunoA, repository.templates.single())
    }

    @Test
    fun aServerNameConflict_showsTheHint_andKeepsTheDraft() = runTest(dispatcher) {
        // The list read at load time did not know the other comida yet.
        val model = loaded()
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        model.onEvent(TemplateDetailsEvent.NameChanged("Comida 2"))
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        val content = contentOf(model)
        assertEquals(TemplateNameError.Taken, content.nameError)
        assertFalse(content.canSave)
        assertEquals("Comida 2", content.name)
        assertEquals("200", content.rows.first().amountText)
        assertFalse(content.busy)
        model.onEvent(TemplateDetailsEvent.NameChanged("Comida 3"))
        assertNull(contentOf(model).nameError)
        assertTrue(contentOf(model).canSave)
    }

    @Test
    fun aSaveFailure_emitsActionFailed_andKeepsTheDraft() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.AmountChanged(0, "200"))
        repository.saveFailure = AppError.Network

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.SaveClicked)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        val content = contentOf(model)
        assertEquals("200", content.rows.first().amountText)
        assertTrue(content.canSave)
        assertFalse(content.busy)
        assertEquals(120.0, repository.templates.single().items.first().amount, 0.0)
    }

    // --- Adding foods ---

    @Test
    fun addFoodClicked_asksForThePicker() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.AddFoodClicked)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.OpenFoodPicker, awaitItem())
        }
    }

    @Test
    fun aPickedFood_appendsARow_withItsKcal_andTheTotals() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 200.0, "2 × taza"))

        val content = contentOf(model)
        val added = content.rows.last()
        assertEquals(3, content.rows.size)
        assertEquals(2, added.index)
        assertTrue(added.added)
        assertEquals("rice", added.foodId)
        assertEquals("200", added.amountText)
        assertEquals("2 × taza", added.portionLabel)
        assertEquals(260.0, added.kcal, 0.001)
        assertEquals(270.0 + 260.0, content.totalKcal, 0.001)
        assertTrue(content.isDirty)
        assertTrue(content.canSave)
    }

    @Test
    fun anAddedRow_canBeEdited_andRemoved() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 200.0, null))
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.AmountChanged(2, "100"))
        assertEquals(130.0, contentOf(model).rows.last().kcal, 0.001)
        model.onEvent(TemplateDetailsEvent.RemoveFood(2))

        val content = contentOf(model)
        assertEquals(listOf(0, 1), content.rows.map { it.index })
        assertFalse(content.isDirty)
    }

    @Test
    fun removingASavedFood_thenAddingAnother_keepsTheNewRowIdentity() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.RemoveFood(0))

        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 50.0, null))

        val content = contentOf(model)
        assertEquals(listOf(1, 2), content.rows.map { it.index })
        assertEquals(listOf("shake", "rice"), content.rows.map { it.foodId })
    }

    @Test
    fun aPickedFoodThatCannotBeRead_emitsActionFailed_andAddsNothing() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.FoodPicked("ghost", 50.0, null))
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.NotFound), awaitItem())
        }

        assertEquals(2, contentOf(model).rows.size)
    }

    // --- Delete ---

    @Test
    fun delete_removesTheComidaAtOnce_withoutAConfirmState_andEmitsDeleted() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Delete)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.Deleted, awaitItem())
        }

        assertTrue(repository.templates.isEmpty())
    }

    @Test
    fun offline_deleteDoesNothing() = runTest(dispatcher) {
        val model = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.Delete)
        advanceUntilIdle()

        assertEquals(1, repository.templates.size)
    }

    @Test
    fun deleteFailure_emitsActionFailed_andKeepsTheComida() = runTest(dispatcher) {
        val model = loaded()
        repository.deleteFailure = AppError.Network

        model.effects.test {
            model.onEvent(TemplateDetailsEvent.Delete)
            advanceUntilIdle()
            assertEquals(TemplateDetailsEffect.ActionFailed(AppError.Network), awaitItem())
        }

        assertFalse(contentOf(model).busy)
        assertEquals(1, repository.templates.size)
    }

    // --- Food sheet ---

    @Test
    fun openingAFood_showsItsSheet_withTheNutritionOfItsRow_andItsMicronutrients() = runTest(dispatcher) {
        val fiber = Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.0, MicronutrientUnit.G)))
        foods.micronutrientResults["eggs"] = Result.success(fiber)
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        val open = assertNotNull(contentOf(model).openFood)
        assertEquals(0, contentOf(model).openFoodIndex)
        assertEquals("eggs", open.food.id)
        assertEquals(120.0, open.row.kcal, 0.001)
        assertEquals(fiber, open.micronutrients)
    }

    @Test
    fun aFoodWithoutMicronutrients_hasNone_andTheSheetStillOpens() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(TemplateDetailsEvent.OpenFood(1))

        val open = assertNotNull(contentOf(model).openFood)
        assertNull(open.micronutrients)
    }

    @Test
    fun theSheetOfAnAddedFood_opens() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.FoodPicked("rice", 200.0, null))
        advanceUntilIdle()

        model.onEvent(TemplateDetailsEvent.OpenFood(2))

        val open = assertNotNull(contentOf(model).openFood)
        assertEquals("rice", open.food.id)
        assertEquals("200", open.row.amountText)
    }

    @Test
    fun removingTheOpenFood_closesItsSheet_andDismissClosesIt() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(1))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))
        assertNull(contentOf(model).openFood)

        model.onEvent(TemplateDetailsEvent.OpenFood(0))
        model.onEvent(TemplateDetailsEvent.DismissFood)
        assertNull(contentOf(model).openFoodIndex)
    }

    @Test
    fun anAmountTypedInTheFoodSheet_isOnlyPreviewed_theListAndTotalsKeepTheCommittedAmount() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        model.onEvent(TemplateDetailsEvent.FoodAmountChanged("250"))

        val content = contentOf(model)
        assertEquals(250.0, content.openFood?.row?.kcal)
        assertEquals("250", content.openFood?.row?.amountText)
        assertEquals("120", content.rows.first().amountText)
        assertEquals(120.0, content.rows.first().kcal, 0.001)
        assertEquals(270.0, content.totalKcal, 0.001)
        assertEquals(120.0, repository.templates.single().items.first().amount, 0.0)
    }

    @Test
    fun dismissingTheFoodSheet_discardsThePendingAmount() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(0))
        model.onEvent(TemplateDetailsEvent.FoodAmountChanged("250"))

        model.onEvent(TemplateDetailsEvent.DismissFood)

        val content = contentOf(model)
        assertNull(content.openFood)
        assertEquals("120", content.rows.first().amountText)
        assertEquals(270.0, content.totalKcal, 0.001)
        assertFalse(content.isDirty)
        assertFalse(content.canSave)
    }

    @Test
    fun savingTheFoodSheet_commitsTheAmount_closesTheSheet_andClearsThePortionLabel() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(1))
        model.onEvent(TemplateDetailsEvent.FoodAmountChanged("200"))

        model.onEvent(TemplateDetailsEvent.SaveFood)

        val content = contentOf(model)
        assertNull(content.openFood)
        val row = content.rows[1]
        assertEquals("200", row.amountText)
        assertEquals(100.0, row.kcal, 0.001)
        assertNull(row.portionLabel)
        assertEquals(220.0, content.totalKcal, 0.001)
        assertTrue(content.isDirty)
        assertTrue(content.canSave)
    }

    @Test
    fun anInvalidPendingAmount_cannotBeSaved_andShowsItsError() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        listOf("" to QuantityCheck.TooLow, "0" to QuantityCheck.TooLow, "5001" to QuantityCheck.TooHigh).forEach {
            model.onEvent(TemplateDetailsEvent.FoodAmountChanged(it.first))
            val open = assertNotNull(contentOf(model).openFood)
            assertFalse(open.canSave, it.first)
            assertEquals(it.second, open.row.quantityError, it.first)

            model.onEvent(TemplateDetailsEvent.SaveFood)
            val content = contentOf(model)
            assertNotNull(content.openFood, it.first)
            assertEquals("120", content.rows.first().amountText)
        }
    }

    @Test
    fun aValidPendingAmount_canBeSaved() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        model.onEvent(TemplateDetailsEvent.FoodAmountChanged("5000"))

        assertTrue(assertNotNull(contentOf(model).openFood).canSave)
    }

    @Test
    fun reopeningAFood_startsFromItsCommittedAmount_notAStalePendingOne() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(0))
        model.onEvent(TemplateDetailsEvent.FoodAmountChanged("250"))
        model.onEvent(TemplateDetailsEvent.DismissFood)

        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        assertEquals("120", contentOf(model).openFood?.row?.amountText)
    }

    @Test
    fun removingTheOpenFood_clearsItsPendingAmount() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(TemplateDetailsEvent.OpenFood(1))
        model.onEvent(TemplateDetailsEvent.FoodAmountChanged("999"))
        model.onEvent(TemplateDetailsEvent.RemoveFood(1))

        model.onEvent(TemplateDetailsEvent.OpenFood(0))

        assertEquals("120", contentOf(model).openFood?.row?.amountText)
        assertEquals(120.0, contentOf(model).totalKcal, 0.001)
    }

    @Test
    fun thePendingAmount_survivesRecreation_whileTheCommittedDraftKeepsTheOldValue() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.OpenFood(0))
        first.onEvent(TemplateDetailsEvent.FoodAmountChanged("250"))

        val content = contentOf(viewModel(state))

        assertEquals("250", content.openFood?.row?.amountText)
        assertEquals("120", content.rows.first().amountText)
        assertFalse(content.isDirty)
    }

    @Test
    fun aSavedFoodAmount_survivesRecreation() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.OpenFood(0))
        first.onEvent(TemplateDetailsEvent.FoodAmountChanged("250"))
        first.onEvent(TemplateDetailsEvent.SaveFood)

        val content = contentOf(viewModel(state))

        assertNull(content.openFood)
        assertEquals("250", content.rows.first().amountText)
    }

    // --- Restoration (ADR-40) ---

    @Test
    fun theDraft_survivesTheViewModelBeingRecreated() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(0, "150"))
        first.onEvent(TemplateDetailsEvent.RemoveFood(1))
        first.onEvent(TemplateDetailsEvent.NameChanged("Mi comida"))
        first.onEvent(TemplateDetailsEvent.OpenFood(0))

        val content = contentOf(viewModel(state))

        assertEquals(listOf("eggs"), content.rows.map { it.foodId })
        assertEquals("150", content.rows.single().amountText)
        assertEquals("Mi comida", content.name)
        assertEquals(0, content.openFoodIndex)
    }

    @Test
    fun addedFoods_withTheirAmountsAndLabels_surviveRecreation() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.FoodPicked("rice", 200.0, "2 × taza"))
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(2, "150"))
        first.onEvent(TemplateDetailsEvent.FoodPicked("rice", 40.0, null))
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.RemoveFood(3))

        val content = contentOf(viewModel(state))

        assertEquals(listOf(0, 1, 2), content.rows.map { it.index })
        assertEquals("150", content.rows.last().amountText)
        assertNull(content.rows.last().portionLabel)
        assertTrue(content.rows.last().added)
        assertTrue(content.canSave)
    }

    @Test
    fun theSameFoodTwice_draftIsRestoredByIndex() = runTest(dispatcher) {
        repository.templates += eggsTwice
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        first.onEvent(TemplateDetailsEvent.AmountChanged(1, "80"))
        first.onEvent(TemplateDetailsEvent.RemoveFood(0))

        val rows = contentOf(viewModel(state)).rows

        assertEquals(listOf(1), rows.map { it.index })
        assertEquals("80", rows.single().amountText)
    }

    // --- Cooking (US-50, ADR-60/63): a method per food, the impact in rows and totals ---

    private val chicken = food("chicken", "Pollo", kcal = 100.0)
    private val milk = food("milk", "Leche", kcal = 50.0, baseUnit = "ml")
    private val cookedMeal = MealTemplate(
        id = "t1",
        name = "Comida",
        mealSlotId = null,
        items = listOf(
            MealTemplateItem(chicken, 200.0, null, CookingMethod.Griddle),
            MealTemplateItem(rice, 100.0, null, CookingMethod.Fried),
            MealTemplateItem(milk, 200.0, null),
        ),
    )

    private fun cookingModel(
        state: SavedStateHandle = SavedStateHandle(),
        prefs: FoodPreferencesRepository = DefaultFoodPreferences,
    ): TemplateDetailsViewModel {
        repository.foods["chicken"] = chicken
        repository.foods["milk"] = milk
        foods.known["chicken"] = chicken
        if (repository.templates.none { it.id == cookedMeal.id }) repository.templates += cookedMeal
        return viewModel(state = state, prefs = prefs)
    }

    private fun TestScope.setMethod(model: TemplateDetailsViewModel, index: Int, method: CookingMethod) {
        model.onEvent(TemplateDetailsEvent.OpenFood(index))
        model.onEvent(TemplateDetailsEvent.MethodChanged(method))
        model.onEvent(TemplateDetailsEvent.SaveFood)
        advanceUntilIdle()
    }

    @Test
    fun cooking_rowsKeepTheirSavedMethod_andIncludeTheFatImpact() = runTest(dispatcher) {
        val rows = contentOf(cookingModel()).rows

        assertEquals(
            listOf(CookingMethod.Griddle, CookingMethod.Fried, CookingMethod.Raw),
            rows.map { it.cookingMethod },
        )
        assertEquals(listOf(true, true, false), rows.map { it.canCook })
        assertEquals(listOf(218.0, 202.0, 100.0), rows.map { it.kcal })
        assertEquals(listOf(12.0, 13.0, 10.0), rows.map { it.fatG })
        assertEquals(CookingImpactPreview(18.0, 2.0), rows[0].cookingImpact)
        assertEquals(CookingImpactPreview(72.0, 8.0), rows[1].cookingImpact)
        assertNull(rows[2].cookingImpact)
    }

    @Test
    fun cooking_totalsIncludeTheImpact() = runTest(dispatcher) {
        val content = contentOf(cookingModel())

        assertEquals(218.0 + 202.0 + 100.0, content.totalKcal)
        assertEquals(35.0, content.totalFatG)
    }

    @Test
    fun cooking_methodChangeThroughTheSheet_isDirtyAndMovesTheTotals() = runTest(dispatcher) {
        val model = cookingModel()
        assertFalse(contentOf(model).isDirty)

        setMethod(model, 0, CookingMethod.Raw)

        val content = contentOf(model)
        assertTrue(content.isDirty)
        assertEquals(CookingMethod.Raw, content.rows[0].cookingMethod)
        assertEquals(200.0 + 202.0 + 100.0, content.totalKcal)
    }

    @Test
    fun cooking_sheetPreviewsThePendingMethod_butDismissDiscardsIt() = runTest(dispatcher) {
        val model = cookingModel()
        contentOf(model)

        model.onEvent(TemplateDetailsEvent.OpenFood(0))
        model.onEvent(TemplateDetailsEvent.MethodChanged(CookingMethod.AirFryer))
        val preview = contentOf(model)
        assertEquals(CookingMethod.AirFryer, preview.openFood?.row?.cookingMethod)
        assertEquals(236.0, preview.openFood?.row?.kcal)
        assertEquals(CookingMethod.Griddle, preview.rows[0].cookingMethod)

        model.onEvent(TemplateDetailsEvent.DismissFood)
        val content = contentOf(model)
        assertEquals(CookingMethod.Griddle, content.rows[0].cookingMethod)
        assertFalse(content.isDirty)
    }

    @Test
    fun cooking_drinksIgnoreTheMethod() = runTest(dispatcher) {
        val model = cookingModel()
        contentOf(model)

        setMethod(model, 2, CookingMethod.Fried)

        val row = contentOf(model).rows[2]
        assertEquals(CookingMethod.Raw, row.cookingMethod)
        assertEquals(100.0, row.kcal)
    }

    @Test
    fun cooking_addHandsEachMethodAndTheCurrentOilToTheDiary() = runTest(dispatcher) {
        val prefs = FakeFoodPreferencesRepository(Result.success(FoodPreferences(fatPctGriddle = 10.0)))
        val model = cookingModel(prefs = prefs)
        contentOf(model)

        model.onEvent(TemplateDetailsEvent.Add)
        advanceUntilIdle()

        val batch = diary.addedBatches.single()
        assertEquals(
            listOf(CookingMethod.Griddle, CookingMethod.Fried, CookingMethod.Raw),
            batch.map { it.cookingMethod },
        )
        assertEquals(listOf(20.0, 8.0, 0.0), batch.map { it.cookingOilRetainedG })
        assertEquals(listOf(380.0, 202.0, 100.0), batch.map { it.snapshot.kcal })
    }

    @Test
    fun cooking_saveStoresTheMethodOfEachFood() = runTest(dispatcher) {
        val model = cookingModel()
        contentOf(model)
        setMethod(model, 1, CookingMethod.AirFryer)

        model.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        assertEquals(
            listOf(CookingMethod.Griddle, CookingMethod.AirFryer, CookingMethod.Raw),
            repository.templates.single().items.map { it.cookingMethod },
        )
    }

    @Test
    fun cooking_addedFoodsStartRaw_andCanBeCooked() = runTest(dispatcher) {
        val model = cookingModel()
        contentOf(model)
        model.onEvent(TemplateDetailsEvent.FoodPicked("chicken", 100.0, null))
        advanceUntilIdle()
        assertEquals(CookingMethod.Raw, contentOf(model).rows.last().cookingMethod)

        setMethod(model, 3, CookingMethod.Fried)

        assertEquals(CookingMethod.Fried, contentOf(model).rows.last().cookingMethod)
    }

    @Test
    fun cooking_removedRowDropsItsMethod() = runTest(dispatcher) {
        val model = cookingModel()
        contentOf(model)

        model.onEvent(TemplateDetailsEvent.RemoveFood(0))

        val content = contentOf(model)
        assertEquals(listOf(CookingMethod.Fried, CookingMethod.Raw), content.rows.map { it.cookingMethod })
        assertEquals(202.0 + 100.0, content.totalKcal)
    }

    @Test
    fun cooking_preferencesFailure_meansNoImpact_andRefreshRetries() = runTest(dispatcher) {
        val prefs = FakeFoodPreferencesRepository(Result.failure(AppException(AppError.Network)))
        val model = cookingModel(prefs = prefs)

        val failed = contentOf(model)
        assertEquals(listOf(200.0, 130.0, 100.0), failed.rows.map { it.kcal })
        assertNull(failed.rows[0].cookingImpact)

        prefs.result = Result.success(FoodPreferences())
        model.onEvent(TemplateDetailsEvent.Refresh)
        assertEquals(218.0, contentOf(model).rows[0].kcal)
    }

    @Test
    fun cooking_methodsAndThePendingSheetMethodSurviveRecreation() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val first = cookingModel(state = state)
        contentOf(first)
        setMethod(first, 1, CookingMethod.Raw)
        first.onEvent(TemplateDetailsEvent.OpenFood(0))
        first.onEvent(TemplateDetailsEvent.MethodChanged(CookingMethod.AirFryer))
        advanceUntilIdle()

        val content = contentOf(viewModel(state = state))

        assertEquals(
            listOf(CookingMethod.Griddle, CookingMethod.Raw, CookingMethod.Raw),
            content.rows.map { it.cookingMethod },
        )
        assertEquals(CookingMethod.AirFryer, content.openFood?.row?.cookingMethod)
    }

    @Test
    fun aTakenNameHint_survivesRecreation() = runTest(dispatcher) {
        repository.templates += desayunoA
        val state = SavedStateHandle()
        val first = viewModel(state)
        advanceUntilIdle()
        repository.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        first.onEvent(TemplateDetailsEvent.NameChanged("Comida 2"))
        first.onEvent(TemplateDetailsEvent.SaveClicked)
        advanceUntilIdle()

        val content = contentOf(viewModel(state))

        assertEquals(TemplateNameError.Taken, content.nameError)
    }
}
