package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.PortionError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateFoodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(barcode: String? = null, name: String? = null, state: SavedStateHandle = savedState) =
        CreateFoodViewModel(barcode, name, repository, connectivity, state)

    private fun CreateFoodViewModel.type(field: CreateFoodField, text: String) =
        onEvent(CreateFoodEvent.FieldChanged(field, text))

    private fun CreateFoodViewModel.fillTortilla() {
        type(CreateFoodField.Name, "Tortilla de patatas casera")
        type(CreateFoodField.Kcal, "190")
        type(CreateFoodField.Protein, "7")
        type(CreateFoodField.Carbs, "15")
        type(CreateFoodField.Fat, "11")
    }

    @Test
    fun prefill_fromBarcodeAndName() = runTest(dispatcher) {
        val model = viewModel(barcode = "8410000000001", name = "Tortilla")
        advanceUntilIdle()

        assertEquals("8410000000001", model.uiState.value.barcode)
        assertEquals("Tortilla", model.uiState.value.name)
        assertEquals("g", model.uiState.value.baseUnit)
    }

    @Test
    fun prefill_doesNotOverwriteARestoredDraft() = runTest(dispatcher) {
        val first = viewModel(name = "Tortilla")
        first.type(CreateFoodField.Name, "Otra cosa")

        val restored = viewModel(name = "Tortilla", state = savedState)

        assertEquals("Otra cosa", restored.uiState.value.name)
    }

    @Test
    fun emptyForm_cannotBeSaved_andShowsNoErrorYet() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.uiState.value.canSave)
        assertNull(model.uiState.value.errors.name)
    }

    @Test
    fun clearedName_showsRequiredAndDisablesSave() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()

        model.type(CreateFoodField.Name, "")
        advanceUntilIdle()

        assertEquals(NewFoodError.Required, model.uiState.value.errors.name)
        assertFalse(model.uiState.value.canSave)
    }

    @Test
    fun negativeKcal_showsNegativeAndDisablesSave() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()

        model.type(CreateFoodField.Kcal, "-1")
        advanceUntilIdle()

        assertEquals(NewFoodError.Negative, model.uiState.value.errors.kcal)
        assertFalse(model.uiState.value.canSave)
    }

    @Test
    fun tortillaExample_canBeSaved_andSendsBaseUnitGAndValues() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)

        model.effects.test {
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.Saved("new-1"), awaitItem())
        }
        val sent = repository.created.single()
        assertEquals("Tortilla de patatas casera", sent.name)
        assertEquals("g", sent.baseUnit)
        assertEquals(NutritionPer100(190.0, 7.0, 15.0, 11.0), sent.nutrition)
        assertNull(sent.barcode)
    }

    @Test
    fun blankMacros_areSentAsZero_butKcalIsRequired() = runTest(dispatcher) {
        val model = viewModel()
        model.type(CreateFoodField.Name, "Agua")
        advanceUntilIdle()
        assertFalse(model.uiState.value.canSave)

        model.type(CreateFoodField.Kcal, "0")
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(NutritionPer100(0.0, 0.0, 0.0, 0.0), repository.created.single().nutrition)
    }

    @Test
    fun commaDecimalSeparator_isAccepted() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()
        model.type(CreateFoodField.Protein, "7,5")

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(7.5, repository.created.single().nutrition.proteinG)
    }

    @Test
    fun mlBaseUnit_andBarcode_areSent() = runTest(dispatcher) {
        val model = viewModel(barcode = "8410000000001")
        model.fillTortilla()
        model.onEvent(CreateFoodEvent.UnitSelected("ml"))

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals("ml", repository.created.single().baseUnit)
        assertEquals("8410000000001", repository.created.single().barcode)
    }

    @Test
    fun portion_addedEditedRemovedAndSent() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()

        model.onEvent(CreateFoodEvent.AddPortion)
        model.onEvent(CreateFoodEvent.PortionLabelChanged(0, "1 ración"))
        model.onEvent(CreateFoodEvent.PortionAmountChanged(0, "250"))
        model.onEvent(CreateFoodEvent.AddPortion)
        model.onEvent(CreateFoodEvent.RemovePortion(1))
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.portions.size)
        assertTrue(model.uiState.value.canSave)

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(NewPortion("1 ración", 250.0)), repository.created.single().portions)
    }

    @Test
    fun portion_zeroAmount_isAnErrorAndDisablesSave() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()
        model.onEvent(CreateFoodEvent.AddPortion)
        model.onEvent(CreateFoodEvent.PortionLabelChanged(0, "ración"))
        model.onEvent(CreateFoodEvent.PortionAmountChanged(0, "0"))
        advanceUntilIdle()

        assertEquals(PortionError.AmountInvalid, model.uiState.value.errors.portions[0])
        assertFalse(model.uiState.value.canSave)
    }

    @Test
    fun portions_areLimitedToTen() = runTest(dispatcher) {
        val model = viewModel()
        repeat(12) { model.onEvent(CreateFoodEvent.AddPortion) }
        advanceUntilIdle()

        assertEquals(10, model.uiState.value.portions.size)
        assertFalse(model.uiState.value.canAddPortion)
    }

    @Test
    fun saveFailure_keepsTheDraft_showsTheError_andAllowsRetry() = runTest(dispatcher) {
        repository.createFailure = AppException(AppError.Network)
        val model = viewModel()
        model.fillTortilla()

        model.effects.test {
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.SaveFailed, awaitItem())
        }
        assertTrue(model.uiState.value.saveFailed)
        assertFalse(model.uiState.value.isSaving)
        assertEquals("Tortilla de patatas casera", model.uiState.value.name)

        repository.createFailure = null
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertFalse(model.uiState.value.saveFailed)
        assertEquals(2, repository.created.size)
    }

    @Test
    fun doubleTap_savesOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val model = viewModel()
        model.fillTortilla()

        model.effects.test {
            model.onEvent(CreateFoodEvent.Save)
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            assertTrue(model.uiState.value.isSaving)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.Saved("new-1"), awaitItem())
            expectNoEvents()
        }
        assertEquals(1, repository.created.size)
    }

    @Test
    fun offline_disablesSave() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()
        connectivity.state.value = false
        advanceUntilIdle()

        assertTrue(model.uiState.value.isOffline)
        assertFalse(model.uiState.value.canSave)
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun draft_isRestoredFromTheSavedState() = runTest(dispatcher) {
        val model = viewModel(barcode = "8410000000001")
        model.type(CreateFoodField.Name, "Tortilla")
        model.type(CreateFoodField.Kcal, "190")
        model.onEvent(CreateFoodEvent.UnitSelected("ml"))
        model.onEvent(CreateFoodEvent.AddPortion)
        model.onEvent(CreateFoodEvent.PortionLabelChanged(0, "1 ración"))
        model.onEvent(CreateFoodEvent.PortionAmountChanged(0, "250"))
        advanceUntilIdle()

        val restored = viewModel(barcode = "8410000000001", state = savedState)
        advanceUntilIdle()

        assertEquals(model.uiState.value, restored.uiState.value)
    }
}
