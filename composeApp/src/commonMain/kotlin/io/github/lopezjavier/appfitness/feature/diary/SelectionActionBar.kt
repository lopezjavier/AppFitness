package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_copy_foods
import io.github.lopezjavier.appfitness.composeapp.resources.diary_selection_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.diary_selection_many
import io.github.lopezjavier.appfitness.composeapp.resources.diary_selection_one
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom bar of the selection mode: the count of selected entries (a polite live region), "Cancelar" and "Copiar
 * alimentos" (disabled with nothing selected).
 */
@Composable
internal fun SelectionActionBar(count: Int, onCopy: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceRaised)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
            .testTag("diary_selection_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = if (count == 1) {
                stringResource(Res.string.diary_selection_one)
            } else {
                stringResource(Res.string.diary_selection_many, count)
            },
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("diary_selection_count"),
        )
        GhostButton(
            text = stringResource(Res.string.diary_selection_cancel),
            onClick = onCancel,
            tone = GhostTone.Neutral,
            modifier = Modifier.testTag("diary_selection_cancel"),
        )
        PrimaryButton(
            text = stringResource(Res.string.diary_copy_foods),
            onClick = onCopy,
            enabled = count > 0,
            modifier = Modifier.testTag("diary_selection_copy"),
        )
    }
}
