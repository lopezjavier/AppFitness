package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Placeholder sheet opened by the FAB until the real flow of [action] exists. */
@OptIn(ExperimentalMaterial3Api::class) // AppBottomSheet exposes the experimental SheetState.
@Composable
internal fun WipActionSheet(action: FabAction, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "wip_sheet") {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = Spacing.screenMargin, end = Spacing.screenMargin, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(action.title),
                style = AppTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }.testTag("wip_sheet_title"),
            )
            Text(
                text = stringResource(Res.string.wip_message),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("wip_sheet_message"),
            )
        }
    }
}
