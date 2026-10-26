package io.github.lopezjavier.appfitness.feature.food.photo

import io.github.lopezjavier.appfitness.domain.port.PhotoSource

/**
 * State of the "estimate the amount from a photo" flow (US-54). [step] reuses [FoodPhotoStep] (no mode sheet, no
 * comment): [FoodPhotoStep.Closed], [FoodPhotoStep.SourceSheet], [FoodPhotoStep.Capturing], [FoodPhotoStep.Preview],
 * [FoodPhotoStep.Estimating] and [FoodPhotoStep.Error]. [isOffline] disables the icon and "Usar foto".
 */
data class AmountPhotoUiState(
    val step: FoodPhotoStep = FoodPhotoStep.Closed,
    val lastSource: PhotoSource? = null,
    val captureFailed: Boolean = false,
    val isOffline: Boolean = false,
) {
    /**
     * What the add-food sheet shows in place, under the amount (never in a window below the sheet): nothing,
     * "Estimando…" or "No hemos podido estimar la cantidad" with "Reintentar". The estimate itself arrives as an
     * [AmountPhotoEffect.AmountEstimated]; on a failure the amount is never touched.
     */
    val phase: AmountPhotoPhase
        get() = when (step) {
            FoodPhotoStep.Estimating -> AmountPhotoPhase.Estimating
            is FoodPhotoStep.Error -> AmountPhotoPhase.Failed(retryable = !isOffline)
            else -> AmountPhotoPhase.Idle
        }
}

/** The in-sheet state of the amount-from-photo flow, derived from [AmountPhotoUiState.step]. */
sealed interface AmountPhotoPhase {
    data object Idle : AmountPhotoPhase

    data object Estimating : AmountPhotoPhase

    /** The estimate failed (AI error, over quota, unreadable photo, network); [retryable] is false while offline. */
    data class Failed(val retryable: Boolean) : AmountPhotoPhase
}

sealed interface AmountPhotoEvent {
    /** Open the source sheet (ignored offline). [amountText] is the amount field now: the baseline of the result. */
    data class Open(val amountText: String) : AmountPhotoEvent

    data class SourceChosen(val source: PhotoSource) : AmountPhotoEvent

    /** Discard the photo and capture again from the same source. */
    data object Retake : AmountPhotoEvent

    /** Send the previewed photo (ignored offline). */
    data object UsePhoto : AmountPhotoEvent

    /** Send the same photo again after a failure. */
    data object Retry : AmountPhotoEvent

    /** Close the flow from a sheet, the scrim or the cancel button, also while estimating: the photo is dropped. */
    data object Dismiss : AmountPhotoEvent

    /** System back: Preview to the source sheet, Error to the preview; anything else (also estimating) closes. */
    data object Back : AmountPhotoEvent
}

sealed interface AmountPhotoEffect {
    /**
     * The estimate in the food's base unit. Forward it as
     * `AddFoodEvent.AmountEstimated(amount, baselineAmountText, confidence)`: [baselineAmountText] lets the sheet drop
     * the result if the user typed another amount meanwhile. [confidence] is the model's 1 (guess) to 10 (near certain)
     * score, null when the server sent none (ADR-72).
     */
    data class AmountEstimated(val amount: Double, val baselineAmountText: String, val confidence: Int? = null) :
        AmountPhotoEffect
}
