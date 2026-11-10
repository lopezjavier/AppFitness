package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-135: alcohol by volume for an ml food; the draft and the saved value stay grams per 100. */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateFoodAlcoholTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createModel(state: SavedStateHandle = SavedStateHandle()) = CreateFoodViewModel(
        barcode = null,
        name = null,
        foodRepository = repository,
        connectivity = connectivity,
        savedState = state,
    )

    private fun CreateFoodViewModel.type(field: CreateFoodField, text: String) =
        onEvent(CreateFoodEvent.FieldChanged(field, text))

    private fun CreateFoodViewModel.abv(text: String) = onEvent(CreateFoodEvent.AlcoholAbvChanged(text))

    private fun CreateFoodViewModel.wineInGrados() {
        onEvent(CreateFoodEvent.UnitSelected("ml"))
        onEvent(CreateFoodEvent.AlcoholModeSelected(AlcoholMode.Grados))
    }

    private fun CreateFoodViewModel.fillWine() {
        type(CreateFoodField.Name, "Vino tinto")
        type(CreateFoodField.Kcal, "83")
        type(CreateFoodField.Protein, "0,1")
        type(CreateFoodField.Carbs, "2,6")
        type(CreateFoodField.Fat, "0")
    }

    @Test
    fun defaultsToGramsMode() = runTest(dispatcher) {
        val state = createModel().uiState.value

        assertEquals(AlcoholMode.Grams, state.alcoholMode)
        assertEquals("", state.alcoholAbv)
        assertNull(state.alcoholConvertedG)
    }

    @Test
    fun typingGrados_setsTheGramsAndShowsTheConversion() = runTest(dispatcher) {
        val model = createModel()
        model.wineInGrados()
        model.abv("5")

        advanceUntilIdle()
        assertEquals("3,9", model.uiState.value.alcohol)
        assertEquals("3,9", model.uiState.value.alcoholConvertedG)
        assertEquals("5", model.uiState.value.alcoholAbv)
    }

    @Test
    fun redWineAtTwelvePercent_passesTheEnergyCheckAndSavesGrams() = runTest(dispatcher) {
        val model = createModel()
        model.fillWine()
        model.wineInGrados()
        model.abv("12")
        advanceUntilIdle()

        advanceUntilIdle()
        assertNull(model.uiState.value.energyWarning)
        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(9.5, repository.created.single().alcoholG)
    }

    @Test
    fun blankGrados_clearsTheGrams() = runTest(dispatcher) {
        val model = createModel()
        model.wineInGrados()
        model.abv("5")
        model.abv("")

        advanceUntilIdle()
        assertEquals("", model.uiState.value.alcohol)
        assertNull(model.uiState.value.alcoholConvertedG)
    }

    @Test
    fun invalidGrados_clearsTheGrams() = runTest(dispatcher) {
        val model = createModel()
        model.wineInGrados()
        model.abv("5")
        model.abv("abc")

        advanceUntilIdle()
        assertEquals("", model.uiState.value.alcohol)
        assertNull(model.uiState.value.alcoholConvertedG)
    }

    @Test
    fun switchingModeKeepsTheGrams() = runTest(dispatcher) {
        val model = createModel()
        model.wineInGrados()
        model.abv("12")
        model.onEvent(CreateFoodEvent.AlcoholModeSelected(AlcoholMode.Grams))

        advanceUntilIdle()
        assertEquals(AlcoholMode.Grams, model.uiState.value.alcoholMode)
        assertEquals("9,5", model.uiState.value.alcohol)
    }

    @Test
    fun typingGrams_clearsTheGrados() = runTest(dispatcher) {
        val model = createModel()
        model.wineInGrados()
        model.abv("12")
        model.type(CreateFoodField.Alcohol, "8")

        advanceUntilIdle()
        assertEquals("", model.uiState.value.alcoholAbv)
        assertEquals("8", model.uiState.value.alcohol)
    }

    @Test
    fun switchingToGrams_resetsTheModeAndClearsGradosButKeepsTheGrams() = runTest(dispatcher) {
        val model = createModel()
        model.wineInGrados()
        model.abv("12")
        model.onEvent(CreateFoodEvent.UnitSelected("g"))

        advanceUntilIdle()
        val state = model.uiState.value
        assertEquals(AlcoholMode.Grams, state.alcoholMode)
        assertEquals("", state.alcoholAbv)
        assertEquals("9,5", state.alcohol)
    }

    @Test
    fun gradosMode_isIgnoredForAGramsFood() = runTest(dispatcher) {
        val model = createModel()
        model.onEvent(CreateFoodEvent.AlcoholModeSelected(AlcoholMode.Grados))

        advanceUntilIdle()
        assertEquals(AlcoholMode.Grams, model.uiState.value.alcoholMode)
    }

    @Test
    fun modeAndGradosSurviveRecreation() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val first = createModel(state)
        first.wineInGrados()
        first.abv("5")

        val restored = createModel(state).uiState.value

        assertEquals(AlcoholMode.Grados, restored.alcoholMode)
        assertEquals("5", restored.alcoholAbv)
        assertEquals("3,9", restored.alcohol)
        assertEquals("3,9", restored.alcoholConvertedG)
    }

    @Test
    fun edit_opensInGramsModeShowingGrams() = runTest(dispatcher) {
        repository.known["f1"] =
            food("f1", isManual = true).copy(nutrition = NutritionPer100(83.0, 0.1, 2.6, 0.0))
        repository.storedAlcohol["f1"] = 9.5
        val model = CreateFoodViewModel(null, null, repository, connectivity, SavedStateHandle(), editFoodId = "f1")
        advanceUntilIdle()

        advanceUntilIdle()
        val state = model.uiState.value
        assertEquals(AlcoholMode.Grams, state.alcoholMode)
        assertEquals("9,5", state.alcohol)
        assertTrue(state.alcoholAbv.isEmpty())
    }
}
