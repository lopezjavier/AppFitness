package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_action_create_next
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_action_missing_food
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_action_missing_food_past
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_action_missing_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_action_missing_weigh_ins_past
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_action_phase_pending
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_sentence
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_sentence_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_report_sentence_lift
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_phase_accepted
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_downward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calculate
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fact_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.ic_remove
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_flat
import io.github.lopezjavier.appfitness.composeapp.resources.reports_block_phase_kept
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_up
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_up_none
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_weight
import io.github.lopezjavier.appfitness.composeapp.resources.week_report_sentence_weight_same
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_header
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The coach card of the block review (`07` §3 (A0)): the same lit card as the weekly report ([ReportCoachCard]) with
 * the block sentence ("En este bloque: 5 ejercicios suben, press banca +5 kg (+6 %), peso −1,8 kg.") and the
 * first action. [onOpen] makes the action a link to the pending decision card on the same screen; a stored review
 * passes null.
 */
@Composable
internal fun BlockReportCoachCard(coach: BlockCoachUi, names: Map<String, String>, onOpen: (() -> Unit)?) {
    ReportCoachCard(
        sentence = coach.sentence?.let { blockSentence(it, names) },
        look = blockActionLook(coach.action),
        onOpen = onOpen,
        scope = ReportScope.Block,
    )
}

@Composable
private fun blockSentence(sentence: BlockSentenceUi, names: Map<String, String>): String {
    val parts = buildList {
        sentence.ups?.let { up ->
            add(
                if (up == 0) {
                    stringResource(Res.string.week_report_sentence_up_none)
                } else {
                    pluralStringResource(Res.plurals.week_report_sentence_up, up, up)
                },
            )
        }
        // A lift whose name is unknown is left out (R-54): the sentence never shows an id.
        sentence.mainLift?.let { lift ->
            names[lift.exerciseId]?.let { name ->
                add(
                    stringResource(
                        Res.string.block_report_sentence_lift,
                        name,
                        WeekSummaryText.signedKg(lift.changeKg),
                        lift.changePct?.let { " (${WeekSummaryText.signedKg(it)} %)" }.orEmpty(),
                    ),
                )
            }
        }
        sentence.weightChangeKg?.let { change ->
            val text = WeekSummaryText.signedKg(change)
            add(
                if (text == "0") {
                    stringResource(Res.string.week_report_sentence_weight_same)
                } else {
                    stringResource(Res.string.week_report_sentence_weight, text)
                },
            )
        }
    }
    return if (parts.isEmpty()) {
        stringResource(Res.string.block_report_sentence_empty)
    } else {
        stringResource(Res.string.block_report_sentence, parts.joinToString(", "))
    }
}

@Composable
private fun blockActionLook(action: BlockReportAction): ActionLook = when (action) {
    is BlockReportAction.PendingPhase -> ActionLook(
        Res.drawable.ic_flag,
        stringResource(Res.string.block_report_action_phase_pending, stringResource(PhaseText.label(action.toPhase))),
    )

    is BlockReportAction.PendingCalorie -> ActionLook(
        Res.drawable.ic_calculate,
        proposalText(action.proposal, ProposalStatus.Pending, unanswered = false),
    )

    BlockReportAction.Stall ->
        ActionLook(Res.drawable.ic_trending_flat, stringResource(Res.string.week_summary_stall_header))

    is BlockReportAction.MissingData -> ActionLook(
        Res.drawable.ic_fact_check,
        stringResource(blockMissingRes(action)),
    )

    is BlockReportAction.DecidedPhase -> when (action.decision) {
        PhaseDecision.Accepted -> ActionLook(
            Res.drawable.ic_check,
            stringResource(Res.string.block_review_phase_accepted, stringResource(PhaseText.label(action.toPhase))),
        )

        PhaseDecision.Kept -> ActionLook(Res.drawable.ic_remove, stringResource(Res.string.reports_block_phase_kept))
    }

    BlockReportAction.CreateNext ->
        ActionLook(Res.drawable.ic_arrow_downward, stringResource(Res.string.block_report_action_create_next))
}

/** The missing-data line in block words ("por semana", "en el bloque"); the weekly wording says "esta semana". */
private fun blockMissingRes(action: BlockReportAction.MissingData): StringResource = when (action.kind) {
    MissingKind.WeighIns -> if (action.past) {
        Res.string.block_report_action_missing_weigh_ins_past
    } else {
        Res.string.block_report_action_missing_weigh_ins
    }

    MissingKind.Food -> if (action.past) {
        Res.string.block_report_action_missing_food_past
    } else {
        Res.string.block_report_action_missing_food
    }

    else -> missingRes(WeekReportAction.MissingData(action.kind, action.past))
}
