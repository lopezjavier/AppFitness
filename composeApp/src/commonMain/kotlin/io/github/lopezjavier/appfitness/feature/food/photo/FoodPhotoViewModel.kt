package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.toNewFoodDraft
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val KEY_STEP = "food_photo_step"
private const val KEY_MODE = "food_photo_mode"
private const val KEY_COMMENT = "food_photo_comment"

/**
 * "Add a food from a photo" (US-48, US-49): mode sheet, source sheet, preview, estimate and failure, ending in an
 * effect that opens the create-food form (prefilled, or empty after "Crear manualmente"). Nothing is created here:
 * the user confirms the form, which reuses the normal create path.
 *
 * The photo (handle, bytes) and the in-flight request live only in memory. After process death ([savedState]) the
 * flow comes back on the source sheet of the same mode with the typed comment, never on a preview without a photo;
 * a capture result is accepted only while the flow is [FoodPhotoStep.Capturing] in this process. The photo file is
 * discarded on every way out. This class never logs, prints or stores an exception, the comment or the response:
 * a failure is reduced to a [FoodPhotoErrorKind].
 */
@Suppress("TooManyFunctions") // One function per step of the flow.
class FoodPhotoViewModel(
    private val photoRepository: FoodPhotoRepository,
    private val photoCapture: PhotoCapture,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(restore())
    val uiState: StateFlow<FoodPhotoUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<FoodPhotoEffect>(Channel.BUFFERED)
    val effects: Flow<FoodPhotoEffect> = effectChannel.receiveAsFlow()

    /** The photo being previewed or sent; null when there is none. Memory only. */
    private var handle: String? = null

    /** Identifies the capture that may still deliver a result; a stale result is discarded. */
    private var captureId = 0

    init {
        if (savedState.contains(KEY_STEP)) photoCapture.sweep()
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _uiState.update { it.copy(isOffline = !online) } }
        }
    }

    fun onEvent(event: FoodPhotoEvent) {
        when (event) {
            FoodPhotoEvent.Open -> open()
            is FoodPhotoEvent.ModeChosen -> chooseMode(event.mode)
            is FoodPhotoEvent.SourceChosen -> chooseSource(event.source)
            FoodPhotoEvent.Retake -> retake()
            is FoodPhotoEvent.CommentChanged -> changeComment(event.text)
            FoodPhotoEvent.UsePhoto -> if (step() is FoodPhotoStep.Preview) send()
            FoodPhotoEvent.Retry -> if (step() is FoodPhotoStep.Error) send()
            FoodPhotoEvent.CreateManually -> createManually()
            FoodPhotoEvent.Dismiss -> dismiss()
            FoodPhotoEvent.Back -> back()
        }
    }

    private fun open() {
        if (!_uiState.value.isOffline && step() == FoodPhotoStep.Closed) setStep(FoodPhotoStep.ModeSheet)
    }

    private fun chooseMode(mode: FoodPhotoMode) {
        if (step() != FoodPhotoStep.ModeSheet) return
        _uiState.update { it.copy(mode = mode, captureFailed = false) }
        setStep(FoodPhotoStep.SourceSheet)
    }

    private fun changeComment(text: String) {
        val limited = text.take(MAX_PHOTO_COMMENT)
        savedState[KEY_COMMENT] = limited
        _uiState.update { it.copy(comment = limited) }
    }

    private fun step() = _uiState.value.step

    private fun setStep(step: FoodPhotoStep) {
        _uiState.update { it.copy(step = step) }
        savedState[KEY_STEP] = when (step) {
            FoodPhotoStep.Closed -> "Closed"

            FoodPhotoStep.ModeSheet -> "ModeSheet"

            // The photo does not survive process death: every later step comes back as the source sheet.
            else -> "SourceSheet"
        }
        savedState[KEY_MODE] = _uiState.value.mode?.name
    }

    private fun chooseSource(source: PhotoSource) {
        if (step() == FoodPhotoStep.SourceSheet) capture(source)
    }

    private fun dismiss() {
        if (step() != FoodPhotoStep.Estimating) close()
    }

    private fun capture(source: PhotoSource) {
        val id = ++captureId
        _uiState.update { it.copy(lastSource = source, captureFailed = false) }
        setStep(FoodPhotoStep.Capturing(source))
        viewModelScope.launch {
            photoCapture.sweep()
            val result = photoCapture.capture(source)
            if (id != captureId || step() != FoodPhotoStep.Capturing(source)) {
                // Late result: the flow moved on (dismissed, or a restored process).
                if (result is CaptureResult.Photo) photoCapture.discard(result.handle)
                return@launch
            }
            when (result) {
                is CaptureResult.Photo -> {
                    handle = result.handle
                    setStep(FoodPhotoStep.Preview(result.handle))
                }

                CaptureResult.Cancelled -> setStep(FoodPhotoStep.SourceSheet)

                CaptureResult.Error -> {
                    _uiState.update { it.copy(captureFailed = true) }
                    setStep(FoodPhotoStep.SourceSheet)
                }
            }
        }
    }

    private fun retake() {
        val source = _uiState.value.lastSource
        if (step() !is FoodPhotoStep.Preview || source == null) return
        dropPhoto()
        // Back to the source sheet first so the capture starts from a state that accepts it.
        setStep(FoodPhotoStep.SourceSheet)
        capture(source)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun send() {
        val state = _uiState.value
        val photo = handle
        val mode = state.mode
        if (photo == null || mode == null || state.isOffline) return
        val comment = state.comment.trim().takeIf { mode == FoodPhotoMode.Dish && it.isNotEmpty() }
        setStep(FoodPhotoStep.Estimating)
        viewModelScope.launch {
            val outcome = try {
                val encoded = Base64.encode(photoCapture.readJpeg(photo))
                photoRepository.estimate(mode, encoded, comment)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The reason is deliberately dropped: nothing about the photo or the response is kept.
                Result.failure(IllegalStateException("estimate failed"))
            }
            if (handle != photo || step() != FoodPhotoStep.Estimating) return@launch
            outcome.fold(
                onSuccess = {
                    dropPhoto()
                    setStep(FoodPhotoStep.Closed)
                    effectChannel.send(FoodPhotoEffect.OpenPrefilledForm(it.toNewFoodDraft(mode)))
                },
                onFailure = { setStep(FoodPhotoStep.Error(kindOf(mode, (it as? AppException)?.error))) },
            )
        }
    }

    private fun kindOf(mode: FoodPhotoMode, error: AppError?): FoodPhotoErrorKind = when (error) {
        AppError.PhotoNotRecognized ->
            if (mode == FoodPhotoMode.Dish) {
                FoodPhotoErrorKind.NotRecognizedDish
            } else {
                FoodPhotoErrorKind.NotRecognizedProduct
            }

        AppError.Network -> FoodPhotoErrorKind.Network

        else -> FoodPhotoErrorKind.Unavailable
    }

    private fun createManually() {
        if (step() !is FoodPhotoStep.Error) return
        dropPhoto()
        setStep(FoodPhotoStep.Closed)
        effectChannel.trySend(FoodPhotoEffect.OpenEmptyForm)
    }

    private fun back() {
        when (step()) {
            is FoodPhotoStep.Preview -> {
                dropPhoto()
                setStep(FoodPhotoStep.SourceSheet)
            }

            is FoodPhotoStep.Error -> setStep(FoodPhotoStep.Preview(handle ?: return))

            FoodPhotoStep.Estimating -> Unit

            else -> close()
        }
    }

    /** Leaves the flow: the photo and the comment are dropped. */
    private fun close() {
        captureId++
        dropPhoto()
        savedState[KEY_COMMENT] = ""
        _uiState.update { it.copy(comment = "", captureFailed = false) }
        setStep(FoodPhotoStep.Closed)
    }

    private fun dropPhoto() {
        handle?.let(photoCapture::discard)
        handle = null
    }

    /** Releases the photo file; called when the ViewModel is cleared. */
    internal fun release() {
        captureId++
        dropPhoto()
    }

    override fun onCleared() = release()

    private fun restore(): FoodPhotoUiState {
        val step = when (savedState.get<String>(KEY_STEP)) {
            "ModeSheet" -> FoodPhotoStep.ModeSheet
            "SourceSheet" -> FoodPhotoStep.SourceSheet
            else -> FoodPhotoStep.Closed
        }
        val mode = savedState.get<String>(KEY_MODE)?.let { name ->
            FoodPhotoMode.entries.firstOrNull { it.name == name }
        }
        // A source sheet without a mode makes no sense: start over from the mode sheet.
        val safeStep = if (step == FoodPhotoStep.SourceSheet && mode == null) FoodPhotoStep.ModeSheet else step
        return FoodPhotoUiState(step = safeStep, mode = mode, comment = savedState.get<String>(KEY_COMMENT).orEmpty())
    }
}
