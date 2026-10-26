package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.toNewFoodDraft
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.PhotoQuality
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class FoodPhotoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val photos = FakeFoodPhotoRepository()
    private val capture = FakePhotoCapture()
    private val connectivity = FakeConnectivityObserver()
    private val savedState = SavedStateHandle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) =
        FoodPhotoViewModel(photos, capture, connectivity, state)

    private fun FoodPhotoViewModel.step() = uiState.value.step

    private fun TestScope.toPreview(
        model: FoodPhotoViewModel,
        mode: FoodPhotoMode = FoodPhotoMode.Dish,
        source: PhotoSource = PhotoSource.Camera,
    ) {
        advanceUntilIdle()
        model.onEvent(FoodPhotoEvent.Open)
        model.onEvent(FoodPhotoEvent.ModeChosen(mode))
        model.onEvent(FoodPhotoEvent.SourceChosen(source))
        advanceUntilIdle()
    }

    @Test
    fun open_showsTheModeSheet() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.Open)

        assertEquals(FoodPhotoStep.ModeSheet, model.step())
    }

    @Test
    fun open_isIgnoredOffline() = runTest(dispatcher) {
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.Open)

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertTrue(model.uiState.value.isOffline)
    }

    @Test
    fun modeThenSource_reachesThePreview() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        assertEquals(FoodPhotoStep.Preview("photo-1"), model.step())
    }

    @Test
    fun cameraAndGallery_passTheRightSource() = runTest(dispatcher) {
        toPreview(viewModel(SavedStateHandle()), source = PhotoSource.Gallery)
        toPreview(viewModel(SavedStateHandle()), source = PhotoSource.Camera)

        assertEquals(listOf(PhotoSource.Gallery, PhotoSource.Camera), capture.captured)
        assertEquals(listOf(PhotoQuality.Full, PhotoQuality.Full), capture.qualities)
    }

    @Test
    fun cancelled_returnsToTheSourceSheetWithoutErrorOrHandle() = runTest(dispatcher) {
        capture.results += CaptureResult.Cancelled
        val model = viewModel()
        toPreview(model)

        assertEquals(FoodPhotoStep.SourceSheet, model.step())
        assertFalse(model.uiState.value.captureFailed)
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun captureError_staysOnTheSourceSheetWithTheFlag() = runTest(dispatcher) {
        capture.results += CaptureResult.Error
        val model = viewModel()
        toPreview(model, source = PhotoSource.Gallery)

        assertEquals(FoodPhotoStep.SourceSheet, model.step())
        assertTrue(model.uiState.value.captureFailed)
        assertEquals(PhotoSource.Gallery, model.uiState.value.lastSource)
    }

    @Test
    fun dishWithComment_sendsItTrimmed() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.CommentChanged("  sin chorizo  "))

        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        val call = photos.calls.single()
        assertEquals(FoodPhotoMode.Dish, call.mode)
        assertEquals("sin chorizo", call.comment)
        assertEquals(Base64.encode(capture.bytes), call.imageBase64)
    }

    @Test
    fun dishWithBlankComment_sendsNull() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.CommentChanged("   "))

        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertNull(photos.calls.single().comment)
    }

    @Test
    fun comment_isLimitedTo200Characters() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.CommentChanged("a".repeat(201)))

        assertEquals(200, model.uiState.value.comment.length)
    }

    @Test
    fun labelAndBarcode_neverSendAComment() = runTest(dispatcher) {
        listOf(FoodPhotoMode.Label, FoodPhotoMode.Barcode).forEach { mode ->
            val model = viewModel(SavedStateHandle())
            advanceUntilIdle()
            model.onEvent(FoodPhotoEvent.CommentChanged("typed while in dish mode"))
            toPreview(model, mode)

            model.onEvent(FoodPhotoEvent.UsePhoto)
            advanceUntilIdle()

            assertNull(photos.calls.last().comment)
            assertEquals(mode, photos.calls.last().mode)
        }
    }

    @Test
    fun success_emitsThePrefilledDraft_discardsOnce_andCreatesNothing() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.effects.test {
            model.onEvent(FoodPhotoEvent.UsePhoto)
            advanceUntilIdle()
            val draft = (awaitItem() as FoodPhotoEffect.OpenPrefilledForm).draft
            assertEquals(FakeFoodPhotoRepository.lentils.toNewFoodDraft(FoodPhotoMode.Dish), draft)
            assertEquals(180.0, draft.kcal)
            assertEquals(6.0, draft.protein)
            assertEquals(15.0, draft.carbs)
            assertEquals(10.0, draft.fat)
            assertEquals(listOf(NewPortion("1 ración", 250.0)), draft.portions)
        }
        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun labelDraftWithNulls_reachesTheEffectAsIs() = runTest(dispatcher) {
        photos.result = Result.success(
            FakeFoodPhotoRepository.lentils.copy(kcal = null, protein = null, estimatedGrams = null),
        )
        val model = viewModel()
        toPreview(model, FoodPhotoMode.Label)

        model.effects.test {
            model.onEvent(FoodPhotoEvent.UsePhoto)
            advanceUntilIdle()
            val draft = (awaitItem() as FoodPhotoEffect.OpenPrefilledForm).draft
            assertNull(draft.kcal)
            assertNull(draft.protein)
            assertTrue(draft.portions.isEmpty())
        }
    }

    @Test
    fun estimating_blocksDismissAndBack() = runTest(dispatcher) {
        photos.gate = CompletableDeferred()
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.Back)
        model.onEvent(FoodPhotoEvent.Dismiss)

        assertEquals(FoodPhotoStep.Estimating, model.step())
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun failureKinds_mapByCauseAndMode() = runTest(dispatcher) {
        val cases = listOf(
            Triple(AppError.PhotoNotRecognized, FoodPhotoMode.Dish, FoodPhotoErrorKind.NotRecognizedDish),
            Triple(AppError.PhotoNotRecognized, FoodPhotoMode.Label, FoodPhotoErrorKind.NotRecognizedProduct),
            Triple(AppError.PhotoNotRecognized, FoodPhotoMode.Barcode, FoodPhotoErrorKind.NotRecognizedProduct),
            Triple(AppError.Network, FoodPhotoMode.Dish, FoodPhotoErrorKind.Network),
            Triple(AppError.QuotaExceeded, FoodPhotoMode.Dish, FoodPhotoErrorKind.Unavailable),
            Triple(AppError.ServiceUnavailable, FoodPhotoMode.Label, FoodPhotoErrorKind.Unavailable),
            Triple(AppError.Unknown, FoodPhotoMode.Barcode, FoodPhotoErrorKind.Unavailable),
        )
        cases.forEach { (error, mode, kind) ->
            photos.result = Result.failure(AppException(error))
            val model = viewModel(SavedStateHandle())
            toPreview(model, mode)

            model.onEvent(FoodPhotoEvent.UsePhoto)
            advanceUntilIdle()

            assertEquals(FoodPhotoStep.Error(kind), model.step(), "$error in $mode")
        }
    }

    @Test
    fun foreignThrowable_isUnavailable() = runTest(dispatcher) {
        photos.result = Result.failure(IllegalStateException("secret provider body"))
        val model = viewModel()
        toPreview(model)

        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Error(FoodPhotoErrorKind.Unavailable), model.step())
    }

    @Test
    fun retry_resendsTheSameImageWithoutCapturingAgain() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        photos.result = Result.success(FakeFoodPhotoRepository.lentils)
        model.onEvent(FoodPhotoEvent.Retry)
        advanceUntilIdle()

        assertEquals(listOf("photo-1", "photo-1"), capture.reads)
        assertEquals(1, capture.captured.size)
        assertEquals(2, photos.calls.size)
        assertEquals(FoodPhotoStep.Closed, model.step())
    }

    @Test
    fun success_clearsTheDishComment() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.CommentChanged("sin chorizo"))

        model.effects.test {
            model.onEvent(FoodPhotoEvent.UsePhoto)
            advanceUntilIdle()
            awaitItem()
        }

        assertEquals("sin chorizo", photos.calls.single().comment)
        assertEquals("", model.uiState.value.comment)
        assertEquals("", savedState.get<String>("food_photo_comment").orEmpty())
    }

    @Test
    fun createManually_clearsTheDishComment() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.CommentChanged("sin chorizo"))
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodPhotoEvent.CreateManually)
            awaitItem()
        }

        assertEquals("", model.uiState.value.comment)
        assertEquals("", savedState.get<String>("food_photo_comment").orEmpty())
    }

    @Test
    fun createManually_opensTheEmptyForm_discardsOnce() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(FoodPhotoEvent.CreateManually)
            assertEquals(FoodPhotoEffect.OpenEmptyForm, awaitItem())
        }
        assertEquals(listOf("photo-1"), capture.discarded)
        assertEquals(FoodPhotoStep.Closed, model.step())
    }

    @Test
    fun backFromError_returnsToThePreviewKeepingThePhoto() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.Back)

        assertEquals(FoodPhotoStep.Preview("photo-1"), model.step())
        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun retake_discardsAndCapturesAgainFromTheSameSource() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model, source = PhotoSource.Gallery)

        model.onEvent(FoodPhotoEvent.Retake)
        advanceUntilIdle()

        assertEquals(listOf("photo-1"), capture.discarded)
        assertEquals(listOf(PhotoSource.Gallery, PhotoSource.Gallery), capture.captured)
        assertEquals(FoodPhotoStep.Preview("photo-2"), model.step())
    }

    @Test
    fun backFromPreview_discardsAndShowsTheSourceSheet() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.onEvent(FoodPhotoEvent.Back)

        assertEquals(FoodPhotoStep.SourceSheet, model.step())
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun dismiss_discardsThePhotoOnceAndClearsTheComment() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.CommentChanged("algo"))

        model.onEvent(FoodPhotoEvent.Dismiss)
        model.onEvent(FoodPhotoEvent.Dismiss)

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals("", model.uiState.value.comment)
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun release_discardsThePhotoOnce() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        model.release()
        model.release()

        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun captureStart_sweeps() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)

        assertEquals(1, capture.sweeps)
    }

    @Test
    fun resultArrivingAfterDismiss_isIgnoredAndDiscarded() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        capture.gate = gate
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(FoodPhotoEvent.Open)
        model.onEvent(FoodPhotoEvent.ModeChosen(FoodPhotoMode.Dish))
        model.onEvent(FoodPhotoEvent.SourceChosen(PhotoSource.Camera))
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.Dismiss)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.Closed, model.step())
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun offline_blocksUsePhoto() = runTest(dispatcher) {
        val model = viewModel()
        toPreview(model)
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertTrue(photos.calls.isEmpty())
        assertEquals(FoodPhotoStep.Preview("photo-1"), model.step())
    }

    @Test
    fun theFlow_neverTouchesTheFoodRepositoryOrBarcodeLookup() = runTest(dispatcher) {
        val foods = FakeFoodRepository()
        val model = viewModel()
        toPreview(model, FoodPhotoMode.Barcode)
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertTrue(foods.created.isEmpty())
        assertTrue(foods.barcodeLookups.isEmpty())
    }

    // Restoration (ADR-40): only the sheets, the mode and the comment survive process death.

    @Test
    fun restoredFromEveryLaterStep_landsOnTheSourceSheetWithModeAndComment() = runTest(dispatcher) {
        val steps = listOf<(FoodPhotoViewModel, SavedStateHandle) -> Unit>(
            { m, _ -> m.onEvent(FoodPhotoEvent.SourceChosen(PhotoSource.Camera)) },
            { m, _ ->
                m.onEvent(FoodPhotoEvent.SourceChosen(PhotoSource.Camera))
                advanceUntilIdleBlocking()
            },
            { m, _ ->
                m.onEvent(FoodPhotoEvent.SourceChosen(PhotoSource.Camera))
                advanceUntilIdleBlocking()
                photos.gate = CompletableDeferred()
                m.onEvent(FoodPhotoEvent.UsePhoto)
                advanceUntilIdleBlocking()
            },
        )
        steps.forEach { enter ->
            val state = SavedStateHandle()
            val model = viewModel(state)
            advanceUntilIdle()
            model.onEvent(FoodPhotoEvent.Open)
            model.onEvent(FoodPhotoEvent.ModeChosen(FoodPhotoMode.Label))
            model.onEvent(FoodPhotoEvent.CommentChanged("sin sal"))
            enter(model, state)

            val restored = viewModel(state)

            assertEquals(FoodPhotoStep.SourceSheet, restored.step())
            assertEquals(FoodPhotoMode.Label, restored.uiState.value.mode)
            assertEquals("sin sal", restored.uiState.value.comment)
        }
    }

    private fun advanceUntilIdleBlocking() = dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun restoredFromError_landsOnTheSourceSheet() = runTest(dispatcher) {
        photos.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        toPreview(model)
        model.onEvent(FoodPhotoEvent.UsePhoto)
        advanceUntilIdle()

        assertEquals(FoodPhotoStep.SourceSheet, viewModel(savedState).step())
    }

    @Test
    fun restoredFromModeSheet_staysOnTheModeSheet() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(FoodPhotoEvent.Open)

        assertEquals(FoodPhotoStep.ModeSheet, viewModel(savedState).step())
    }

    @Test
    fun restoredStart_sweeps_freshStartDoesNot() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(0, capture.sweeps)
        model.onEvent(FoodPhotoEvent.Open)

        viewModel(savedState)

        assertEquals(1, capture.sweeps)
    }
}
