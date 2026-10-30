@file:Suppress("MatchingDeclarationName") // The file holds the copy holder and the shared content.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The two messages that name what the coach was asked for (a plan, a block); the rest of the copy is shared. */
internal class CoachFailureCopy(val error: StringResource, val interrupted: StringResource)

/**
 * The state of a failed AI call (R-31) shared by the coach proposal screens: the limit message, the offline state or
 * an error with "Reintentar". Tags are `<tagPrefix>_limit`, `_offline`, `_error*` and `_retry`.
 */
@Composable
internal fun CoachFailureContent(
    kind: PlanFailureKind,
    isOnline: Flow<Boolean>,
    copy: CoachFailureCopy,
    tagPrefix: String,
    onRetry: () -> Unit,
) {
    val retry = AppStateAction(stringResource(Res.string.action_retry), onRetry)
    val errorMessage: @Composable (StringResource) -> Unit = { message ->
        ErrorState(
            message = stringResource(message),
            action = retry,
            testTagPrefix = "${tagPrefix}_error",
            actionTestTag = "${tagPrefix}_retry",
        )
    }
    when (kind) {
        is PlanFailureKind.Limit -> CoachLimitMessage(
            failure = CoachFailure.LimitReached(kind.scope),
            modifier = Modifier.padding(Spacing.screenMargin),
            testTag = "${tagPrefix}_limit",
        )

        PlanFailureKind.NoCredit -> CoachLimitMessage(
            failure = CoachFailure.NoCredit,
            modifier = Modifier.padding(Spacing.screenMargin),
            testTag = "${tagPrefix}_limit",
        )

        PlanFailureKind.Offline -> Box(Modifier.testTag("${tagPrefix}_offline")) {
            OfflineState(isOnline = isOnline, onReload = onRetry)
        }

        PlanFailureKind.Interrupted -> errorMessage(copy.interrupted)

        PlanFailureKind.Timeout, PlanFailureKind.Invalid, is PlanFailureKind.Missing, PlanFailureKind.Infeasible ->
            errorMessage(coachFailureMessage(kind.toCoachFailure()))

        PlanFailureKind.Other -> errorMessage(copy.error)
    }
}

private fun PlanFailureKind.toCoachFailure(): CoachFailure = when (this) {
    PlanFailureKind.Timeout -> CoachFailure.Timeout
    PlanFailureKind.Invalid -> CoachFailure.Invalid
    is PlanFailureKind.Missing -> CoachFailure.Missing(fields)
    PlanFailureKind.Infeasible -> CoachFailure.Infeasible
    else -> CoachFailure.Other
}
