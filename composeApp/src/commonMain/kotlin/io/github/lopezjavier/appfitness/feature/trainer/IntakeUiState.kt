package io.github.lopezjavier.appfitness.feature.trainer

/** Ranges that mirror the server validation of `save_coach_intake`. */
object IntakeLimits {
    const val MAX_FREE_TEXT = 500
    const val MIN_YEARS = 0.0
    const val MAX_YEARS = 60.0
    const val YEARS_STEP = 0.5
    const val MIN_KCAL = 1200
    const val MAX_KCAL = 6000
}

/** An inline message under the action that failed (R-31); the draft stays as it is. */
enum class IntakeNotice { SaveFailed, SaveOffline }

/** "Para conocerte" free-text editor (US-81): one multi-line field, nothing else. */
sealed interface IntakeUiState {
    data object Loading : IntakeUiState

    /** [baseline] is the stored text; [dirty] is true when [text] differs from it. */
    data class Content(
        val text: String,
        val baseline: String,
        val saving: Boolean = false,
        val notice: IntakeNotice? = null,
        val discardDialog: Boolean = false,
    ) : IntakeUiState {
        val dirty: Boolean get() = text != baseline
        val remainingChars: Int get() = IntakeLimits.MAX_FREE_TEXT - text.length
        val canSave: Boolean get() = dirty && !saving
    }

    data object Offline : IntakeUiState

    data object Error : IntakeUiState
}

sealed interface IntakeEvent {
    data object Retry : IntakeEvent

    /** Text beyond 500 characters is cut. */
    data class EditFreeText(val text: String) : IntakeEvent

    data object Save : IntakeEvent

    data object Back : IntakeEvent

    data object ConfirmDiscard : IntakeEvent

    data object DismissDiscard : IntakeEvent
}

sealed interface IntakeEffect {
    data object Saved : IntakeEffect

    data object Close : IntakeEffect
}
