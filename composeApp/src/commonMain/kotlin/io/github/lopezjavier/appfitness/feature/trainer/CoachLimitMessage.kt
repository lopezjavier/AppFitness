package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_block_in_progress
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_infeasible
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_invalid
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_missing_generic
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_missing_intention
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_missing_place
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_missing_profile
import io.github.lopezjavier.appfitness.composeapp.resources.coach_timeout
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.MissingField
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Spanish message of a failed AI call: a timeout, an invalid answer, missing data or an infeasible plan get their
 * own copy; every other failure reads as a generic "could not use the AI, try again".
 */
internal fun coachFailureMessage(failure: CoachFailure): StringResource = when (failure) {
    CoachFailure.Timeout -> Res.string.coach_timeout

    CoachFailure.Invalid -> Res.string.coach_failure_invalid

    is CoachFailure.Missing -> missingMessage(failure.fields)

    CoachFailure.Infeasible -> Res.string.coach_failure_infeasible

    CoachFailure.NeedsConfirmation,
    CoachFailure.Conflict,
    CoachFailure.InvalidReplacement,
    CoachFailure.InvalidBlock,
    CoachFailure.Other,
    -> Res.string.coach_error

    CoachFailure.BlockInProgress -> Res.string.coach_failure_block_in_progress
}

/** Names where to fix what is missing: the profile data first, then the training intention, then the place. */
private fun missingMessage(fields: Set<MissingField>): StringResource = when {
    fields.any {
        it == MissingField.Sex || it == MissingField.Age || it == MissingField.Height ||
            it == MissingField.Weight || it == MissingField.Expenditure
    } ->
        Res.string.coach_failure_missing_profile

    fields.isEmpty() || MissingField.Unknown in fields -> Res.string.coach_failure_missing_generic

    MissingField.Intention in fields -> Res.string.coach_failure_missing_intention

    else -> Res.string.coach_failure_missing_place
}

/** A message (not a dialog, R-08) for a failed AI call, shown where the action failed (R-31). */
@Composable
internal fun CoachLimitMessage(failure: CoachFailure, modifier: Modifier = Modifier, testTag: String? = null) {
    InlineNote(text = stringResource(coachFailureMessage(failure)), modifier = modifier, testTag = testTag)
}
