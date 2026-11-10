package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.toNewFoodDraft
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MEAL = "pechuga de pollo con ensalada, 2 cervezas y 4 calamares"

/** "Describir comida": the text-input step of the add-with-AI flow (US-132). */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodPhotoTextViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeFoodPhotoRepository()
    private val capture = FakePhotoCapture()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val retryStore = FoodPhotoRetryStore(capture)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) =
        FoodPhotoViewModel(photos, capture, connectivity, state, retryStore)

    private fun FoodPhotoViewModel.step() = uiState.value.step

    private fun TestScope.toTextInput(model: FoodPhotoViewModel) {
        advanceUntilIdle()
        model.onEvent(FoodPhotoEvent.Open)
        model.onEvent(FoodPhotoEvent.ModeChosen(FoodPhotoMode.Text))
    }

    private fun FoodPhotoViewModel.type(text: String = MEAL) = onEvent(FoodPhotoEvent.MealTextChanged(text))

    private val failing = FakeFoodPhotoRepository.lentils.copy(kcal = 500.0, protein = 10.0, carbs = 30.0, fat = 10.0)

    @Test
    fun choosingText_opensTheTextInputWithoutAskingForAPhotoSource() = runTest(dispatcher) {
        val model = viewModel()

        toTextInput(model)

        assertEquals(FoodPhotoStep.TextInput, model.step())
        assertEquals(FoodPhotoMode.Text, model.uiState.value.mode)
        assertTrue(capture.captured.isEmpty())
    }

    @Test
    fun typing_isCappedAt500Characters() = runTest(dispatcher) {
        val model = viewModel()
        toTextInput(model)

        model.type("a".repeat(MAX_MEAL_TEXT + 20))

        assertEquals(MAX_MEAL_TEXT, model.uiState.value.mealText.length)
    }

    @Test
    fun send_estimatesTheTrimmedTextWithoutAnyPhoto() = runTest(dispatcher) {
        val model = viewModel()
        toTextInput(model)
        model.type("  $MEAL  ")

        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        assertEquals(listOf(MEAL), photos.textCalls.map { it.first })
        assertNull(photos.textCalls.single().second)
        assertTrue(photos.calls.isEmpty())
        assertTrue(capture.reads.isEmpty())
    }

    @Test
    fun send_whileEstimating_showsTheProgressStep() = runTest(dispatcher) {
        photos.gate = CompletableDeferred()
        val model = viewModel()
        toTextInput(model)
        model.type()

        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Estimating, model.step())
    }

    @Test
    fun success_opensTheDraftWithTheMealAsOnePortion_clearsTheText_andHasNoToken() = runTest(dispatcher) {
        val model = viewModel()
        toTextInput(model)
        model.type()

        model.effects.test {
            model.onEvent(FoodPhotoEvent.SendMealText)
            advanceUntilIdle()
            val effect = awaitItem() as FoodPhotoEffect.OpenPrefilledForm
            assertEquals(FakeFoodPhotoRepository.lentils.toNewFoodDraft(FoodPhotoMode.Text), effect.draft)
            assertEquals(listOf(NewPortion("1 ración", 250.0)), effect.draft.portions)
            assertNull(effect.retryToken)
        }
        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals("", model.uiState.value.mealText)
    }

    @Test
    fun failingDraft_handsTheTextToTheRetryStoreWithATokenAndDiscardsNoPhoto() = runTest(dispatcher) {
        photos.result = Result.success(failing)
        val model = viewModel()
        toTextInput(model)
        model.type()

        model.effects.test {
            model.onEvent(FoodPhotoEvent.SendMealText)
            advanceUntilIdle()
            val token = assertNotNull((awaitItem() as FoodPhotoEffect.OpenPrefilledForm).retryToken)
            assertEquals(RetryPhoto(null, FoodPhotoMode.Text, null, MEAL), retryStore.get(token))
        }
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun blankText_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        toTextInput(model)
        model.type("   ")

        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.TextInput, model.step())
        assertTrue(photos.textCalls.isEmpty())
    }

    @Test
    fun offline_sendIsBlocked_andOpenIsIgnored() = runTest(dispatcher) {
        val model = viewModel()
        toTextInput(model)
        model.type()
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        assertTrue(model.uiState.value.isOffline)
        assertEquals(FoodPhotoStep.TextInput, model.step())
        assertTrue(photos.textCalls.isEmpty())
    }

    @Test
    fun unrecognisedMeal_showsItsOwnError_andBackReturnsToTheInputWithTheText() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        val model = viewModel()
        toTextInput(model)
        model.type()

        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.NotRecognizedMeal), model.step())
        model.onEvent(FoodPhotoEvent.Back)
        assertEquals(FoodPhotoStep.TextInput, model.step())
        assertEquals(MEAL, model.uiState.value.mealText)
    }

    @Test
    fun networkAndServiceFailures_mapToTheUsualKinds() = runTest(dispatcher) {
        photos.results += Result.failure(AppException(AppError.Network))
        photos.results += Result.failure(AppException(AppError.QuotaExceeded))
        val model = viewModel()
        toTextInput(model)
        model.type()

        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()
        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.Network), model.step())
        model.onEvent(FoodPhotoEvent.Retry)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.Unavailable), model.step())
    }

    @Test
    fun retryAfterNoAnswer_resendsTheSameTextAndOpensTheDraft() = runTest(dispatcher) {
        photos.results += Result.failure(AppException(AppError.ServiceUnavailable))
        val model = viewModel()
        toTextInput(model)
        model.type()
        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodPhotoEvent.Retry)
            advanceUntilIdle()
            assertTrue(awaitItem() is FoodPhotoEffect.OpenPrefilledForm)
        }
        assertEquals(listOf(MEAL, MEAL), photos.textCalls.map { it.first })
    }

    @Test
    fun createManuallyAfterAnError_opensAnEmptyFormAndDropsTheText() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        val model = viewModel()
        toTextInput(model)
        model.type()
        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodPhotoEvent.CreateManually)
            assertEquals(FoodPhotoEffect.OpenEmptyForm, awaitItem())
        }
        assertEquals("", model.uiState.value.mealText)
    }

    @Test
    fun backAndDismiss_fromTheInput_closeAndClearTheText() = runTest(dispatcher) {
        val model = viewModel()
        toTextInput(model)
        model.type()
        model.onEvent(FoodPhotoEvent.Back)
        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals("", model.uiState.value.mealText)

        toTextInput(model)
        model.type()
        model.onEvent(FoodPhotoEvent.Dismiss)

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals("", model.uiState.value.mealText)
    }

    @Test
    fun backWhileEstimating_isIgnored() = runTest(dispatcher) {
        photos.gate = CompletableDeferred()
        val model = viewModel()
        toTextInput(model)
        model.type()
        model.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.Back)

        assertEquals(FoodPhotoStep.Estimating, model.step())
    }

    @Test
    fun recreated_onTheInput_restoresTheStepAndTheTypedText() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val first = viewModel(state)
        toTextInput(first)
        first.type()

        val restored = viewModel(state)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.TextInput, restored.step())
        assertEquals(FoodPhotoMode.Text, restored.uiState.value.mode)
        assertEquals(MEAL, restored.uiState.value.mealText)
    }

    @Test
    fun recreated_whileEstimatingOrOnAnError_comesBackOnTheInputWithTheText() = runTest(dispatcher) {
        photos.gate = CompletableDeferred()
        val state = SavedStateHandle()
        val first = viewModel(state)
        toTextInput(first)
        first.type()
        first.onEvent(FoodPhotoEvent.SendMealText)
        advanceUntilIdle()

        val restored = viewModel(state)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.TextInput, restored.step())
        assertEquals(MEAL, restored.uiState.value.mealText)
    }

    @Test
    fun photoFlow_isUnchanged_andNeverUsesTheTextEndpoint() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(FoodPhotoEvent.Open)
        model.onEvent(FoodPhotoEvent.ModeChosen(FoodPhotoMode.Dish))
        assertEquals(FoodPhotoStep.SourceSheet, model.step())
        model.onEvent(FoodPhotoEvent.SourceChosen(io.github.lopezjavier.appfitness.domain.port.PhotoSource.Camera))
        advanceUntilIdle()
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertEquals(1, photos.calls.size)
        assertTrue(photos.textCalls.isEmpty())
    }
}
