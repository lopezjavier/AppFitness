package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * A summary box that opens its full information on another screen: an [AppCard] with the [title], an optional
 * [headline] figure (`headlineMetric`) with its [headlineUnit], the key facts as [lines] (`bodyMedium`), key-value
 * [facts] (label at the start, value W600 at the end, 4 dp apart) and a [progress] slot, plus a decorative chevron
 * aligned to the top. [highlighted] marks a card that waits for a decision (R-94). The whole card is one `Role.Button`
 * of at least 48 dp that reads "title, headline, lines, facts"; [testTag] lands on the root.
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
    facts: List<Pair<String, String>> = emptyList(),
    highlighted: Boolean = false,
    progress: (@Composable () -> Unit)? = null,
) {
    AppCard(
        modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        highlighted = highlighted,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                if (headline != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = headline,
                            style = AppTheme.typography.headlineMetric,
                            color = AppTheme.colors.textPrimary,
                        )
                        if (headlineUnit != null) {
                            Text(
                                text = headlineUnit,
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary,
                            )
                        }
                    }
                }
                lines.forEach {
                    Text(text = it, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                }
                facts.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = label,
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                            color = AppTheme.colors.textPrimary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                if (progress != null) {
                    Box(Modifier.fillMaxWidth().padding(top = Spacing.sm)) { progress() }
                }
            }
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
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
