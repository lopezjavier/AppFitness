package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_error
import io.github.lopezjavier.appfitness.composeapp.resources.coach_limit_user
import io.github.lopezjavier.appfitness.composeapp.resources.coach_timeout
import io.github.lopezjavier.appfitness.composeapp.resources.coach_unavailable
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Spanish message of a failed AI call. The user can do nothing about the global limit or a missing provider
 * balance, so both read as "not available now"; only their own monthly limit is worded as such.
 */
internal fun coachFailureMessage(failure: CoachFailure): StringResource = when (failure) {
    is CoachFailure.LimitReached ->
        if (failure.scope == CoachFailure.Scope.User) Res.string.coach_limit_user else Res.string.coach_unavailable

    CoachFailure.NoCredit -> Res.string.coach_unavailable

    CoachFailure.Timeout -> Res.string.coach_timeout

    CoachFailure.Other -> Res.string.coach_error
}

/** A message (not a dialog, R-08) for a failed AI call, shown where the action failed (R-31). */
@Composable
internal fun CoachLimitMessage(failure: CoachFailure, modifier: Modifier = Modifier, testTag: String? = null) {
    InlineNote(text = stringResource(coachFailureMessage(failure)), modifier = modifier, testTag = testTag)
}
