package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.WeekProgress
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState

private const val WEIGH_IN_DAYS_GATE = 3
private const val COMPLETE_DAYS_GATE = 5
private const val BASE_WEEK = 1

/**
 * The coach card (`07` §3 (0)): the progress sentence and the first action that applies: proposal > weight diagnosis >
 * stall > missing data > deload > on plan. [progress] is null for a v1 report: no sentence, the action comes from the
 * kept fields. On a past report ([live] false) a proposal is read as its outcome.
 */
fun weekReportHeadline(summary: WeekSummary, progress: WeekProgress?, live: Boolean): CoachCardUi =
    CoachCardUi(sentenceOf(summary, progress), actionOf(summary, progress, live))

private fun actionOf(summary: WeekSummary, progress: WeekProgress?, live: Boolean): WeekReportAction {
    val decisions = summary.nutrition?.decisions.orEmpty()
    val pending = decisions.firstOrNull { it.status == ProposalStatus.Pending }
    val missing = missingOf(summary, progress)
    return when {
        live && pending != null -> WeekReportAction.PendingProposal(pending.proposal)

        !live && decisions.isNotEmpty() -> decisions.first().let {
            WeekReportAction.DecidedProposal(it.proposal, it.status)
        }

        summary.nutrition?.diagnosis != null -> WeekReportAction.WeightDiagnosis

        summary.stalls.isNotEmpty() -> WeekReportAction.Stall

        missing != null -> WeekReportAction.MissingData(missing, past = !live)

        summary.deload -> WeekReportAction.Deload

        else -> WeekReportAction.OnPlan
    }
}

// Gaps that can be fixed come before the informational "no longer recorded" ones.
private val MISSING_ORDER =
    listOf(MissingKind.WeighIns, MissingKind.Food, MissingKind.DeletedSessions, MissingKind.DeletedWeighIns)

private fun missingOf(summary: WeekSummary, progress: WeekProgress?): MissingKind? {
    val kinds = progress?.missing ?: buildList {
        if (summary.weighInDays < WEIGH_IN_DAYS_GATE) add(MissingKind.WeighIns)
        if (summary.completeDays < COMPLETE_DAYS_GATE) add(MissingKind.Food)
    }
    return MISSING_ORDER.firstOrNull { it in kinds }
}

private fun sentenceOf(summary: WeekSummary, progress: WeekProgress?): CoachSentenceUi? = when {
    progress == null -> null

    progress.strength.blockWeek <= BASE_WEEK -> CoachSentenceUi(null, null, null, base = true)

    else -> {
        val compared = progress.strength.groups.flatMap { it.exercises }.filter { it.state != ProgressState.Uncompared }
        CoachSentenceUi(
            strengthUp = if (compared.isEmpty()) null else compared.count { it.state == ProgressState.Up },
            weightChangeKg = progress.weight.changeKg,
            onPace = onPaceOf(weekPaceOf(progress, summary)?.state),
        )
    }
}

private fun onPaceOf(state: WeightPaceState?): Boolean? = when (state) {
    WeightPaceState.OnPace, WeightPaceState.Stable -> true
    WeightPaceState.Slower, WeightPaceState.Faster, WeightPaceState.Rising, WeightPaceState.Falling -> false
    else -> null
}
