package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.domain.port.PhotoQuality
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import io.github.lopezjavier.appfitness.domain.repository.FoodRepository
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

private const val KEY_STEP = "amount_photo_step"
private const val KEY_BASELINE = "amount_photo_baseline"

/**
 * "Estimate the amount from a photo" for the food [foodId] of the add-food sheet (US-54, ADR-64): source sheet,
 * preview, estimate and failure, ending in an [AmountPhotoEffect.AmountEstimated] effect. Nothing is created or stored.
 *
 * Like [FoodPhotoViewModel], the photo and the in-flight request live only in memory; after process death
 * ([savedState]) the flow comes back on the source sheet. Unlike it, cancelling is allowed while estimating: the photo
 * is discarded and a late response is ignored. The food is read from [foodRepository] when the photo is sent and goes
 * to the estimate as data. This class never logs or stores the photo, the food details or an exception.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one function per step.
class AmountPhotoViewModel(
    private val foodId: String,
    private val foodRepository: FoodRepository,
    private val photoRepository: FoodPhotoRepository,
    private val photoCapture: PhotoCapture,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(restore())
    val uiState: StateFlow<AmountPhotoUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<AmountPhotoEffect>(Channel.BUFFERED)
    val effects: Flow<AmountPhotoEffect> = effectChannel.receiveAsFlow()

    /** The photo being previewed or sent; null when there is none. Memory only. */
    private var handle: String? = null

    /** Identifies the capture that may still deliver a result; a stale result is discarded. */
    private var captureId = 0

    /** Identifies the request whose result is still wanted; a cancelled one is ignored. */
    private var requestId = 0

    init {
        if (savedState.contains(KEY_STEP)) photoCapture.sweep()
        viewModelScope.launch {
            connectivity.isOnline.collect { online -> _uiState.update { it.copy(isOffline = !online) } }
        }
    }

    fun onEvent(event: AmountPhotoEvent) {
        when (event) {
            is AmountPhotoEvent.Open -> open(event.amountText)
            is AmountPhotoEvent.SourceChosen -> if (step() == FoodPhotoStep.SourceSheet) capture(event.source)
            AmountPhotoEvent.Retake -> retake()
            AmountPhotoEvent.UsePhoto -> if (step() is FoodPhotoStep.Preview) send()
            AmountPhotoEvent.Retry -> if (step() is FoodPhotoStep.Error) send()
            AmountPhotoEvent.Dismiss -> close()
            AmountPhotoEvent.Back -> back()
        }
    }

    private fun open(amountText: String) {
        if (_uiState.value.isOffline || step() != FoodPhotoStep.Closed) return
        savedState[KEY_BASELINE] = amountText
        setStep(FoodPhotoStep.SourceSheet)
    }

    private fun step() = _uiState.value.step

    private fun setStep(step: FoodPhotoStep) {
        _uiState.update { it.copy(step = step) }
        // The photo does not survive process death: every step after the source sheet comes back as the source sheet.
        savedState[KEY_STEP] = if (step == FoodPhotoStep.Closed) "Closed" else "SourceSheet"
    }

    private fun capture(source: PhotoSource) {
        val id = ++captureId
        _uiState.update { it.copy(lastSource = source, captureFailed = false) }
        setStep(FoodPhotoStep.Capturing(source))
        viewModelScope.launch {
            photoCapture.sweep()
            val result = photoCapture.capture(source, PhotoQuality.Compact)
            if (id != captureId || step() != FoodPhotoStep.Capturing(source)) {
                // Late result: the flow was closed meanwhile.
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
        setStep(FoodPhotoStep.SourceSheet)
        capture(source)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun send() {
        val photo = handle
        if (photo == null || _uiState.value.isOffline) return
        val baseline = savedState.get<String>(KEY_BASELINE).orEmpty()
        val id = ++requestId
        setStep(FoodPhotoStep.Estimating)
        viewModelScope.launch {
            val outcome = try {
                val food = foodRepository.getById(foodId).getOrThrow()
                val encoded = Base64.encode(photoCapture.readJpeg(photo))
                photoRepository.estimateAmount(food, encoded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The reason is deliberately dropped: nothing about the photo or the response is kept.
                Result.failure(IllegalStateException("estimate failed"))
            }
            if (id != requestId || step() != FoodPhotoStep.Estimating) return@launch
            outcome.fold(
                onSuccess = {
                    dropPhoto()
                    setStep(FoodPhotoStep.Closed)
                    effectChannel.send(AmountPhotoEffect.AmountEstimated(it.amount, baseline, it.confidence))
                },
                onFailure = { setStep(FoodPhotoStep.Error(kindOf((it as? AppException)?.error))) },
            )
        }
    }

    private fun kindOf(error: AppError?): FoodPhotoErrorKind = when (error) {
        AppError.PhotoNotRecognized -> FoodPhotoErrorKind.NotRecognizedProduct
        AppError.Network -> FoodPhotoErrorKind.Network
        else -> FoodPhotoErrorKind.Unavailable
    }

    private fun back() {
        when (step()) {
            is FoodPhotoStep.Preview -> {
                dropPhoto()
                setStep(FoodPhotoStep.SourceSheet)
            }

            is FoodPhotoStep.Error -> setStep(FoodPhotoStep.Preview(handle ?: return))

            else -> close()
        }
    }

    /** Leaves the flow, also while estimating: the photo is dropped and any pending result is ignored. */
    private fun close() {
        captureId++
        requestId++
        dropPhoto()
        _uiState.update { it.copy(captureFailed = false) }
        setStep(FoodPhotoStep.Closed)
    }

    private fun dropPhoto() {
        handle?.let(photoCapture::discard)
        handle = null
    }

    /** Releases the photo file; called when the ViewModel is cleared. */
    internal fun release() {
        captureId++
        requestId++
        dropPhoto()
    }

    override fun onCleared() = release()

    private fun restore(): AmountPhotoUiState {
        val step = if (savedState.get<String>(KEY_STEP) == "SourceSheet") {
            FoodPhotoStep.SourceSheet
        } else {
            FoodPhotoStep.Closed
        }
        return AmountPhotoUiState(step = step)
    }
}
