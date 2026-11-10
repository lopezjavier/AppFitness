package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

/**
 * A summary box that opens its full information on another screen: an [AppCard] with the [title], the key facts as
 * [lines] (facts only, `bodyMedium`) and a decorative chevron. The whole card is one `Role.Button` of at least 48 dp
 * that reads "title, lines"; [testTag] lands on the root.
 */
@Composable
fun SummaryCard(
    title: String,
    lines: List<String>,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                lines.forEach {
                    Text(text = it, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
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
