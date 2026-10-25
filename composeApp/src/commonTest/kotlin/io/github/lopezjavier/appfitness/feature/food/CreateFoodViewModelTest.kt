package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.PortionError
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass") // One method per scenario.
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

    private fun CreateFoodViewModel.nutrient(kind: MicronutrientKind, text: String) =
        onEvent(CreateFoodEvent.NutrientChanged(kind, text))

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
    fun labelFields_typedValuesReachTheNewFood_commaAccepted() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()
        model.nutrient(MicronutrientKind.SaturatedFat, "1,2")
        model.nutrient(MicronutrientKind.Sugars, "4.1")
        model.nutrient(MicronutrientKind.Fiber, "2.5")
        model.nutrient(MicronutrientKind.Salt, "0,5")
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(
            NewMicronutrients(
                mapOf(
                    MicronutrientKind.SaturatedFat to 1.2,
                    MicronutrientKind.Sugars to 4.1,
                    MicronutrientKind.Fiber to 2.5,
                    MicronutrientKind.Salt to 0.5,
                ),
            ),
            repository.created.single().micronutrients,
        )
    }

    @Test
    fun labelFields_emptyAreAllowedAndSentAsNotTyped() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()
        model.nutrient(MicronutrientKind.Fiber, "")
        advanceUntilIdle()

        assertTrue(model.uiState.value.canSave)
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(NewMicronutrients(), repository.created.single().micronutrients)
    }

    @Test
    fun labelFields_negativeShowsTheErrorAndDisablesSave() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()

        model.nutrient(MicronutrientKind.Sugars, "-1")
        advanceUntilIdle()

        assertEquals(NewFoodError.Negative, model.uiState.value.errors.nutrients[MicronutrientKind.Sugars])
        assertFalse(model.uiState.value.canSave)
    }

    @Test
    fun labelFields_notANumberShowsTheErrorAndDisablesSave() = runTest(dispatcher) {
        val model = viewModel()
        model.fillTortilla()

        model.nutrient(MicronutrientKind.Salt, "abc")
        advanceUntilIdle()

        assertEquals(NewFoodError.NotANumber, model.uiState.value.errors.nutrients[MicronutrientKind.Salt])
        assertFalse(model.uiState.value.canSave)
    }

    @Test
    fun labelFields_areRestoredFromTheSavedState() = runTest(dispatcher) {
        val model = viewModel()
        model.nutrient(MicronutrientKind.SaturatedFat, "1,2")
        model.nutrient(MicronutrientKind.Fiber, "3")
        advanceUntilIdle()

        val restored = viewModel(state = savedState)
        advanceUntilIdle()

        assertEquals("1,2", restored.uiState.value.nutrients[MicronutrientKind.SaturatedFat])
        assertEquals("3", restored.uiState.value.nutrients[MicronutrientKind.Fiber])
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

    // --- Edit mode (T-102, US-47) ---

    private val yogurt = food(
        "yogurt",
        "Yogur casero",
        kcal = 60.0,
        portions = listOf(FoodPortion("p1", "1 tarrina", 125.0)),
        isManual = true,
    )

    private fun editModel(state: SavedStateHandle = SavedStateHandle()) = CreateFoodViewModel(
        barcode = null,
        name = null,
        foodRepository = repository,
        connectivity = connectivity,
        savedState = state,
        editFoodId = "yogurt",
    )

    @Test
    fun edit_loadsTheFoodAndPrefillsTheDraftKeepingTheUsedUnitReadOnly() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        repository.inUse += "yogurt"
        repository.storedMicronutrients["yogurt"] = mapOf(
            MicronutrientKind.Sugars to 4.7,
            MicronutrientKind.VitaminC to 0.0008,
            MicronutrientKind.Selenium to 0.0000015,
        )
        val model = editModel()
        assertTrue(model.uiState.value.isLoading)

        advanceUntilIdle()

        val state = model.uiState.value
        assertTrue(state.isEdit)
        assertFalse(state.isLoading)
        assertEquals("Yogur casero", state.name)
        assertEquals("60", state.kcal)
        assertEquals("10", state.protein)
        assertEquals("4,7", state.nutrients[MicronutrientKind.Sugars])
        assertEquals("0,8", state.nutrients[MicronutrientKind.VitaminC])
        assertEquals("1,5", state.nutrients[MicronutrientKind.Selenium])
        assertEquals(listOf(PortionDraft("1 tarrina", "125")), state.portions)
        assertTrue(state.unitLocked)
        model.onEvent(CreateFoodEvent.UnitSelected("ml"))
        assertEquals("g", model.uiState.value.baseUnit)
    }

    @Test
    fun edit_unusedFood_unitCanChangeAndIsSent() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()
        assertFalse(model.uiState.value.unitLocked)

        model.onEvent(CreateFoodEvent.UnitSelected("ml"))
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals("ml", model.uiState.value.baseUnit)
        assertEquals("ml", repository.updates.single().baseUnit)
        assertEquals("ml", repository.known.getValue("yogurt").baseUnit)
    }

    @Test
    fun edit_usedFood_savingOtherEditsSendsTheStoredUnit() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        repository.inUse += "yogurt"
        val model = editModel()
        advanceUntilIdle()

        model.type(CreateFoodField.Kcal, "70")
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals("g", repository.updates.single().baseUnit)
    }

    @Test
    fun edit_staleChangeRefused_revertsTheUnitLocksItKeepsTheDraftAndRaisesNoGenericFailure() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()
        model.type(CreateFoodField.Name, "Yogur griego")
        model.onEvent(CreateFoodEvent.UnitSelected("ml"))
        repository.inUse += "yogurt" // an entry was logged from another device meanwhile

        model.effects.test {
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            expectNoEvents()
        }

        val state = model.uiState.value
        assertEquals("g", state.baseUnit)
        assertTrue(state.unitLocked)
        assertTrue(state.unitRefused)
        assertFalse(state.saveFailed)
        assertEquals("Yogur griego", state.name)
        assertTrue(state.canSave)
        assertEquals("g", repository.known.getValue("yogurt").baseUnit)

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertEquals("g", repository.updates.last().baseUnit)
        assertEquals("Yogur griego", repository.known.getValue("yogurt").name)
    }

    private fun TestScope.refusedModel(state: SavedStateHandle = SavedStateHandle()): CreateFoodViewModel {
        repository.known["yogurt"] = yogurt
        val model = editModel(state)
        advanceUntilIdle()
        model.onEvent(CreateFoodEvent.UnitSelected("ml"))
        repository.inUse += "yogurt"
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertTrue(model.uiState.value.unitRefused)
        return model
    }

    @Test
    fun edit_refusal_clearsOnALaterFieldEdit_keepsTheUnitLocked_andDropsTheSavedFlag() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val model = refusedModel(state)

        model.type(CreateFoodField.Name, "Yogur griego")
        advanceUntilIdle()

        val current = model.uiState.value
        assertFalse(current.unitRefused)
        assertTrue(current.unitLocked)
        assertEquals("Yogur griego", current.name)
        assertEquals("g", current.baseUnit)
        assertFalse(editModel(state).also { advanceUntilIdle() }.uiState.value.unitRefused)
    }

    @Test
    fun edit_refusal_clearsOnALaterNutrientEdit() = runTest(dispatcher) {
        val model = refusedModel()

        model.onEvent(CreateFoodEvent.NutrientChanged(MicronutrientKind.Fiber, "3"))
        advanceUntilIdle()

        assertFalse(model.uiState.value.unitRefused)
        assertTrue(model.uiState.value.unitLocked)
    }

    @Test
    fun edit_refusal_clearsWhenTheUserRetriesAndTheRetryFails() = runTest(dispatcher) {
        val model = refusedModel()
        repository.updateFailure = AppException(AppError.Network)

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertFalse(model.uiState.value.unitRefused)
        assertTrue(model.uiState.value.unitLocked)
    }

    @Test
    fun edit_refusedStateSurvivesRecreation() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val state = SavedStateHandle()
        val first = editModel(state)
        advanceUntilIdle()
        first.onEvent(CreateFoodEvent.UnitSelected("ml"))
        repository.inUse += "yogurt"
        first.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        val restored = editModel(state)
        advanceUntilIdle()

        assertEquals("g", restored.uiState.value.baseUnit)
        assertTrue(restored.uiState.value.unitLocked)
        assertTrue(restored.uiState.value.unitRefused)
    }

    @Test
    fun edit_changedUnitSurvivesRecreationAndStaysEditable() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val state = SavedStateHandle()
        val first = editModel(state)
        advanceUntilIdle()
        first.onEvent(CreateFoodEvent.UnitSelected("ml"))

        val restored = editModel(state)
        advanceUntilIdle()

        assertEquals("ml", restored.uiState.value.baseUnit)
        assertFalse(restored.uiState.value.unitLocked)
    }

    @Test
    fun edit_otherSaveFailures_stillRaiseSaveFailedAndDoNotLock() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        repository.updateFailure = AppException(AppError.Network)
        val model = editModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(CreateFoodEvent.UnitSelected("ml"))
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.SaveFailed, awaitItem())
        }
        assertFalse(model.uiState.value.unitLocked)
        assertEquals("ml", model.uiState.value.baseUnit)
    }

    @Test
    fun edit_changingKcalFrom60To70_callsUpdateWithTheNewValueAndEmitsSaved() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()

        model.effects.test {
            model.type(CreateFoodField.Kcal, "70")
            advanceUntilIdle()
            assertTrue(model.uiState.value.canSave)
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()

            assertEquals(CreateFoodEffect.Saved("yogurt"), awaitItem())
        }
        assertEquals(70.0, repository.updates.single().nutrition.kcal)
        assertEquals("yogurt", repository.updates.single().id)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun edit_addingEditingAndRemovingAPortion() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()

        model.onEvent(CreateFoodEvent.AddPortion)
        model.onEvent(CreateFoodEvent.PortionLabelChanged(1, "1 tarrina grande"))
        model.onEvent(CreateFoodEvent.PortionAmountChanged(1, "125"))
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertEquals(
            listOf(NewPortion("1 tarrina", 125.0), NewPortion("1 tarrina grande", 125.0)),
            repository.updates.last().portions,
        )

        model.onEvent(CreateFoodEvent.PortionAmountChanged(1, "120"))
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertEquals(NewPortion("1 tarrina grande", 120.0), repository.updates.last().portions.last())

        model.onEvent(CreateFoodEvent.RemovePortion(1))
        model.onEvent(CreateFoodEvent.RemovePortion(0))
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.updates.last().portions.isEmpty())
    }

    @Test
    fun edit_fullLabelKeepsTypedLabelUnitsAndStoresGrams() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()

        model.nutrient(MicronutrientKind.VitaminC, "80")
        model.nutrient(MicronutrientKind.Iodine, "150")
        model.nutrient(MicronutrientKind.Starch, "3,5")
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        val values = repository.updates.single().micronutrients.values
        assertEquals(
            mapOf(
                MicronutrientKind.VitaminC to 80.0,
                MicronutrientKind.Iodine to 150.0,
                MicronutrientKind.Starch to 3.5,
            ),
            values,
        )
        assertEquals(0.08, repository.storedMicronutrients["yogurt"]?.get(MicronutrientKind.VitaminC))
    }

    @Test
    fun edit_invalidPortionOrNutrientBlocksSave() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()

        model.onEvent(CreateFoodEvent.PortionAmountChanged(0, "0"))
        advanceUntilIdle()
        assertFalse(model.uiState.value.canSave)
        assertEquals(PortionError.AmountInvalid, model.uiState.value.errors.portions[0])

        model.onEvent(CreateFoodEvent.PortionAmountChanged(0, "125"))
        model.nutrient(MicronutrientKind.Calcium, "-3")
        advanceUntilIdle()
        assertFalse(model.uiState.value.canSave)
        assertEquals(NewFoodError.Negative, model.uiState.value.errors.nutrients[MicronutrientKind.Calcium])
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun edit_offlineBlocksSave() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(model.uiState.value.canSave)
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun edit_saveFailureKeepsTheDraftAndShowsTheError() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        repository.updateFailure = AppException(AppError.Network)
        val model = editModel()
        advanceUntilIdle()

        model.effects.test {
            model.type(CreateFoodField.Kcal, "70")
            model.onEvent(CreateFoodEvent.Save)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.SaveFailed, awaitItem())
        }
        assertTrue(model.uiState.value.saveFailed)
        assertEquals("70", model.uiState.value.kcal)
        assertTrue(model.uiState.value.canSave)
    }

    @Test
    fun edit_discardingWritesNothing() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val model = editModel()
        advanceUntilIdle()

        model.type(CreateFoodField.Kcal, "999")
        model.nutrient(MicronutrientKind.Salt, "1")
        model.onEvent(CreateFoodEvent.RemovePortion(0))
        advanceUntilIdle()

        assertTrue(repository.updates.isEmpty())
        assertTrue(repository.created.isEmpty())
        assertEquals(60.0, repository.known.getValue("yogurt").nutrition.kcal)
    }

    @Test
    fun edit_loadFailureCanBeRetried() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        repository.editableFailures = 1
        val model = editModel()
        advanceUntilIdle()

        assertTrue(model.uiState.value.loadFailed)
        assertFalse(model.uiState.value.isLoading)

        model.onEvent(CreateFoodEvent.Retry)
        advanceUntilIdle()

        assertFalse(model.uiState.value.loadFailed)
        assertEquals("Yogur casero", model.uiState.value.name)
    }

    @Test
    fun edit_aFoodThatIsNotManualFailsToLoad() = runTest(dispatcher) {
        repository.known["off"] = food("off")
        val model = CreateFoodViewModel(null, null, repository, connectivity, SavedStateHandle(), editFoodId = "off")
        advanceUntilIdle()

        assertTrue(model.uiState.value.loadFailed)
    }

    @Test
    fun edit_recreatedViewModelKeepsTheTypedDraftAndDoesNotReload() = runTest(dispatcher) {
        repository.known["yogurt"] = yogurt
        val state = SavedStateHandle()
        val first = editModel(state)
        advanceUntilIdle()
        first.type(CreateFoodField.Kcal, "70")
        first.nutrient(MicronutrientKind.VitaminC, "12")
        advanceUntilIdle()
        val loads = repository.editableLoads.size

        val restored = editModel(state)
        advanceUntilIdle()

        assertEquals(loads, repository.editableLoads.size)
        assertFalse(restored.uiState.value.isLoading)
        assertEquals("70", restored.uiState.value.kcal)
        assertEquals("12", restored.uiState.value.nutrients[MicronutrientKind.VitaminC])
        assertTrue(restored.uiState.value.isEdit)
    }

    @Test
    fun create_isNotInEditModeAndOffersEveryLabelNutrient() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.uiState.value.isEdit)
        assertFalse(model.uiState.value.isLoading)
        assertEquals(MicronutrientKind.editableKinds, model.uiState.value.labelRows)
    }

    @Test
    fun sections_startCollapsed_toggleAndSurviveRecreation() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val model = viewModel(state = state)
        advanceUntilIdle()
        assertEquals(emptySet(), model.uiState.value.expandedSections)

        model.onEvent(CreateFoodEvent.ToggleSection(CreateFoodSection.Vitamins))
        advanceUntilIdle()
        assertEquals(setOf(CreateFoodSection.Vitamins), model.uiState.value.expandedSections)

        val restored = viewModel(state = state)
        advanceUntilIdle()
        assertEquals(setOf(CreateFoodSection.Vitamins), restored.uiState.value.expandedSections)

        restored.onEvent(CreateFoodEvent.ToggleSection(CreateFoodSection.Vitamins))
        advanceUntilIdle()
        assertEquals(emptySet(), restored.uiState.value.expandedSections)
    }

    @Test
    fun sections_partitionEveryEditableNutrientNotInTheUsualRows() {
        val grouped = CreateFoodSection.entries.flatMap { it.kinds }
        val usual = CreateFoodSection.USUAL_KINDS
        assertEquals(MicronutrientKind.editableKinds.toSet(), (grouped + usual).toSet())
        assertEquals(grouped.size + usual.size, MicronutrientKind.editableKinds.size)
    }
}
