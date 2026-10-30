package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind

/**
 * What the metric cards show: the BMI, body-fat and waist-to-hip interpretations, null without their inputs (no
 * height, no body fat, no sex, no waist/hip). [openMetric] is the metric whose detail sheet is open.
 */
data class GoalContent(
    val currentKg: Double?,
    val strategy: GoalStrategy,
    val bmi: MetricInterpretation?,
    val bodyFat: MetricInterpretation?,
    val waistToHip: MetricInterpretation?,
    val isOnline: Boolean,
    val openMetric: MetricKind?,
)

sealed interface GoalUiState {
    data object Loading : GoalUiState

    data object Error : GoalUiState

    data class Content(val content: GoalContent) : GoalUiState
}

sealed interface GoalEvent {
    data class OpenMetric(val kind: MetricKind) : GoalEvent

    data object CloseMetric : GoalEvent

    /** Silent reload, e.g. on resume or after a weight change. */
    data object Refresh : GoalEvent

    data object Retry : GoalEvent
}
