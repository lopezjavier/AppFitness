package io.github.lopezjavier.appfitness.feature.food.photo

import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.port.PhotoSource

/** Longest optional dish comment, in characters. */
const val MAX_PHOTO_COMMENT = 200

/** Why an estimate failed; each kind has its own message. No `Throwable` ever reaches the state. */
enum class FoodPhotoErrorKind {
    /** The photo of a dish did not show a recognisable food. */
    NotRecognizedDish,

    /** The label or barcode photo did not identify a product. */
    NotRecognizedProduct,

    /** No connection. */
    Network,

    /** Quota used up, service down or anything else. */
    Unavailable,
}

/** Where the "add from photo" flow stands. Only [Closed], [ModeSheet] and [SourceSheet] survive process death. */
sealed interface FoodPhotoStep {
    data object Closed : FoodPhotoStep

    data object ModeSheet : FoodPhotoStep

    data object SourceSheet : FoodPhotoStep

    /** The camera or the picker is open. */
    data class Capturing(val source: PhotoSource) : FoodPhotoStep

    /** [handle] is the compressed photo to show; read it through `PhotoCapture.readJpeg`. */
    data class Preview(val handle: String) : FoodPhotoStep

    data object Estimating : FoodPhotoStep

    data class Error(val kind: FoodPhotoErrorKind) : FoodPhotoStep
}

/**
 * State of the flow. [mode] is the chosen kind of photo (null before the mode sheet is answered); [comment] is the
 * optional dish detail (only sent in dish mode). [captureFailed] is true on the source sheet after the camera or the
 * picker failed for [lastSource]. [isOffline] disables opening the flow and "Usar foto".
 */
data class FoodPhotoUiState(
    val step: FoodPhotoStep = FoodPhotoStep.Closed,
    val mode: FoodPhotoMode? = null,
    val comment: String = "",
    val lastSource: PhotoSource? = null,
    val captureFailed: Boolean = false,
    val isOffline: Boolean = false,
)

sealed interface FoodPhotoEvent {
    /** Open the mode sheet (ignored offline). */
    data object Open : FoodPhotoEvent

    data class ModeChosen(val mode: FoodPhotoMode) : FoodPhotoEvent

    data class SourceChosen(val source: PhotoSource) : FoodPhotoEvent

    /** Discard the photo and capture again from the same source. */
    data object Retake : FoodPhotoEvent

    data class CommentChanged(val text: String) : FoodPhotoEvent

    /** Send the previewed photo to the estimate (ignored offline). */
    data object UsePhoto : FoodPhotoEvent

    /** Send the same photo again after a failure. */
    data object Retry : FoodPhotoEvent

    /** Leave the flow for an empty create-food form. */
    data object CreateManually : FoodPhotoEvent

    /** Close the flow from a sheet or the scrim: the photo and the comment are dropped. */
    data object Dismiss : FoodPhotoEvent

    /** System back: one step back (Preview to source sheet, Error to preview, a sheet closes); not while estimating. */
    data object Back : FoodPhotoEvent
}

sealed interface FoodPhotoEffect {
    /** Open the create-food form prefilled with the estimate. */
    data class OpenPrefilledForm(val draft: NewFoodDraft) : FoodPhotoEffect

    /** Open the create-food form empty ("Crear manualmente"). */
    data object OpenEmptyForm : FoodPhotoEffect
}
