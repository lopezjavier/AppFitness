package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_empty_action
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_empty_plain_message
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_error_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_state_messages
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_state_action_count
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the empty and error state blocks (T-026). */
internal fun LazyListScope.stateMessageItems() {
    item { StateMessagesSection() }
}

@Composable
private fun StateMessagesSection() {
    var count by rememberSaveable { mutableIntStateOf(0) }
    val onAction: () -> Unit = remember { { count++ } }
    val addLabel = stringResource(Res.string.catalog_empty_action)
    val retryLabel = stringResource(Res.string.state_retry)
    val addAction = remember(addLabel, onAction) { AppStateAction(addLabel, onAction) }
    val retryAction = remember(retryLabel, onAction) { AppStateAction(retryLabel, onAction) }
    CatalogSection(
        stringResource(Res.string.catalog_section_state_messages),
        "catalog_section_state_messages",
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                EmptyState(
                    message = stringResource(Res.string.catalog_empty_message),
                    title = stringResource(Res.string.catalog_empty_title),
                    action = addAction,
                    testTagPrefix = "catalog_empty_state",
                )
                EmptyState(
                    message = stringResource(Res.string.catalog_empty_plain_message),
                    testTagPrefix = "catalog_empty_state_plain",
                )
                ErrorState(
                    message = stringResource(Res.string.catalog_error_message),
                    action = retryAction,
                    title = stringResource(Res.string.catalog_error_title),
                    testTagPrefix = "catalog_error_state",
                )
            }
        }
        Text(
            text = stringResource(Res.string.catalog_state_action_count, count.toString()),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("catalog_state_action_count"),
        )
    }
}
