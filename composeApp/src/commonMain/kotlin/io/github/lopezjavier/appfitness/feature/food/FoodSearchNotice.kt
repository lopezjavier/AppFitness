package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.food_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.food_not_found_message
import io.github.lopezjavier.appfitness.composeapp.resources.food_not_found_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_scan_error
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScanError() {
    Text(
        text = stringResource(Res.string.food_scan_error),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("food_search_scan_error"),
    )
}

/** The "Producto no encontrado" card of a scanned code, with "Crear alimento" as its primary action. */
@Composable
internal fun NotFoundCard(code: String, onEvent: (FoodSearchEvent) -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag("food_search_not_found_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(Res.string.food_not_found_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.food_not_found_message, code),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            PrimaryButton(
                text = stringResource(Res.string.food_create_action),
                onClick = { onEvent(FoodSearchEvent.CreateFromNotFound) },
                modifier = Modifier.fillMaxWidth().testTag("food_search_not_found_create_button"),
            )
        }
    }
}
