@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument list holds a few short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.reports_block_phase_kept
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import org.jetbrains.compose.resources.stringResource

private const val PREFIX = "block_review"

/**
 * The phase proposal of a stored review (R-60): the decision as plain text, or the proposal itself without buttons
 * when none was taken (never "Sin decidir"). A stored "kept" has no promise of a future proposal.
 */
@Composable
internal fun StoredPhaseSection(state: BlockReviewUiState.Content, proposal: PhaseProposal) {
    val decisionTag = Modifier.testTag("past_report_decision")
    when (state.review.phaseDecision) {
        PhaseDecision.Accepted -> StatusCard(
            stringResource(
                Res.string.block_review_phase_accepted,
                stringResource(PhaseText.label(proposal.toPhase)),
            ),
            decisionTag,
        )

        PhaseDecision.Kept -> StatusCard(stringResource(Res.string.reports_block_phase_kept), decisionTag)

        null -> SummaryCard("block_review_phase_status") {
            val reason = BlockReviewText.proposalReason(proposal)
            PhaseRowContent(proposal.toPhase, stringResource(reason.res, *reason.args.toTypedArray()), PREFIX)
        }
    }
}
