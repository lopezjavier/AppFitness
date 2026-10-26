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
 * header (for example an edit icon button) and a [progress] bar under everything. With [onClick] and no
 * [trailingAction] the whole card is one `Role.Button` target. With both, only the label and value block is the
 * button; [supporting] and [trailingAction] are sibling nodes outside it, so they may hold interactive content (an
 * edit pencil, a "Sincronizar" button). [clickLabel] is the spoken action of [onClick] ("Ver detalle"). At least
 * 48 dp high.
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
    clickLabel: String? = null,
) {
    // With a trailing action only the label and value block is the button, so the action is a sibling node.
    val cardClick = onClick.takeIf { trailingAction == null }?.let {
        Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = it)
    } ?: Modifier
    val headClick = onClick.takeIf { trailingAction != null }?.let {
        Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = it)
    } ?: Modifier
    AppCard(modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).then(cardClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).then(headClick),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
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
                }
                supporting?.invoke()
            }
            trailingAction?.invoke()
        }
        if (progress != null) {
            Column(Modifier.padding(top = Spacing.sm)) { progress() }
        }
    }
}
