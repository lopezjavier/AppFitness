@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument list holds a few short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import org.jetbrains.compose.resources.stringResource

private const val PREFIX = "block_review"

/**
 * The phase proposal of a stored review (R-60): the proposal and its reason without buttons. The decision is shown
 * once, in the coach card ([BlockReportAction.DecidedPhase]), never repeated here (C-195).
 */
@Composable
internal fun StoredPhaseSection(proposal: PhaseProposal) {
    ReportNoteCard("block_review_phase_status") {
        val reason = BlockReviewText.proposalReason(proposal)
        PhaseRowContent(proposal.toPhase, stringResource(reason.res, *reason.args.toTypedArray()), PREFIX)
    }
}
