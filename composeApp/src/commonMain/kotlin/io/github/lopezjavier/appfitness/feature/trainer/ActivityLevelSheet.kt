package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_sheet_note
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_sheet_offline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_sheet_title
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.feature.biometrics.ActivityCards
import org.jetbrains.compose.resources.stringResource

/** Tag prefix of the sheet: `<prefix>_close` and `<prefix>_activity_<level>` for the cards. */
internal const val ACTIVITY_SHEET_TAG = "activity_level_sheet"

/**
 * Picker sheet of the activity level (US-68, R-12): tapping a card confirms every value with that level at once, no
 * footer. Closes with X or back (R-38). Cards are disabled while saving or offline, with the reason in the body.
 */
@Composable
internal fun ActivityLevelSheet(
    selected: ActivityLevel?,
    saving: Boolean,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) {
    AppPickerSheet(
        title = stringResource(Res.string.trainer_sheet_title),
        onDismiss = { onEvent(TrainerEvent.CloseEdit) },
        testTagPrefix = ACTIVITY_SHEET_TAG,
    ) {
        InlineNote(text = stringResource(Res.string.trainer_sheet_note), testTag = "trainer_sheet_note")
        if (!online) {
            InlineNote(
                text = stringResource(Res.string.trainer_sheet_offline),
                modifier = Modifier.padding(top = Spacing.sm),
                testTag = "trainer_sheet_offline",
            )
        }
        ActivityCards(
            selected = selected,
            onSelect = { onEvent(TrainerEvent.PickLevel(it)) },
            tagPrefix = ACTIVITY_SHEET_TAG,
            modifier = Modifier.padding(top = Spacing.sm),
            enabled = online && !saving,
        )
    }
}
