package io.github.lopezjavier.appfitness.feature.food

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.EnergyIssue
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.PreviousEstimate
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import io.github.lopezjavier.appfitness.feature.food.photo.FakeFoodPhotoRepository
import io.github.lopezjavier.appfitness.feature.food.photo.FakePhotoCapture
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoRetryStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Repetir la estimación": the AI retry of a failing estimate from the create-food form (ADR-104). */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class CreateFoodRetryTest {
    private val dispatcher = StandardTestDispatcher()
    private val foods = FakeFoodRepository()
    private val photos = FakeFoodPhotoRepository()
    private val capture = FakePhotoCapture()
    private val store = FoodPhotoRetryStore(capture)
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val failingDraft = NewFoodDraft(
        name = "Lentejas",
        brand = "Marca",
        baseUnit = "g",
        kcal = 500.0,
        protein = 10.0,
        carbs = 30.0,
        fat = 10.0,
        micronutrients = NewMicronutrients(),
        portions = listOf(NewPortion("1 plato", 300.0)),
    )

    private fun token(mode: FoodPhotoMode = FoodPhotoMode.Dish, comment: String? = "con salsa") =
        store.put("photo-1", mode, comment)

    private fun model(retryToken: String?, state: SavedStateHandle = SavedStateHandle()) = CreateFoodViewModel(
        barcode = null,
        name = null,
        foodRepository = foods,
        connectivity = connectivity,
        savedState = state,
        prefill = NewFoodDraftCodec.encode(failingDraft),
        retryToken = retryToken,
        photoRepository = photos,
        photoCapture = capture,
        retryStore = store,
    )

    private fun CreateFoodViewModel.retry() = onEvent(CreateFoodEvent.RetryEstimate)

    @Test
    fun aFormFromAFailingEstimate_offersTheRetry_andAFormWithoutAPhotoDoesNot() = runTest(dispatcher) {
        val withPhoto = model(token())
        val without = model(null)
        advanceUntilIdle()

        assertEquals(RetryState.Available, withPhoto.uiState.value.retryState)
        assertEquals(RetryState.None, without.uiState.value.retryState)
    }

    @Test
    fun retry_sendsTheCurrentValuesAndTheReason_withTheStoredModeAndComment() = runTest(dispatcher) {
        val model = model(token())
        advanceUntilIdle()
        model.onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Kcal, "480"))

        model.retry()
        advanceUntilIdle()

        val call = photos.calls.single()
        assertEquals(FoodPhotoMode.Dish, call.mode)
        assertEquals("con salsa", call.comment)
        assertEquals(Base64.encode(capture.bytes), call.imageBase64)
        assertEquals(
            PreviousEstimate(
                name = "Lentejas",
                baseUnit = "g",
                kcal = 480.0,
                protein = 10.0,
                carbs = 30.0,
                fat = 10.0,
                alcohol = null,
                fibre = null,
                polyols = null,
                reason = EnergyIssue(EnergyCheckLevel.ClearlyWrong, 480.0, 250.0, -230.0),
            ),
            call.previous,
        )
    }

    @Test
    fun textRetry_resendsTheTypedTextWithTheCurrentValuesAndNoImage() = runTest(dispatcher) {
        val model = model(store.putText("pollo con ensalada"))
        advanceUntilIdle()

        model.retry()
        advanceUntilIdle()

        val (text, previous) = photos.textCalls.single()
        assertEquals("pollo con ensalada", text)
        assertNotNull(previous)
        assertEquals(500.0, previous.kcal)
        assertTrue(photos.calls.isEmpty())
        assertTrue(capture.reads.isEmpty())
    }

    @Test
    fun textRetry_thatPasses_releasesTheTokenAndKeepsTheFormValuesApplied() = runTest(dispatcher) {
        val token = store.putText("pollo con ensalada")
        val model = model(token)
        advanceUntilIdle()

        model.retry()
        advanceUntilIdle()

        assertEquals(RetryState.None, model.uiState.value.retryState)
        assertFalse(store.has(token))
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun textOrigin_isExposed_andSurvivesProcessDeathAsTextGone() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val withText = model(store.putText("pollo"), state)
        advanceUntilIdle()
        val withPhoto = model(token())
        advanceUntilIdle()

        val afterDeath = CreateFoodViewModel(
            barcode = null,
            name = null,
            foodRepository = foods,
            connectivity = connectivity,
            savedState = state,
            photoRepository = photos,
            photoCapture = capture,
            retryStore = FoodPhotoRetryStore(capture),
        )
        advanceUntilIdle()

        assertTrue(withText.uiState.value.retryFromText)
        assertFalse(withPhoto.uiState.value.retryFromText)
        assertEquals(RetryState.PhotoGone, afterDeath.uiState.value.retryState)
        assertTrue(afterDeath.uiState.value.retryFromText)
        afterDeath.retry()
        advanceUntilIdle()
        assertTrue(photos.textCalls.isEmpty())
    }

    @Test
    fun retry_overwritesOnlyTheNutritionFields_keepingNameBrandAndPortions() = runTest(dispatcher) {
        photos.results += Result.success(
            FakeFoodPhotoRepository.lentils.copy(
                name = "Otro nombre",
                brand = "Otra marca",
                alcohol = 1.0,
                micronutrients = mapOf("fiber_100g" to 2.0, "polyols_100g" to 5.0),
            ),
        )
        val model = model(token())
        advanceUntilIdle()

        model.retry()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals("Lentejas", state.name)
        assertEquals("Marca", state.brand)
        assertEquals(listOf(PortionDraft("1 plato", "300")), state.portions)
        assertEquals("180", state.kcal)
        assertEquals("6", state.protein)
        assertEquals("15", state.carbs)
        assertEquals("10", state.fat)
        assertEquals("1", state.alcohol)
        assertEquals(
            listOf("2", "5"),
            listOf(
                state.nutrients.getValue(MicronutrientKind.Fiber),
                state.nutrients.getValue(MicronutrientKind.Polyols),
            ),
        )
        assertTrue(CreateFoodSection.Fats in state.expandedSections)
        assertTrue(state.aiEstimateNote)
    }

    @Test
    fun retry_thatStillFails_keepsTheIconTheWarningAndThePhoto() = runTest(dispatcher) {
        photos.results += Result.success(FakeFoodPhotoRepository.lentils.copy(kcal = 400.0))
        val model = model(token())
        advanceUntilIdle()

        model.retry()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(RetryState.Available, state.retryState)
        assertEquals("400", state.kcal)
        assertNotNull(state.energyWarning)
        assertFalse(state.canSave)
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun retry_thatPasses_removesTheIcon_releasesThePhoto_andEnablesSave() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val token = token()
        val model = model(token, state)
        advanceUntilIdle()

        model.retry()
        advanceUntilIdle()

        assertEquals(RetryState.None, model.uiState.value.retryState)
        assertNull(model.uiState.value.energyWarning)
        assertTrue(model.uiState.value.canSave)
        assertEquals(listOf("photo-1"), capture.discarded)
        assertFalse(store.has(token))
        assertNull(state.get<String>("create_food_retry_token"))
    }

    @Test
    fun retry_whileRunning_isRunningAndBlocksSave_thenIsAvailableAgainAfterAFailure() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        photos.gate = gate
        photos.results += Result.failure(AppException(AppError.ServiceUnavailable))
        val model = model(token())
        advanceUntilIdle()

        model.effects.test {
            model.retry()
            advanceUntilIdle()
            assertEquals(RetryState.Running, model.uiState.value.retryState)
            assertFalse(model.uiState.value.canSave)
            model.retry()
            advanceUntilIdle()
            assertEquals(1, photos.calls.size)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(CreateFoodEffect.RetryFailed, awaitItem())
        }
        assertEquals(RetryState.Available, model.uiState.value.retryState)
    }

    @Test
    fun retryFailure_keepsTheDraftAndThePhoto_andIsNotASaveFailure() = runTest(dispatcher) {
        photos.results += Result.failure(AppException(AppError.Network))
        val model = model(token())
        advanceUntilIdle()
        val before = model.uiState.value

        model.retry()
        advanceUntilIdle()

        val after = model.uiState.value
        assertEquals(before.kcal, after.kcal)
        assertEquals(before.protein, after.protein)
        assertFalse(after.saveFailed)
        assertNotNull(after.energyWarning)
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun retry_offline_isIgnored() = runTest(dispatcher) {
        val model = model(token())
        connectivity.state.value = false
        advanceUntilIdle()

        model.retry()
        advanceUntilIdle()

        assertTrue(photos.calls.isEmpty())
        assertTrue(model.uiState.value.isOffline)
        assertEquals(RetryState.Available, model.uiState.value.retryState)
    }

    @Test
    fun retry_whenTheFormPassesAlready_isIgnored() = runTest(dispatcher) {
        val model = model(token())
        advanceUntilIdle()
        model.onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Kcal, "250"))

        model.retry()
        advanceUntilIdle()

        assertTrue(photos.calls.isEmpty())
    }

    @Test
    fun recreatedInProcess_withTheStoreAlive_isStillAvailable() = runTest(dispatcher) {
        val state = SavedStateHandle()
        model(token(), state)
        advanceUntilIdle()

        val restored = model(null, state)
        advanceUntilIdle()

        assertEquals(RetryState.Available, restored.uiState.value.retryState)
    }

    @Test
    fun recreatedAfterProcessDeath_withAnEmptyStore_isPhotoGone_withTheDraftAndWarningIntact() = runTest(dispatcher) {
        val state = SavedStateHandle()
        model(token(), state)
        advanceUntilIdle()

        val afterDeath = CreateFoodViewModel(
            barcode = null,
            name = null,
            foodRepository = foods,
            connectivity = connectivity,
            savedState = state,
            photoRepository = photos,
            photoCapture = capture,
            retryStore = FoodPhotoRetryStore(capture),
        )
        advanceUntilIdle()

        val restored = afterDeath.uiState.value
        assertEquals(RetryState.PhotoGone, restored.retryState)
        assertEquals("500", restored.kcal)
        assertNotNull(restored.energyWarning)
        afterDeath.retry()
        advanceUntilIdle()
        assertTrue(photos.calls.isEmpty())
    }

    @Test
    fun saving_releasesThePhoto() = runTest(dispatcher) {
        val model = model(token())
        advanceUntilIdle()
        model.onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Kcal, "250"))
        advanceUntilIdle()

        model.onEvent(CreateFoodEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf("photo-1"), capture.discarded)
        assertEquals(1, foods.created.size)
    }

    @Test
    fun release_discardsThePhotoOnce_evenIfCalledTwice() = runTest(dispatcher) {
        val token = token()
        val model = model(token)
        advanceUntilIdle()

        model.release()
        model.release()

        assertEquals(listOf("photo-1"), capture.discarded)
        assertFalse(store.has(token))
    }

    @Test
    fun editMode_neverOffersTheRetry() = runTest(dispatcher) {
        foods.known["f1"] = food("f1", isManual = true)
        val edit = CreateFoodViewModel(
            barcode = null,
            name = null,
            foodRepository = foods,
            connectivity = connectivity,
            savedState = SavedStateHandle(),
            editFoodId = "f1",
            retryToken = token(),
            photoRepository = photos,
            photoCapture = capture,
            retryStore = store,
        )
        advanceUntilIdle()

        assertEquals(RetryState.None, edit.uiState.value.retryState)
    }
}
