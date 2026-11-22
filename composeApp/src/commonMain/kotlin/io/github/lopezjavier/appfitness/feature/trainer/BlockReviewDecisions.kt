@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold a few short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_accept
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_decision_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_keep
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_kept
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_offline
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CoachProposalCard
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import org.jetbrains.compose.resources.stringResource

private const val PREFIX = "block_review"

/** The phase proposal while it waits; once decided, one status line from the stored decision (never from memory). */
@Composable
internal fun PhaseProposalSection(
    state: BlockReviewUiState.Content,
    proposal: PhaseProposal,
    online: Boolean,
    onEvent: (BlockReviewEvent) -> Unit,
) {
    when (state.review.phaseDecision) {
        PhaseDecision.Accepted -> StatusCard(
            stringResource(
                Res.string.block_review_phase_accepted,
                stringResource(PhaseText.label(proposal.toPhase)),
            ),
        )

        PhaseDecision.Kept -> StatusCard(stringResource(Res.string.block_review_phase_kept))

        null -> {
            val reason = BlockReviewText.proposalReason(proposal)
            val reasonText = stringResource(reason.res, *reason.args.toTypedArray())
            CoachProposalCard(
                acceptText = stringResource(Res.string.action_accept),
                discardText = stringResource(Res.string.block_review_phase_keep),
                onAccept = { onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted)) },
                onDiscard = { onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Kept)) },
                testTagPrefix = "block_review_phase",
                busy = state.deciding != null,
                acceptEnabled = online,
                footer = if (online && state.decisionFailed == null) {
                    null
                } else {
                    {
                        if (!online) OfflineHint(stringResource(Res.string.block_review_phase_offline))
                        if (state.decisionFailed != null) {
                            DecisionFailure("block_review_phase_retry") { onEvent(BlockReviewEvent.RetryDecision) }
                        }
                    }
                },
                content = { PhaseRowContent(proposal.toPhase, reasonText, PREFIX) },
            )
        }
    }
}

@Composable
internal fun StatusCard(text: String) {
    ReportNoteCard("block_review_phase_status") { SummaryLine(text) }
}

@Composable
internal fun CalorieSection(state: BlockReviewUiState.Content, online: Boolean, onEvent: (BlockReviewEvent) -> Unit) {
    val proposal = state.calorieProposal ?: return
    CalorieProposalCard(
        proposal = proposal,
        deciding = state.decidingCalorie,
        online = online,
        onApply = { onEvent(BlockReviewEvent.ApplyCalorie) },
        onNotNow = { onEvent(BlockReviewEvent.NotNowCalorie) },
        testTagPrefix = "block_review_calorie",
        failure = state.calorieFailed?.let { choice ->
            { DecisionFailure("block_review_calorie_retry") { onEvent(choice.toEvent()) } }
        },
    )
}

/** The event that repeats a failed calorie [CalorieChoice]. */
internal fun CalorieChoice.toEvent(): BlockReviewEvent = when (this) {
    CalorieChoice.Apply -> BlockReviewEvent.ApplyCalorie
    CalorieChoice.NotNow -> BlockReviewEvent.NotNowCalorie
}

/** "No se pudo guardar tu decisión" with "Reintentar" inline in the card (R-37); the choice is kept in state. */
@Composable
private fun DecisionFailure(retryTag: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.block_review_decision_error),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.error,
        )
        GhostButton(
            text = stringResource(Res.string.action_retry),
            onClick = onRetry,
            modifier = Modifier.testTag(retryTag),
        )
    }
}
