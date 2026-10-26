package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A card with one headline number: the technical [label] over the [value] (`headlineMetric`, same styles as
 * [MetricTile]), then optional [supporting] content (a trend, a hint), a [trailingAction] at the end of the
 * header (for example an edit icon button) and a [progress] bar under everything. With [onClick] the card is
 * one `Role.Button` target (the trailing action then must not be interactive). At least 48 dp high.
 */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    progress: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    AppCard(modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).then(clickable)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = label,
                    style = MetricTileDefaults.labelStyle(AppTheme.typography),
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    text = value,
                    style = MetricTileDefaults.valueStyle(AppTheme.typography),
                    color = AppTheme.colors.textPrimary,
                )
                supporting?.invoke()
            }
            trailingAction?.invoke()
        }
        if (progress != null) {
            Column(Modifier.padding(top = Spacing.sm)) { progress() }
        }
    }
}
