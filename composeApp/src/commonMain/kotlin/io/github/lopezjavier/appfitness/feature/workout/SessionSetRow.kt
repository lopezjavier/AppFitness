package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_cloud_off
import io.github.lopezjavier.appfitness.composeapp.resources.live_invalid_set
import io.github.lopezjavier.appfitness.composeapp.resources.live_pending_save
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SetRow
import io.github.lopezjavier.appfitness.designsystem.SetRowValues
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Maps a live domain row to the values of a [SetRow]. The single place to extend for other set entry types. */
internal fun LiveSetRow.toSetRowValues() = SetRowValues(
    index = setIndex,
    previous = null,
    weight = weight,
    reps = reps,
    completed = completed,
    weightPlaceholder = weightPlaceholder,
    repsPlaceholder = repsPlaceholder,
)

/**
 * One set of the live table: the design-system [SetRow] plus, outside it, a marker when the write is queued
 * (icon and text) and a hint when checking was refused.
 */
@Composable
internal fun SessionSetRow(
    position: Int,
    row: LiveSetRow,
    onEvent: (LiveSessionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = "live_set_${position}_${row.setIndex}"
    Column(modifier) {
        SetRow(
            values = row.toSetRowValues(),
            onWeightChange = { onEvent(LiveSessionEvent.WeightChanged(position, row.setIndex, it)) },
            onRepsChange = { onEvent(LiveSessionEvent.RepsChanged(position, row.setIndex, it)) },
            onCompletedChange = { onEvent(LiveSessionEvent.SetChecked(position, row.setIndex, it)) },
            modifier = Modifier.testTag(tag),
            testTagPrefix = tag,
        )
        if (row.pendingSave) PendingSaveLabel(Modifier.testTag("${tag}_pending"))
        if (row.invalid) {
            Text(
                text = stringResource(Res.string.live_invalid_set),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(start = Spacing.xl).semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("${tag}_invalid"),
            )
        }
    }
}

/** "Pendiente de guardar": amber icon and text under a row whose write has not reached the server. */
@Composable
private fun PendingSaveLabel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = Spacing.xl).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_cloud_off),
            contentDescription = null,
            tint = AppTheme.colors.energyAmber,
            modifier = Modifier.size(Spacing.lg),
        )
        Text(
            text = stringResource(Res.string.live_pending_save),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.energyAmber,
        )
    }
}
