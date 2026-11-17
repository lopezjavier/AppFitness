@file:Suppress("MatchingDeclarationName", "MagicNumber") // MetricGauge and its spec; clinical zone limits.

package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GaugeBar
import io.github.lopezjavier.appfitness.designsystem.GaugeZone
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind

/** The scale of a [GaugeBar]: where it starts and its zones. The zone limits mirror `InterpretMetric` (§3.8). */
internal data class MetricGaugeSpec(val min: Double, val zones: List<GaugeZone>)

private const val BMI_MIN = 15.0
private const val BMI_MAX = 40.0
private const val FAT_MIN = 3.0
private const val FAT_MAX = 45.0
private const val WHR_MIN = 0.6
private const val WHR_MAX = 1.2

/** The gauge zones of [kind]; the limits are the ones `InterpretMetric` uses for the status (male when unknown). */
@Composable
internal fun metricGaugeSpec(kind: MetricKind, sex: Sex?): MetricGaugeSpec {
    val colors = AppTheme.colors
    return when (kind) {
        MetricKind.Bmi -> MetricGaugeSpec(
            BMI_MIN,
            listOf(
                GaugeZone(18.5, colors.athleticCyan),
                GaugeZone(25.0, colors.electricGreen),
                GaugeZone(30.0, colors.energyAmber),
                GaugeZone(BMI_MAX, colors.error),
            ),
        )

        MetricKind.BodyFat -> {
            val (low, normal, high) = if (sex == Sex.Female) listOf(18.0, 28.0, 32.0) else listOf(10.0, 20.0, 25.0)
            MetricGaugeSpec(
                FAT_MIN,
                listOf(
                    GaugeZone(low, colors.energyAmber),
                    GaugeZone(normal, colors.electricGreen),
                    GaugeZone(high, colors.energyAmber),
                    GaugeZone(FAT_MAX, colors.error),
                ),
            )
        }

        MetricKind.WaistToHip -> {
            val lowRisk = if (sex == Sex.Female) 0.85 else 0.90
            MetricGaugeSpec(
                WHR_MIN,
                listOf(GaugeZone(lowRisk, colors.electricGreen), GaugeZone(WHR_MAX, colors.error)),
            )
        }
    }
}

private fun limitText(kind: MetricKind, value: Double): String = when (kind) {
    MetricKind.WaistToHip -> ratioText(value)
    else -> if (value == value.toInt().toDouble()) value.toInt().toString() else decimalText(value)
}

/** The gauge of [metric] with its marker; [showLimits] adds the zone limits under it (detail sheet). */
@Composable
internal fun MetricGauge(
    metric: MetricInterpretation,
    sex: Sex?,
    modifier: Modifier = Modifier,
    showLimits: Boolean = false,
) {
    val spec = metricGaugeSpec(metric.metric, sex)
    Column(modifier.fillMaxWidth()) {
        GaugeBar(metric.value, spec.min, spec.zones)
        if (showLimits) {
            val span = spec.zones.last().upTo - spec.min
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                var start = spec.min
                spec.zones.forEachIndexed { index, zone ->
                    val weight = ((zone.upTo - start) / span).toFloat()
                    start = zone.upTo
                    if (index < spec.zones.lastIndex) {
                        Text(
                            text = limitText(metric.metric, zone.upTo),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.weight(weight),
                        )
                    } else {
                        Spacer(Modifier.weight(weight))
                    }
                }
            }
        }
    }
}
