package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_active_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_diary
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_profile_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_steps
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_template
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_training_history
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_missing_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_provisional_state
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_value_supporting
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.CoachMissing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One derived value of the Trainer card: label, "value · source" line (only the source when [value] is blank: the
 * value then lives in [trailing]), the "Provisional" chip while it is not
 * confirmed (R-39, R-41: text and icon, announced as "Valor provisional"), the missing data note and any extra
 * [notes]. Tags: `trainer_row_<key>`, `trainer_provisional_<key>`, `trainer_missing_note_<key>`.
 */
@Composable
@Suppress("LongParameterList") // Label, value, source, state, missing data and the slots of one row.
internal fun TrainerValueRow(
    key: String,
    headline: String,
    value: String,
    supportingText: String,
    provisional: Boolean,
    missing: List<CoachMissing>,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    notes: @Composable ColumnScope.() -> Unit = {},
) {
    val provisionalState = stringResource(Res.string.trainer_provisional_state)
    Column(modifier.fillMaxWidth().testTag("trainer_row_$key")) {
        AppListRow(
            headline = headline,
            supporting = if (value.isBlank()) {
                supportingText
            } else {
                stringResource(Res.string.trainer_value_supporting, value, supportingText)
            },
            trailing = trailing,
            modifier = if (provisional) Modifier.semantics { stateDescription = provisionalState } else Modifier,
        )
        if (provisional) {
            StatusChip(
                text = stringResource(Res.string.trainer_provisional),
                tone = StatusTone.Caution,
                modifier = Modifier.testTag("trainer_provisional_$key"),
            )
        }
        val labels = missing.mapNotNull { item -> missingLabel(item)?.let { stringResource(it) } }
        if (labels.isNotEmpty()) {
            InlineNote(
                text = stringResource(Res.string.trainer_missing_template, labels.joinToString(", ")),
                modifier = Modifier.padding(top = Spacing.xs),
                testTag = "trainer_missing_note_$key",
            )
        }
        notes()
    }
}

/** The Spanish word of a missing datum; an unknown server key has none and is omitted, never shown raw. */
private fun missingLabel(missing: CoachMissing): StringResource? = when (missing) {
    CoachMissing.Steps -> Res.string.trainer_missing_steps
    CoachMissing.ActiveKcal -> Res.string.trainer_missing_active_kcal
    CoachMissing.TrainingHistory -> Res.string.trainer_missing_training_history
    CoachMissing.ProfileData -> Res.string.trainer_missing_profile_data
    CoachMissing.Diary -> Res.string.trainer_missing_diary
    CoachMissing.WeighIns -> Res.string.trainer_missing_weigh_ins
    is CoachMissing.Unknown -> null
}
