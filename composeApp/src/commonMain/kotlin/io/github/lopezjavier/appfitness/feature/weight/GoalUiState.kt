package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.usecase.BodyMeasurementProblem
import io.github.lopezjavier.appfitness.domain.usecase.Comparison
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
    val saveError: WeightSaveError?,
)

/** An older measurement against the latest one; [comparison] is `latest - older`. */
data class ComparisonState(val older: BodyMeasurement, val latest: BodyMeasurement, val comparison: Comparison)

/**
 * What the goal card and metric tiles show. [targetKg] is null until a goal is set; [distance] needs a target and a
 * current weight; [progressPct] also needs the onboarding weight and a start different from the target. The
 * interpretations are null without their inputs (no height, no body fat, no sex, no waist/hip). [openMetric] is the
 * metric whose detail sheet is open; [comparison] the open comparison (null while loading its rows).
 */
data class GoalContent(
    val targetKg: Double?,
    val currentKg: Double?,
    val strategy: GoalStrategy,
    val distance: TargetDistanceResult?,
    val progressPct: Int?,
    val bmi: MetricInterpretation?,
    val bodyFat: MetricInterpretation?,
    val waistToHip: MetricInterpretation?,
    val isOnline: Boolean,
    val targetSheet: TargetSheetState?,
    val openMetric: MetricKind?,
    val comparison: ComparisonState?,
    val comparisonOpen: Boolean,
)

sealed interface GoalUiState {
    data object Loading : GoalUiState

    data object Error : GoalUiState

    data class Content(val content: GoalContent) : GoalUiState
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

    /** Compares measurement [olderId] against the latest one. */
    data class OpenCompare(val olderId: String) : GoalEvent

    data object CloseCompare : GoalEvent

    /** Silent reload, e.g. on resume or after a weight change. */
    data object Refresh : GoalEvent

    data object Retry : GoalEvent
}
