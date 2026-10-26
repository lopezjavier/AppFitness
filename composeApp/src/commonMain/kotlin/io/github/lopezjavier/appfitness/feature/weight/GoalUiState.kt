package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.domain.usecase.TargetDistanceResult

/**
 * The target-weight sheet: [text] is the raw draft (decimal comma or point). [problem] is set only for non-blank
 * invalid text. [canSave] needs a valid value different from the saved target, connectivity and no save in flight.
 */
data class TargetSheetState(
    val text: String,
    val problem: BodyMeasurementProblem?,
    val canSave: Boolean,
    val isSaving: Boolean,
)

/**
 * What the goal card and metric tiles show. [targetKg] is null until a goal is set; [distance] needs a target and a
 * current weight; [sinceFirstKg] is current minus onboarding weight (signed; null without either). The
 * interpretations are null without their inputs (no height, no body fat, no sex, no waist/hip). [openMetric] is the
 * metric whose detail sheet is open.
 */
data class GoalContent(
    val targetKg: Double?,
    val currentKg: Double?,
    val strategy: GoalStrategy,
    val distance: TargetDistanceResult?,
    val sinceFirstKg: Double?,
    val bmi: MetricInterpretation?,
    val bodyFat: MetricInterpretation?,
    val waistToHip: MetricInterpretation?,
    val isOnline: Boolean,
    val targetSheet: TargetSheetState?,
    val openMetric: MetricKind?,
    /** The onboarding weight, the start of the goal progress; null without one. */
    val startKg: Double? = null,
    /** Share of the way from [startKg] to [targetKg] covered by [currentKg], 0..1; null without the three. */
    val progress: Double? = null,
)

sealed interface GoalUiState {
    data object Loading : GoalUiState

    data object Error : GoalUiState

    data class Content(val content: GoalContent) : GoalUiState
}

sealed interface GoalEffect {
    /** The target was saved; the UI gives one confirmation haptic and no message. */
    data object Saved : GoalEffect

    /** The save was rejected; [offline] when the cause was connectivity. The draft is kept. */
    data class SaveFailed(val offline: Boolean) : GoalEffect
}

sealed interface GoalEvent {
    /** Opens the sheet prefilled with the saved target. */
    data object OpenTargetSheet : GoalEvent

    data class EditTarget(val text: String) : GoalEvent

    data object SaveTarget : GoalEvent

    /** Closes the target sheet discarding the draft. */
    data object Dismiss : GoalEvent

    data class OpenMetric(val kind: MetricKind) : GoalEvent

    data object CloseMetric : GoalEvent

    /** Silent reload, e.g. on resume or after a weight change. */
    data object Refresh : GoalEvent

    data object Retry : GoalEvent
}
