package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/**
 * One key-value line of a [SummaryCard]: [label] at the start, [value] W600 at the end. With a [fraction] a 4 dp
 * [AppLinearProgress] sits 4 dp below (clamped at 100 %, `athleticCyan`, never amber); a [muted] value (no data) is
 * `textTertiary` and regular weight; [staggerIndex] times the one-time fill (R-97).
 */
data class SummaryFact(
    val label: String,
    val value: String,
    val fraction: Float? = null,
    val muted: Boolean = false,
    val staggerIndex: Int? = null,
)

/**
 * A summary box that opens its full information on another screen: an [AppCard] with the [title] and an optional
 * [status] slot (chips) before the chevron, then a full-width [content] slot, an optional [headline] figure
 * (`headlineMetric`) with its [headlineUnit], a [progress] slot, the key facts as [lines] (`bodyMedium`), key-value
 * [facts] (8 dp apart), a decorative chevron aligned to the top and a full-width [footer] slot. [highlighted] marks a
 * card that waits for a decision (R-94). The body (title, status, content, headline, progress, lines, facts) is one
 * `Role.Button` of at least 48 dp and carries [testTag]. The [footer] and the [titleAction] (a 48 dp icon button
 * placed before the chevron, its space reserved in the title row) sit outside that button with their own targets
 * (R-103). Slots bring their own spacing: the column gap is 4 dp.
 */
@Composable
@Suppress("LongParameterList") // Slots fixed by the round 3 summary-card contract (R-95).
fun SummaryCard(
    title: String,
    lines: List<String>,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    headline: String? = null,
    headlineUnit: String? = null,
    facts: List<SummaryFact> = emptyList(),
    highlighted: Boolean = false,
    progress: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    titleAction: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth(), highlighted = highlighted) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(Modifier.fillMaxWidth()) {
                SummaryBody(
                    Modifier
                        .testTag(testTag)
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics(mergeDescendants = true) {},
                    title, status, titleAction != null, content, headline, headlineUnit, progress, lines, facts,
                )
                // Outside the merged button: its own 48 dp target next to the chevron (R-103).
                titleAction?.let {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = Spacing.xl + Spacing.sm)) { it() }
                }
            }
            footer?.invoke()
        }
    }
}

@Composable
@Suppress("LongParameterList") // The merged body of the card; every slot of the public contract.
private fun SummaryBody(
    modifier: Modifier,
    title: String,
    status: (@Composable () -> Unit)?,
    reserveAction: Boolean,
    content: (@Composable () -> Unit)?,
    headline: String?,
    headlineUnit: String?,
    progress: (@Composable () -> Unit)?,
    lines: List<String>,
    facts: List<SummaryFact>,
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SummaryTitleRow(title, status, reserveAction)
        content?.invoke()
        if (headline != null) {
            Row(
                Modifier.padding(top = if (content != null) Spacing.sm else Spacing.none),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(text = headline, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.textPrimary)
                if (headlineUnit != null) {
                    Text(
                        text = headlineUnit,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
        }
        progress?.invoke()
        lines.forEach {
            Text(text = it, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        if (facts.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) { facts.forEach { SummaryFactRow(it) } }
        }
    }
}

@Composable
private fun SummaryTitleRow(title: String, status: (@Composable () -> Unit)?, reserveAction: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        status?.invoke()
        if (reserveAction) Spacer(Modifier.size(Spacing.minTouchTarget))
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}

@Composable
private fun SummaryFactRow(fact: SummaryFact) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = fact.label,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = fact.value,
                style = if (fact.muted) {
                    AppTheme.typography.bodyMedium
                } else {
                    AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600)
                },
                color = if (fact.muted) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
        fact.fraction?.let {
            AppLinearProgress(
                fraction = it,
                stateDescription = fact.value,
                barHeight = AppLinearProgressDefaults.SlimBarHeight,
                staggerIndex = fact.staggerIndex,
            )
        }
    }
}

@Preview
@Composable
private fun SummaryCardPreview() {
    AppTheme {
        SummaryCard("Tu plan", listOf("Definición · 2.100 kcal", "Cardio 90 min"), {}, "summary_card")
    }
}
