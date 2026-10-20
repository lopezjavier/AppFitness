package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_copy_meal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_select_foods
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The options of a meal's header menu: "Seleccionar alimentos" (starts the selection mode with the meal's entries) and
 * "Copiar comida" (opens the copy sheet for the whole meal). Shown inline under the header, so it needs no popup.
 */
@Composable
internal fun MealSectionMenu(onSelect: () -> Unit, onCopy: () -> Unit, slotId: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GhostButton(
            text = stringResource(Res.string.diary_select_foods),
            onClick = onSelect,
            tone = GhostTone.Neutral,
            modifier = Modifier.fillMaxWidth().testTag("diary_meal_select_$slotId"),
        )
        GhostButton(
            text = stringResource(Res.string.diary_copy_meal),
            onClick = onCopy,
            tone = GhostTone.Neutral,
            modifier = Modifier.fillMaxWidth().testTag("diary_meal_copy_$slotId"),
        )
    }
}
