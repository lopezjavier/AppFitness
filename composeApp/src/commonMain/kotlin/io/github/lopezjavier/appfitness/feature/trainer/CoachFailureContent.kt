@file:Suppress("MatchingDeclarationName") // The file holds the copy holder and the shared content.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.coach_failure_block_in_progress_action
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.MissingField
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The two messages that name what the coach was asked for (a plan, a block); the rest of the copy is shared. */
internal class CoachFailureCopy(val error: StringResource, val interrupted: StringResource)

/**
 * The state of a failed AI call (R-31) shared by the coach proposal screens: the offline state or
 * an error with "Reintentar"; "Ya tienes un bloque en marcha" offers [onLeave] first. Tags are `<tagPrefix>_offline`,
 * `_error*`, `_leave` and `_retry`.
 */
@Composable
internal fun CoachFailureContent(
    kind: PlanFailureKind,
    isOnline: Flow<Boolean>,
    copy: CoachFailureCopy,
    tagPrefix: String,
    onFix: ((PlanFixTarget) -> Unit)? = null,
    onLeave: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    val retry = AppStateAction(stringResource(Res.string.action_retry), onRetry)
    val errorMessage: @Composable (StringResource) -> Unit = { message ->
        // A missing or infeasible input fails again on retry: the screen that fixes it comes first (R-59).
        val target = if (onFix != null) kind.fixTarget() else null
        if (target != null && onFix != null) {
            ErrorState(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(message),
                action = AppStateAction(stringResource(target.destinationLabel())) { onFix(target) },
                testTagPrefix = "${tagPrefix}_error",
                secondaryAction = retry,
                actionTestTag = "${tagPrefix}_fix",
                secondaryActionTestTag = "${tagPrefix}_retry",
            )
        } else if (kind == PlanFailureKind.BlockInProgress && onLeave != null) {
            // A block already runs: retrying cannot help, so the way out comes first (V-69).
            ErrorState(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(message),
                action = AppStateAction(stringResource(Res.string.coach_failure_block_in_progress_action), onLeave),
                testTagPrefix = "${tagPrefix}_error",
                secondaryAction = retry,
                actionTestTag = "${tagPrefix}_leave",
                secondaryActionTestTag = "${tagPrefix}_retry",
            )
        } else {
            ErrorState(
                modifier = Modifier.fillMaxSize(),
                message = stringResource(message),
                action = retry,
                testTagPrefix = "${tagPrefix}_error",
                actionTestTag = "${tagPrefix}_retry",
            )
        }
    }
    when (kind) {
        PlanFailureKind.Offline -> Box(Modifier.testTag("${tagPrefix}_offline")) {
            OfflineState(isOnline = isOnline, onReload = onRetry)
        }

        PlanFailureKind.Interrupted -> errorMessage(copy.interrupted)

        PlanFailureKind.Timeout,
        PlanFailureKind.Invalid,
        is PlanFailureKind.Missing,
        PlanFailureKind.Infeasible,
        PlanFailureKind.BlockInProgress,
        -> errorMessage(coachFailureMessage(kind.toCoachFailure()))

        PlanFailureKind.Other -> errorMessage(copy.error)
    }
}

/** Where the input a failed call lacks is fixed; null when no screen fixes it (R-59). */
private fun PlanFailureKind.fixTarget(): PlanFixTarget? = when (this) {
    is PlanFailureKind.Missing ->
        if (fields.any { it == MissingField.Intention || it == MissingField.Place }) {
            PlanFixTarget.MyTraining
        } else {
            PlanFixTarget.PersonalData
        }

    PlanFailureKind.Infeasible -> PlanFixTarget.PersonalData

    else -> null
}

private fun PlanFailureKind.toCoachFailure(): CoachFailure = when (this) {
    PlanFailureKind.Timeout -> CoachFailure.Timeout
    PlanFailureKind.Invalid -> CoachFailure.Invalid
    is PlanFailureKind.Missing -> CoachFailure.Missing(fields)
    PlanFailureKind.Infeasible -> CoachFailure.Infeasible
    PlanFailureKind.BlockInProgress -> CoachFailure.BlockInProgress
    else -> CoachFailure.Other
}
