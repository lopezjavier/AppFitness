package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_legend
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_ma_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_ma_help_text
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_ma_help_text_metric
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_title_cd
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The metric name with a chevron (opens the picker) and the "?" help of the moving average. */
@Composable
internal fun ChartTitleRow(metric: BodyMetric, canChange: Boolean, onOpenPicker: () -> Unit) {
    val name = metricName(metric)
    val description = stringResource(Res.string.weight_chart_metric_title_cd, name)
    val titleModifier = if (canChange) {
        Modifier
            .clickable(role = Role.Button, onClick = onOpenPicker)
            .semantics { contentDescription = description }
    } else {
        Modifier.semantics { heading() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Spacing.minTouchTarget)
                .then(titleModifier)
                .testTag("weight_chart_metric_title"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            if (canChange) {
                Icon(
                    painter = painterResource(Res.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
        }
        HelpButton(
            contentDescription = stringResource(Res.string.weight_chart_ma_help_cd),
            title = stringResource(Res.string.weight_chart_legend),
            content = HelpContent(
                paragraphs = listOf(
                    stringResource(
                        if (metric == BodyMetric.Weight) {
                            Res.string.weight_chart_ma_help_text
                        } else {
                            Res.string.weight_chart_ma_help_text_metric
                        },
                    ),
                ),
            ),
            testTag = "weight_chart_ma_help",
        )
    }
}
