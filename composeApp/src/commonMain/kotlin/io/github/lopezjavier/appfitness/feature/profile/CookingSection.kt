package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_air_fryer_label
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_air_fryer_support
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_error
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_fried_label
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_fried_support
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_griddle_label
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_griddle_support
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_helper
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_DECIMALS = 1

/**
 * "Cocina" editor content (US-50): the added-fat percentage of the raw weight per cooking method (plancha,
 * air fryer, frito).
 * Nothing is saved until the pinned "Guardar" of Preferencias; an invalid field shows its error and disables it.
 */
@Composable
internal fun CookingSection(
    form: FoodPreferencesForm,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    run {
        Column(
            modifier.fillMaxWidth().testTag("preferences_cooking_card"),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FatPctField(
                value = form.griddleText,
                onChange = { onEvent(PreferencesEvent.SetFatPctGriddle(it)) },
                label = Res.string.preferences_cooking_griddle_label,
                support = Res.string.preferences_cooking_griddle_support,
                hasError = form.griddleError,
                tag = "preferences_cooking_griddle",
                imeAction = ImeAction.Next,
            )
            FatPctField(
                value = form.airFryerText,
                onChange = { onEvent(PreferencesEvent.SetFatPctAirFryer(it)) },
                label = Res.string.preferences_cooking_air_fryer_label,
                support = Res.string.preferences_cooking_air_fryer_support,
                hasError = form.airFryerError,
                tag = "preferences_cooking_air_fryer",
                imeAction = ImeAction.Next,
            )
            FatPctField(
                value = form.friedText,
                onChange = { onEvent(PreferencesEvent.SetFatPctFried(it)) },
                label = Res.string.preferences_cooking_fried_label,
                support = Res.string.preferences_cooking_fried_support,
                hasError = form.friedError,
                tag = "preferences_cooking_fried",
                imeAction = ImeAction.Done,
            )
            Text(
                text = stringResource(Res.string.preferences_cooking_helper),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList") // One field: value, event, texts, error flag, tag and IME action.
private fun FatPctField(
    value: String,
    onChange: (String) -> Unit,
    label: StringResource,
    support: StringResource,
    hasError: Boolean,
    tag: String,
    imeAction: ImeAction,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        NumberInput(
            value = value,
            onValueChange = onChange,
            label = stringResource(label),
            modifier = Modifier.fillMaxWidth().testTag(tag),
            maxDecimals = MAX_DECIMALS,
            imeAction = imeAction,
        )
        if (hasError) {
            val message = stringResource(Res.string.preferences_cooking_error)
            Text(
                text = message,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(message)
                    }
                    .testTag("${tag}_error"),
            )
        } else {
            Text(
                text = stringResource(support),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${tag}_support"),
            )
        }
    }
}
