package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_sheet_note
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_sheet_title
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.feature.biometrics.ActivityCards
import org.jetbrains.compose.resources.stringResource

/** Tag prefix of the sheet: `<prefix>_close`, `<prefix>_commit` and `<prefix>_activity_<level>` for the cards. */
internal const val ACTIVITY_SHEET_TAG = "activity_level_sheet"

/**
 * Edit sheet of the activity level (US-68): the five activity cards and "Guardar", which confirms every value with the
 * picked level. Closes only with X or back (R-38); the draft is owned by the view model.
 */
@Composable
internal fun ActivityLevelSheet(
    selected: ActivityLevel?,
    saving: Boolean,
    online: Boolean,
    onEvent: (TrainerEvent) -> Unit,
) {
    AppEditSheet(
        title = stringResource(Res.string.trainer_sheet_title),
        commit = SheetCommit.Save,
        onCommit = { onEvent(TrainerEvent.SaveEdit) },
        onDismiss = { onEvent(TrainerEvent.CloseEdit) },
        commitEnabled = online && selected != null,
        saving = saving,
        commitHint = if (online) null else offlineHint(stringResource(Res.string.trainer_save_action)),
        testTagPrefix = ACTIVITY_SHEET_TAG,
    ) {
        InlineNote(text = stringResource(Res.string.trainer_sheet_note))
        ActivityCards(
            selected = selected,
            onSelect = { onEvent(TrainerEvent.PickLevel(it)) },
            tagPrefix = ACTIVITY_SHEET_TAG,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
