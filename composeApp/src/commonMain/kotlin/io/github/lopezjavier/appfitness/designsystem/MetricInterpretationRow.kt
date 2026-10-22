package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_tone_caution
import io.github.lopezjavier.appfitness.composeapp.resources.ic_tone_favorable
import io.github.lopezjavier.appfitness.composeapp.resources.ic_tone_unfavorable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** How a metric status reads; it is never the only cue (the chip also has an icon and the status text). */
enum class StatusTone { Good, Caution, Alert }

object StatusChipDefaults {
    val IconSize = Spacing.lg

    fun color(tone: StatusTone, appColors: AppColors): Color = when (tone) {
        StatusTone.Good -> appColors.electricGreen
        StatusTone.Caution -> appColors.energyAmber
        StatusTone.Alert -> appColors.error
    }

    fun icon(tone: StatusTone): DrawableResource = when (tone) {
        StatusTone.Good -> Res.drawable.ic_tone_favorable
        StatusTone.Caution -> Res.drawable.ic_tone_caution
        StatusTone.Alert -> Res.drawable.ic_tone_unfavorable
    }
}

/**
 * A pill with the status as text, an icon and the tone colour on the text. [mergeSemantics] false lets a clickable
 * parent (a tile) absorb the text into its own node.
 */
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier, mergeSemantics: Boolean = true) {
    val color = StatusChipDefaults.color(tone, AppTheme.colors)
    PillContainer(if (mergeSemantics) modifier.semantics(mergeDescendants = true) {} else modifier) {
        Icon(
            painter = painterResource(StatusChipDefaults.icon(tone)),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(StatusChipDefaults.IconSize),
        )
        Text(text = text, style = AppTheme.typography.bodySmall, color = color)
    }
}

/**
 * An interpreted metric: its [label] and [valueText], the status chip and a short [explanation]. Tags when
 * [testTagPrefix] is set: `<prefix>_status` and `<prefix>_explanation`.
 */
@Composable
fun MetricInterpretationRow(
    label: String,
    valueText: String,
    statusText: String,
    tone: StatusTone,
    explanation: String,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueText, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.textPrimary)
        }
        StatusChip(statusText, tone, testTagPrefix?.let { Modifier.testTag("${it}_status") } ?: Modifier)
        Text(
            text = explanation,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = testTagPrefix?.let { Modifier.testTag("${it}_explanation") } ?: Modifier,
        )
    }
}

@Preview
@Composable
private fun MetricInterpretationRowPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            MetricInterpretationRow("IMC", "23,4", "Normal", StatusTone.Good, "Tu peso es adecuado para tu estatura.")
            MetricInterpretationRow("IMC", "31,0", "Obesidad", StatusTone.Alert, "Tu peso es alto para tu estatura.")
        }
    }
}
