package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus

/** The first action that applies, in the order of `07` §3 (0). */
sealed interface WeekReportAction {
    /** A proposal still waiting on the live report. */
    data class PendingProposal(val proposal: CalorieProposal) : WeekReportAction

    /**
     * A proposal of a past report, read as text, never a button. [status] is [ProposalStatus.Pending] when the
     * report was stored without an answer: the outcome is unknown ("sin respuesta"), never "pendiente".
     */
    data class DecidedProposal(val proposal: CalorieProposal, val status: ProposalStatus) : WeekReportAction

    data object WeightDiagnosis : WeekReportAction

    data object Stall : WeekReportAction

    /**
     * [past] is true on a stored report for a gap that was fixable that week: it reads as information ("Faltaron
     * pesajes esa semana"), not as a call to action.
     */
    data class MissingData(val kind: MissingKind, val past: Boolean = false) : WeekReportAction

    data object Deload : WeekReportAction

    data object OnPlan : WeekReportAction
}
