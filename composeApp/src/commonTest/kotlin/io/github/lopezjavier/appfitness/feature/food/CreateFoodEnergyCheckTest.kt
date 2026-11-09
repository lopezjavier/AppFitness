package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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

    private fun TestScope.save(model: CreateFoodViewModel) {
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
    }

    // ---- pass / skip

    @Test
    fun consistentFood_savesAndShowsNoWarning() = runTest(dispatcher) {
        val model = createModel()
        model.fill("100", "5", "10", "3")
        advanceUntilIdle()

        assertTrue(model.uiState.value.canSave)
        save(model)

        assertNull(model.uiState.value.energyWarning)
        assertEquals(1, repository.created.size)
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
    fun blackCoffeeWithZeroKcal_saves() = runTest(dispatcher) {
        val model = createModel()
        model.fill("0", "0", "0", "0")
        advanceUntilIdle()

        save(model)

        assertEquals(1, repository.created.size)
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

    // ---- hard block

    @Test
    fun epicCase_blocksSaveAndExposesTheWarning500Versus250_withoutAnyEvent() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()

        val state = model.uiState.value
        val warning = assertNotNull(state.energyWarning)
        assertEquals(500.0, warning.kcal, 1e-9)
        assertEquals(250.0, warning.computedKcal, 1e-9)
        assertTrue(warning.clearlyWrong)
        assertFalse(state.canSave)
        save(model)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun fixingTheValues_clearsTheWarningAndEnablesSave() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()

        model.type(CreateFoodField.Kcal, "250")
        advanceUntilIdle()
        assertNull(model.uiState.value.energyWarning)
        assertTrue(model.uiState.value.canSave)
        save(model)

        assertEquals(250.0, repository.created.single().nutrition.kcal)
    }

    @Test
    fun redWineWithoutAlcohol_blocksWithNegativeGap_andAddingItEnablesSave() = runTest(dispatcher) {
        val model = createModel()
        model.fill("83", "0,1", "2,6", "0")
        advanceUntilIdle()

        val warning = assertNotNull(model.uiState.value.energyWarning)
        assertEquals(EnergyCheckLevel.ClearlyWrong, warning.level)
        assertTrue(warning.gap < 0)
        assertFalse(model.uiState.value.canSave)

        model.type(CreateFoodField.Alcohol, "9,5")
        advanceUntilIdle()

        assertNull(model.uiState.value.energyWarning)
        assertTrue(model.uiState.value.canSave)
    }

    @Test
    fun sweetsWithoutPolyols_warnWithPositiveGap() = runTest(dispatcher) {
        val model = createModel()
        model.fill("240", "0", "95", "0")
        advanceUntilIdle()

        assertTrue(assertNotNull(model.uiState.value.energyWarning).gap > 0)
    }

    @Test
    fun mismatch_isNotClearlyWrong_butStillBlocks() = runTest(dispatcher) {
        val model = createModel()
        model.fill("350", "12", "45", "6")
        advanceUntilIdle()

        assertFalse(assertNotNull(model.uiState.value.energyWarning).clearlyWrong)
        assertFalse(model.uiState.value.canSave)
    }

    @Test
    fun tolerance_boundaryIsFifteenPercentPlusFive() = runTest(dispatcher) {
        // Declared 100 kcal: the band is 0.15 * 100 + 5 = 20 kcal. 30 g protein = 120 kcal is exactly on it.
        val atLimit = createModel()
        atLimit.fill("100", "30", "0", "0")
        val beyond = createModel()
        beyond.fill("100", "31", "0", "0")
        advanceUntilIdle()

        assertNull(atLimit.uiState.value.energyWarning)
        assertNotNull(beyond.uiState.value.energyWarning)
    }

    @Test
    fun fibreAndPolyolsTypedInLabelUnitsEnterTheCheck() = runTest(dispatcher) {
        val model = createModel()
        model.fill("350", "12", "45", "6", fibre = "30")
        advanceUntilIdle()

        assertNull(model.uiState.value.energyWarning)
        save(model)
        assertEquals(1, repository.created.size)
    }

    @Test
    fun editingNameBrandOrPortions_keepsTheWarning() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        model.type(CreateFoodField.Name, "Otro")
        model.type(CreateFoodField.Brand, "Marca")
        model.onEvent(CreateFoodEvent.AddPortion)
        advanceUntilIdle()

        assertNotNull(model.uiState.value.energyWarning)
    }

    @Test
    fun blankKcal_hasNoWarningYet() = runTest(dispatcher) {
        val model = createModel()
        model.type(CreateFoodField.Protein, "10")
        advanceUntilIdle()

        assertNull(model.uiState.value.energyWarning)
    }

    @Test
    fun theSectionHoldingPolyols_isExpandedWhileTheWarningShows() = runTest(dispatcher) {
        val model = createModel()
        model.fill("500", "10", "30", "10")
        advanceUntilIdle()
        assertTrue(CreateFoodSection.Fats in model.uiState.value.expandedSections)

        model.type(CreateFoodField.Kcal, "250")
        advanceUntilIdle()

        assertFalse(CreateFoodSection.Fats in model.uiState.value.expandedSections)
    }

    @Test
    fun serverEnergyCheckFailure_isNotASaveFailure_andKeepsTheDraft() = runTest(dispatcher) {
        repository.createFailure = AppException(AppError.FoodEnergyCheckFailed)
        val model = createModel()
        model.fill("100", "5", "10", "3")
        advanceUntilIdle()

        model.effects.test {
            save(model)
            expectNoEvents()
        }

        val state = model.uiState.value
        assertFalse(state.saveFailed)
        assertFalse(state.isSaving)
        assertEquals("100", state.kcal)
    }

    @Test
    fun failedSave_keepsValuesAndFails() = runTest(dispatcher) {
        repository.createFailure = AppException(AppError.Network)
        val model = createModel()
        model.fill("100", "5", "10", "3")
        advanceUntilIdle()

        save(model)

        val state = model.uiState.value
        assertTrue(state.saveFailed)
        assertEquals("100", state.kcal)
    }

    @Test
    fun offline_cannotSave() = runTest(dispatcher) {
        val model = createModel()
        model.fill("100", "5", "10", "3")
        connectivity.state.value = false
        advanceUntilIdle()

        save(model)

        assertFalse(model.uiState.value.canSave)
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
    fun aiDraftWithoutAlcohol_isBlockedWithTheWarningAtOnce() = runTest(dispatcher) {
        val model = createModel(prefill = dishDraft.copy(alcohol = 0.0))
        advanceUntilIdle()

        assertNotNull(model.uiState.value.energyWarning)
        save(model)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun correctedOffFoodDraft_carriesTheBarcodeAndSavesAsCreate() = runTest(dispatcher) {
        val model = createModel(prefill = dishDraft.copy(barcode = "8410000000001"))
        advanceUntilIdle()

        save(model)

        assertEquals("8410000000001", repository.created.single().barcode)
    }

    // ---- edit mode

    private fun storeFood(kcal: Double, protein: Double, carbs: Double, fat: Double) {
        repository.known["f1"] =
            food("f1", isManual = true).copy(nutrition = NutritionPer100(kcal, protein, carbs, fat))
    }

    @Test
    fun edit_loadsAlcohol() = runTest(dispatcher) {
        storeFood(83.0, 0.1, 2.6, 0.0)
        repository.storedAlcohol["f1"] = 9.5
        val model = editModel()
        advanceUntilIdle()

        assertEquals("9,5", model.uiState.value.alcohol)
    }

    @Test
    fun edit_ofAFailingStoredFood_showsTheWarningAtOnceAndBlocksSave() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0)
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Name, "Nuevo nombre")

        assertNotNull(model.uiState.value.energyWarning)
        assertFalse(model.uiState.value.canSave)
        save(model)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun edit_fixedSoItPasses_savesTheUpdate() = runTest(dispatcher) {
        storeFood(500.0, 10.0, 30.0, 10.0)
        val model = editModel()
        advanceUntilIdle()

        model.type(CreateFoodField.Kcal, "250")
        advanceUntilIdle()
        save(model)

        assertNull(model.uiState.value.energyWarning)
        assertEquals(250.0, repository.updates.single().nutrition.kcal)
    }

    @Test
    fun edit_serverEnergyCheckFailure_isNotASaveFailure() = runTest(dispatcher) {
        storeFood(100.0, 5.0, 10.0, 3.0)
        repository.updateFailure = AppException(AppError.FoodEnergyCheckFailed)
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Name, "Nuevo nombre")

        save(model)

        assertFalse(model.uiState.value.saveFailed)
    }

    // ---- restoration

    @Test
    fun blockedDraft_restoresWithTheWarningAndSaveDisabled() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val model = createModel(state)
        model.fill("500", "10", "30", "10", alcohol = "1")
        advanceUntilIdle()

        val restored = createModel(state)
        advanceUntilIdle()

        assertNotNull(restored.uiState.value.energyWarning)
        assertFalse(restored.uiState.value.canSave)
        assertTrue(CreateFoodSection.Fats in restored.uiState.value.expandedSections)
        assertEquals("1", restored.uiState.value.alcohol)
    }
}
