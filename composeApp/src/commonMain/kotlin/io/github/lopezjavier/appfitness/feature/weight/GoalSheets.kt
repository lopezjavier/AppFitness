package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.MetricInterpretationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import org.jetbrains.compose.resources.stringResource

/**
 * Read-only detail of a metric (info archetype): value, status, and explanation.
 * The header X closes it; it has no effect on any data.
 */
@Composable
internal fun MetricDetailSheet(metric: MetricInterpretation, onEvent: (GoalEvent) -> Unit) {
    AppInfoSheet(
        title = stringResource(metricLabelRes(metric.metric)),
        onDismiss = { onEvent(GoalEvent.CloseMetric) },
        testTagPrefix = "weight_metric_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            MetricInterpretationRow(
                label = stringResource(metricLabelRes(metric.metric)),
                valueText = metricValueText(metric),
                statusText = stringResource(statusTextRes(metric.status)),
                tone = metric.status.tone.toStatusTone(),
                explanation = stringResource(explanationRes(metric.status)),
                testTagPrefix = "weight_metric",
            )
        }
    }
}
