package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.PhotoQuality
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AmountPhotoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeFoodPhotoRepository()
    private val foods = FakeFoodRepository()
    private val capture = FakePhotoCapture()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 165.0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        foods.known[chicken.id] = chicken
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) =
        AmountPhotoViewModel(chicken.id, foods, photos, capture, connectivity, state)

    private fun AmountPhotoViewModel.step() = uiState.value.step

    private fun TestScope.toPreview(model: AmountPhotoViewModel, amountText: String = "100") {
        advanceUntilIdle()
        model.onEvent(AmountPhotoEvent.Open(amountText))
        model.onEvent(AmountPhotoEvent.SourceChosen(PhotoSource.Camera))
        advanceUntilIdle()
    }

    @Test
    fun open_skipsTheModeSheetAndShowsTheSourceSheet() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(AmountPhotoEvent.Open("100"))

        assertEquals(FoodPhotoStep.SourceSheet, model.step())
    }

    @Test
    fun sourceChosen_capturesWithTheCompactQualityForTheAmountEstimate() = runTest(dispatcher) {
        toPreview(viewModel())

        assertEquals(listOf(PhotoQuality.Compact), capture.qualities)
    }

    @Test
    fun open_isIgnoredOffline() = runTest(dispatcher) {
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(AmountPhotoEvent.Open("100"))

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertTrue(model.uiState.value.isOffline)
    }

    @Test
    fun sourceChosen_reachesThePreview() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        assertEquals(FoodPhotoStep.Preview("photo-1"), model.step())
    }

    @Test
    fun usePhoto_estimatesWithTheFoodAndEmitsTheAmountWithTheBaseline() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model, amountText = "100")

        model.effects.test {
            model.onEvent(AmountPhotoEvent.UsePhoto)
            assertEquals(FoodPhotoStep.Estimating, model.step())
            advanceUntilIdle()

            assertEquals(AmountPhotoEffect.AmountEstimated(180.0, "100"), awaitItem())
        }
        assertEquals(chicken, photos.amountCalls.single().first)
        assertEquals(FoodPhotoStep.Closed, model.step())
    }

    @Test
    fun usePhoto_carriesTheConfidenceOfTheEstimateInTheEffect() = runTest(dispatcher) {
        photos.amountResult = Result.success(AmountEstimate(180.0, "g", 7))
        val model = viewModel()
        toPreview(model, amountText = "100")

        model.effects.test {
            model.onEvent(AmountPhotoEvent.UsePhoto)
            advanceUntilIdle()

            assertEquals(AmountPhotoEffect.AmountEstimated(180.0, "100", confidence = 7), awaitItem())
        }
    }

    @Test
    fun whileTheEstimateIsStillLoading_thePhaseIsEstimating_andNothingIsEmitted() = runTest(dispatcher) {
        photos.amountGate = CompletableDeferred()
        val model = viewModel()
        toPreview(model)

        model.effects.test {
            model.onEvent(AmountPhotoEvent.UsePhoto)
            advanceUntilIdle()

            assertEquals(AmountPhotoPhase.Estimating, model.uiState.value.phase)
            expectNoEvents()
            photos.amountGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(AmountPhotoEffect.AmountEstimated(180.0, "100"), awaitItem())
        }
        assertEquals(AmountPhotoPhase.Idle, model.uiState.value.phase)
    }

    @Test
    fun overQuotaAiFailureAndUnreadablePhoto_eachFailInPlaceWithRetryAndNeverEmitAnAmount() = runTest(dispatcher) {
        listOf(AppError.QuotaExceeded, AppError.ServiceUnavailable, AppError.PhotoNotRecognized).forEach { error ->
            photos.amountResult = Result.failure(AppException(error))
            val model = viewModel(SavedStateHandle())
            toPreview(model)

            model.effects.test {
                model.onEvent(AmountPhotoEvent.UsePhoto)
                advanceUntilIdle()

                assertEquals(AmountPhotoPhase.Failed(retryable = true), model.uiState.value.phase, "$error")
                expectNoEvents()
            }
        }
    }

    @Test
    fun aFailedEstimate_isNotRetryableOffline() = runTest(dispatcher) {
        photos.amountResult = Result.failure(AppException(AppError.ServiceUnavailable))
        val model = viewModel()
        toPreview(model)
        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()

        connectivity.state.value = false
        advanceUntilIdle()

        assertEquals(AmountPhotoPhase.Failed(retryable = false), model.uiState.value.phase)
    }

    @Test
    fun success_discardsThePhotoFile() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun failure_keepsThePhotoOffersRetryAndEmitsNothing() = runTest(dispatcher) {
        photos.amountResult = Result.failure(AppException(AppError.ServiceUnavailable))
        val model = viewModel()
        toPreview(model)

        model.effects.test {
            model.onEvent(AmountPhotoEvent.UsePhoto)
            advanceUntilIdle()

            assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.Unavailable), model.step())
            expectNoEvents()
        }
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun retry_sendsTheSamePhotoAgainAndSucceeds() = runTest(dispatcher) {
        photos.amountResult = Result.failure(AppException(AppError.QuotaExceeded))
        val model = viewModel()
        toPreview(model)
        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()
        photos.amountResult = Result.success(AmountEstimate(150.0, "g", null))

        model.effects.test {
            model.onEvent(AmountPhotoEvent.Retry)
            advanceUntilIdle()

            assertEquals(AmountPhotoEffect.AmountEstimated(150.0, "100"), awaitItem())
        }
        assertEquals(2, photos.amountCalls.size)
        assertEquals(photos.amountCalls[0].second, photos.amountCalls[1].second)
    }

    @Test
    fun notRecognizedAndNetworkFailures_mapToTheirKinds() = runTest(dispatcher) {
        photos.amountResult = Result.failure(AppException(AppError.PhotoNotRecognized))
        val model = viewModel()
        toPreview(model)
        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()
        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.NotRecognizedProduct), model.step())

        photos.amountResult = Result.failure(AppException(AppError.Network))
        model.onEvent(AmountPhotoEvent.Retry)
        advanceUntilIdle()
        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.Network), model.step())
    }

    @Test
    fun unknownFood_failsWithoutCallingTheEstimate() = runTest(dispatcher) {
        foods.known.clear()
        val model = viewModel()
        toPreview(model)

        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.Unavailable), model.step())
        assertTrue(photos.amountCalls.isEmpty())
    }

    @Test
    fun usePhoto_isIgnoredOffline() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Preview("photo-1"), model.step())
        assertTrue(photos.amountCalls.isEmpty())
    }

    @Test
    fun dismissWhileEstimating_cancelsDiscardsThePhotoAndIgnoresTheLateResult() = runTest(dispatcher) {
        photos.amountGate = CompletableDeferred()
        val model = viewModel()
        toPreview(model)

        model.effects.test {
            model.onEvent(AmountPhotoEvent.UsePhoto)
            advanceUntilIdle()
            model.onEvent(AmountPhotoEvent.Dismiss)
            assertEquals(FoodPhotoStep.Closed, model.step())
            photos.amountGate?.complete(Unit)
            advanceUntilIdle()

            expectNoEvents()
        }
        assertEquals(listOf("photo-1"), capture.discarded)
        assertEquals(FoodPhotoStep.Closed, model.step())
    }

    @Test
    fun backWhileEstimating_cancelsToo() = runTest(dispatcher) {
        photos.amountGate = CompletableDeferred()
        val model = viewModel()
        toPreview(model)
        model.onEvent(AmountPhotoEvent.UsePhoto)
        advanceUntilIdle()

        model.onEvent(AmountPhotoEvent.Back)

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun dismissOnThePreview_discardsThePhoto() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.onEvent(AmountPhotoEvent.Dismiss)

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun retake_discardsTheOldPhotoAndCapturesAgain() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.onEvent(AmountPhotoEvent.Retake)
        advanceUntilIdle()

        assertEquals(listOf("photo-1"), capture.discarded)
        assertEquals(FoodPhotoStep.Preview("photo-2"), model.step())
    }

    @Test
    fun staleCapture_afterDismissIsDiscarded() = runTest(dispatcher) {
        capture.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(AmountPhotoEvent.Open("100"))
        model.onEvent(AmountPhotoEvent.SourceChosen(PhotoSource.Gallery))
        advanceUntilIdle()

        model.onEvent(AmountPhotoEvent.Dismiss)
        capture.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun cancelledOrFailedCapture_returnsToTheSourceSheet() = runTest(dispatcher) {
        capture.results += CaptureResult.Cancelled
        capture.results += CaptureResult.Error
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(AmountPhotoEvent.Open("100"))

        model.onEvent(AmountPhotoEvent.SourceChosen(PhotoSource.Camera))
        advanceUntilIdle()
        assertEquals(FoodPhotoStep.SourceSheet, model.step())
        assertFalse(model.uiState.value.captureFailed)

        model.onEvent(AmountPhotoEvent.SourceChosen(PhotoSource.Camera))
        advanceUntilIdle()
        assertEquals(FoodPhotoStep.SourceSheet, model.step())
        assertTrue(model.uiState.value.captureFailed)
    }

    @Test
    fun recreatedFromTheSavedState_comesBackOnTheSourceSheetWithTheBaseline() = runTest(dispatcher) {
        val first = viewModel()
        toPreview(first, amountText = "120")

        val restored = viewModel(savedState)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.SourceSheet, restored.step())
        assertTrue(capture.sweeps > 0)
        restored.onEvent(AmountPhotoEvent.SourceChosen(PhotoSource.Camera))
        advanceUntilIdle()
        restored.effects.test {
            restored.onEvent(AmountPhotoEvent.UsePhoto)
            advanceUntilIdle()

            assertEquals(AmountPhotoEffect.AmountEstimated(180.0, "120"), awaitItem())
        }
    }

    @Test
    fun cleared_releasesThePhotoFile() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.release()

        assertEquals(listOf("photo-1"), capture.discarded)
    }
}
