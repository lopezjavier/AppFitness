package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_comment_counter
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_text_label
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_text_offline_action
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_text_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_text_submit
import io.github.lopezjavier.appfitness.designsystem.AppButtonDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource

private const val MEAL_TEXT_MIN_LINES = 4

/**
 * Full-screen step where the user describes the meal in one multi-line field (max [MAX_MEAL_TEXT] characters).
 * "Cancelar" (and back) leaves without a dialog, "Estimar" is pinned at the bottom and disabled while the text is
 * blank or offline.
 */
@Composable
internal fun MealTextStep(state: FoodPhotoUiState, onEvent: (FoodPhotoEvent) -> Unit, modifier: Modifier = Modifier) {
    val counter = stringResource(Res.string.food_photo_comment_counter, state.mealText.length, MAX_MEAL_TEXT)
    FullScreenStep("food_photo_text_step", { onEvent(FoodPhotoEvent.Back) }, modifier) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                AppTextField(
                    value = state.mealText,
                    onValueChange = { onEvent(FoodPhotoEvent.MealTextChanged(it)) },
                    label = stringResource(Res.string.food_photo_text_label),
                    placeholder = stringResource(Res.string.food_photo_text_placeholder),
                    maxLength = MAX_MEAL_TEXT,
                    testTagPrefix = "food_photo_text_input",
                    stateDescription = counter,
                    singleLine = false,
                    minLines = MEAL_TEXT_MIN_LINES,
                    autofocus = true,
                )
                CharCounter(counter, "food_photo_text_input_counter")
            }
            if (state.isOffline) {
                Box(Modifier.padding(horizontal = Spacing.screenMargin).testTag("food_photo_text_offline")) {
                    OfflineHint(stringResource(Res.string.food_photo_text_offline_action))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = { onEvent(FoodPhotoEvent.Back) },
                    modifier = Modifier.widthIn(min = AppButtonDefaults.CompactMinWidth)
                        .testTag("food_photo_text_cancel"),
                )
                PrimaryButton(
                    text = stringResource(Res.string.food_photo_text_submit),
                    onClick = { onEvent(FoodPhotoEvent.SendMealText) },
                    modifier = Modifier.weight(1f).testTag("food_photo_text_submit"),
                    enabled = state.mealText.isNotBlank() && !state.isOffline,
                )
            }
        }
    }
}
