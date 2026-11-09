package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass") // One method per scenario.
class CreateFoodEnergyCheckTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createModel(state: SavedStateHandle = SavedStateHandle(), prefill: NewFoodDraft? = null) =
        CreateFoodViewModel(
            barcode = null,
            name = null,
            foodRepository = repository,
            connectivity = connectivity,
            savedState = state,
            prefill = prefill?.let(NewFoodDraftCodec::encode),
        )

    private fun editModel(state: SavedStateHandle = SavedStateHandle()) = CreateFoodViewModel(
        barcode = null,
        name = null,
        foodRepository = repository,
        connectivity = connectivity,
        savedState = state,
        editFoodId = "f1",
    )

    private fun CreateFoodViewModel.type(field: CreateFoodField, text: String) =
        onEvent(CreateFoodEvent.FieldChanged(field, text))

    private fun CreateFoodViewModel.fill(
        kcal: String,
        protein: String,
        carbs: String,
        fat: String,
        alcohol: String = "",
        fibre: String = "",
        polyols: String = "",
    ) {
        type(CreateFoodField.Name, "Alimento")
        type(CreateFoodField.Kcal, kcal)
        type(CreateFoodField.Protein, protein)
        type(CreateFoodField.Carbs, carbs)
        type(CreateFoodField.Fat, fat)
        if (alcohol.isNotEmpty()) type(CreateFoodField.Alcohol, alcohol)
        if (fibre.isNotEmpty()) onEvent(CreateFoodEvent.NutrientChanged(MicronutrientKind.Fiber, fibre))
        if (polyols.isNotEmpty()) onEvent(CreateFoodEvent.NutrientChanged(MicronutrientKind.Polyols, polyols))
    }

    private fun TestScope.save(model: CreateFoodViewModel, event: CreateFoodEvent = CreateFoodEvent.Save) {
        model.onEvent(event)
        advanceUntilIdle()
    }

    // ---- pass / skip

    @Test
    fun consistentFood_savesWithFlagFalseAndNoWarning() = runTest(dispatcher) {
        val model = createModel()
        model.fill("100", "5", "10", "3")
        advanceUntilIdle()

        save(model)

        assertNull(model.uiState.value.energyWarning)
        assertEquals(1, repository.created.size)
        assertFalse(repository.created.single().savedAnyway)
    }

    @Test
    fun coffee_isSkippedAndSaves() = runTest(dispatcher) {
        val model = createModel()
        model.fill("2", "0,1", "0,3", "0")
        advanceUntilIdle()

        save(model)

        assertEquals(1, repository.created.size)
        assertNull(model.uiState.value.energyWarning)
    }

    @Test
    fun redWineWithAlcohol_savesWithoutWarningAndSendsAlcohol() = runTest(dispatcher) {
        val model = createModel()
        model.fill("83", "0,1", "2,6", "0", alcohol = "9,5")
        advanceUntilIdle()

        save(model)

        assertEquals(9.5, repository.created.single().alcoholG)
        assertNull(model.uiState.value.energyWarning)
    }

    @Test
    fun blankAlcoholSendsNull_andZeroSendsZero() = runTest(dispatcher) {
        val blank = createModel()
        blank.fill("100", "5", "10", "3")
        advanceUntilIdle()
        save(blank)
        val zero = createModel()
        zero.fill("100", "5", "10", "3", alcohol = "0")
        advanceUntilIdle()
        save(zero)

        assertNull(repository.created[0].alcoholG)
        assertEquals(0.0, repository.created[1].alcoholG)
    }

    // ---- warnings

    @Test
    fun redWineWithoutAlcohol_warnsWithNegativeGap_andDoesNotSave() = runTest(dispatcher) {
        val model = createModel()
        model.fill("83", "0,1", "2,6", "0")
        advanceUntilIdle()

        save(model)

        val warning = assertNotNull(model.uiState.value.energyWarning)
        assertEquals(EnergyCheckLevel.ClearlyWrong, warning.level)
        assertTrue(warning.gap < 0)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun sweetsWithoutPolyols_warnWithPositiveGap() = runTest(dispatcher) {
        val model = createModel()
        model.fill("240", "0", "95", "0")
        advanceUntilIdle()

        save(model)

        val warning = assertNotNull(model.uiState.value.energyWarning)
        assertTrue(warning.gap > 0)
    }

    @Test
    fun epicCase_warnsWithTheTypedKcalAndTheComputedOne() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()

        save(model)

        val warning = assertNotNull(model.uiState.value.energyWarning)
        assertEquals(500.0, warning.kcal, 1e-9)
        assertEquals(250.0, warning.computedKcal, 1e-9)
        assertTrue(warning.clearlyWrong)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun mismatch_isNotClearlyWrong() = runTest(dispatcher) {
        val model = createModel()
        model.fill("350", "12", "45", "6")
        advanceUntilIdle()

        save(model)

        assertFalse(assertNotNull(model.uiState.value.energyWarning).clearlyWrong)
    }

    @Test
    fun fibreAndPolyolsTypedInLabelUnitsEnterTheCheck() = runTest(dispatcher) {
        val model = createModel()
        model.fill("350", "12", "45", "6", fibre = "30")
        advanceUntilIdle()

        save(model)

        assertEquals(1, repository.created.size)
    }

    @Test
    fun dismissWarning_closesItAndKeepsEveryField() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10", alcohol = "1")
        advanceUntilIdle()
        save(model)

        save(model, CreateFoodEvent.DismissWarning)

        val state = model.uiState.value
        assertNull(state.energyWarning)
        assertEquals("500", state.kcal)
        assertEquals("10", state.protein)
        assertEquals("30", state.carbs)
        assertEquals("10", state.fat)
        assertEquals("1", state.alcohol)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun saveAnyway_savesOnceExactlyAsTypedWithTheFlag() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()
        save(model)

        save(model, CreateFoodEvent.SaveAnyway)

        val created = repository.created.single()
        assertTrue(created.savedAnyway)
        assertEquals(NutritionPer100(500.0, 10.0, 30.0, 10.0), created.nutrition)
        assertNull(model.uiState.value.energyWarning)
    }

    @Test
    fun saveAnyway_withoutAnOpenWarning_doesNothing() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()

        save(model, CreateFoodEvent.SaveAnyway)

        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun editingACheckInput_closesTheWarning() = runTest(dispatcher) {
        val edits = listOf<(CreateFoodViewModel) -> Unit>(
            { it.type(CreateFoodField.Kcal, "501") },
            { it.type(CreateFoodField.Protein, "11") },
            { it.type(CreateFoodField.Carbs, "31") },
            { it.type(CreateFoodField.Fat, "11") },
            { it.type(CreateFoodField.Alcohol, "2") },
            { it.onEvent(CreateFoodEvent.NutrientChanged(MicronutrientKind.Fiber, "1")) },
            { it.onEvent(CreateFoodEvent.NutrientChanged(MicronutrientKind.Polyols, "1")) },
        )
        edits.forEach { edit ->
            val model = createModel()
            model.fill("500", "10", "30", "10")
            advanceUntilIdle()
            save(model)
            assertNotNull(model.uiState.value.energyWarning)

            edit(model)
            advanceUntilIdle()

            assertNull(model.uiState.value.energyWarning)
        }
    }

    @Test
    fun editingNameBrandOrPortions_keepsTheWarning() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()
        save(model)

        model.type(CreateFoodField.Name, "Otro")
        model.type(CreateFoodField.Brand, "Marca")
        model.onEvent(CreateFoodEvent.AddPortion)
        advanceUntilIdle()

        assertNotNull(model.uiState.value.energyWarning)
    }

    @Test
    fun failedSaveAfterSaveAnyway_closesTheWarningKeepsValuesAndFails() = runTest(dispatcher) {
        repository.createFailure = AppException(AppError.Network)
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()
        save(model)

        save(model, CreateFoodEvent.SaveAnyway)

        val state = model.uiState.value
        assertTrue(state.saveFailed)
        assertNull(state.energyWarning)
        assertEquals("500", state.kcal)
    }

    @Test
    fun offline_cannotSaveAndShowsNoWarning() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        connectivity.state.value = false
        advanceUntilIdle()

        save(model)

        assertFalse(model.uiState.value.canSave)
        assertNull(model.uiState.value.energyWarning)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun invalidAlcohol_blocksSaveAndShowsTheErrorOnceTouched() = runTest(dispatcher) {
        val model = createModel()
        model.fill("100", "5", "10", "3", alcohol = "101")
        advanceUntilIdle()

        assertFalse(model.uiState.value.canSave)
        assertNotNull(model.uiState.value.errors.alcohol)
    }

    // ---- entry paths

    private val dishDraft = NewFoodDraft(
        name = "Vino tinto",
        brand = null,
        baseUnit = "ml",
        kcal = 83.0,
        protein = 0.1,
        carbs = 2.6,
        fat = 0.0,
        micronutrients = NewMicronutrients(),
        portions = emptyList(),
        alcohol = 9.5,
    )

    @Test
    fun aiDraft_prefillsAlcoholAndSavesWithoutWarning() = runTest(dispatcher) {
        val model = createModel(prefill = dishDraft)
        advanceUntilIdle()
        assertEquals("9,5", model.uiState.value.alcohol)

        save(model)

        assertEquals(9.5, repository.created.single().alcoholG)
        assertNull(model.uiState.value.energyWarning)
    }

    @Test
    fun aiDraftWithoutAlcohol_stillRunsTheCheck() = runTest(dispatcher) {
        val model = createModel(prefill = dishDraft.copy(alcohol = 0.0))
        advanceUntilIdle()

        save(model)

        assertNotNull(model.uiState.value.energyWarning)
        assertTrue(repository.created.isEmpty())
    }

    // ---- edit mode

    private fun storeFood(kcal: Double, protein: Double, carbs: Double, fat: Double, flagged: Boolean) {
        repository.known["f1"] =
            food("f1", isManual = true).copy(nutrition = NutritionPer100(kcal, protein, carbs, fat))
        if (flagged) repository.storedSavedAnyway += "f1"
    }

    @Test
    fun edit_loadsAlcohol() = runTest(dispatcher) {
        storeFood(83.0, 0.1, 2.6, 0.0, flagged = false)
        repository.storedAlcohol["f1"] = 9.5
        val model = editModel()
        advanceUntilIdle()

        assertEquals("9,5", model.uiState.value.alcohol)
    }

    @Test
    fun flaggedFoodEditedByNameOnly_savesWithNoWarningAndFlagTrue() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0, flagged = true)
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Name, "Nuevo nombre")

        save(model)

        assertNull(model.uiState.value.energyWarning)
        assertTrue(repository.updates.single().savedAnyway)
    }

    @Test
    fun flaggedEditByNameOnly_stillSkipsTheWarningAfterRecreation() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0, flagged = true)
        val state = SavedStateHandle()
        editModel(state)
        advanceUntilIdle()
        val restored = editModel(state)
        restored.type(CreateFoodField.Name, "Nuevo nombre")
        advanceUntilIdle()

        save(restored)

        assertTrue(repository.updates.single().savedAnyway)
    }

    @Test
    fun flaggedFoodWithChangedKcalThatStillFails_warnsAgain() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0, flagged = true)
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Kcal, "480")

        save(model)

        assertNotNull(model.uiState.value.energyWarning)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun flaggedFoodFixedSoItPasses_savesWithFlagFalse() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0, flagged = true)
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Kcal, "250")

        save(model)

        assertNull(model.uiState.value.energyWarning)
        assertFalse(repository.updates.single().savedAnyway)
    }

    @Test
    fun unflaggedFailingFoodEditedInNameOnly_warns() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0, flagged = false)
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Name, "Nuevo nombre")

        save(model)

        assertNotNull(model.uiState.value.energyWarning)
        assertTrue(repository.updates.isEmpty())
    }

    // ---- restoration

    @Test
    fun openWarning_survivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val model = createModel(state)
        model.fill("500", "10", "30", "10", alcohol = "1")
        advanceUntilIdle()
        save(model)

        val restored = createModel(state)
        advanceUntilIdle()

        assertNotNull(restored.uiState.value.energyWarning)
        assertEquals("1", restored.uiState.value.alcohol)
    }
}
