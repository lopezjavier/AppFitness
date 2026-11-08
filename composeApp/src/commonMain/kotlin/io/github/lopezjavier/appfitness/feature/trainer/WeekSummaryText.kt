package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_maintain_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_maintain_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_none_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_none_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_protein_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_protein_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sessions_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sessions_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sessions_one_day_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sets_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sets_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sleep_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sleep_cause
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

/**
 * The figures of the weekly summary as the copy shows them (04 section 5): decimal comma, the real minus sign, one
 * decimal for kilograms and for sets ("10" or "2,5"), integers for counts. The sentences live in `strings.xml`.
 */
@Suppress("TooManyFunctions") // One formatter per figure of the summary.
internal object WeekSummaryText {
    /**
     * The "¿Por qué no progreso?" line of one stalled lift (07 section 3 (7)): the cause and action string resources
     * with their formatted figures. The screen appends [group] to [causeArgs], in lower case when [groupLowercase]
     * (mid-sentence) and as written otherwise (sentence start). [missingFood] adds the extra data line.
     */
    data class StallLine(
        val cause: StringResource,
        val causeArgs: List<String>,
        val action: StringResource,
        val actionArgs: List<String> = emptyList(),
        val group: MuscleGroup? = null,
        val groupLowercase: Boolean = false,
        val missingFood: Boolean = false,
    )

    fun stallLine(cause: StallCause): StallLine = when (cause) {
        is StallCause.Sessions -> StallLine(
            cause = Res.string.week_summary_stall_sessions_cause,
            causeArgs = listOf(sets(cause.avg)),
            action = if (cause.planDays == 1) {
                Res.string.week_summary_stall_sessions_one_day_action
            } else {
                Res.string.week_summary_stall_sessions_action
            },
        )

        is StallCause.Sets -> StallLine(
            cause = Res.string.week_summary_stall_sets_cause,
            causeArgs = listOf(sets(cause.avg)),
            action = Res.string.week_summary_stall_sets_action,
            group = cause.group,
            groupLowercase = true,
        )

        is StallCause.GroupOnMaintain -> StallLine(
            cause = Res.string.week_summary_stall_maintain_cause,
            causeArgs = emptyList(),
            action = Res.string.week_summary_stall_maintain_action,
            group = cause.group,
        )

        is StallCause.Protein -> StallLine(
            cause = Res.string.week_summary_stall_protein_cause,
            causeArgs = listOf(AppNumberFormat.integer(cause.avgG), AppNumberFormat.decimal(cause.perKg, 2)),
            action = Res.string.week_summary_stall_protein_action,
            actionArgs = listOf(AppNumberFormat.integer(cause.targetG)),
        )

        is StallCause.Sleep -> StallLine(
            cause = Res.string.week_summary_stall_sleep_cause,
            causeArgs = listOf(sets(cause.avgH)),
            action = Res.string.week_summary_stall_sleep_action,
        )

        is StallCause.NoClearCause -> StallLine(
            cause = Res.string.week_summary_stall_none_cause,
            causeArgs = emptyList(),
            action = Res.string.week_summary_stall_none_action,
            missingFood = cause.proteinGateMissing,
        )
    }

    /** Sets of a group: "10" or "2,5". */
    fun sets(value: Double): String = AppNumberFormat.decimal(value, maxDecimals = 1)

    /** Kilograms with one decimal: "80,2". */
    fun kg(value: Double): String = AppNumberFormat.decimal(value, maxDecimals = 1)

    /** A weekly rate with its sign: "−0,3", "+0,2", "0". */
    fun signedKg(value: Double): String {
        val text = kg(value)
        return if (value > 0 && text != "0") "+$text" else text
    }

    /** The calorie change of a proposal with its sign: "+100", "−150". */
    fun kcalDelta(proposal: CalorieProposal): String =
        AppNumberFormat.signedInteger(proposal.toKcal - proposal.fromKcal)

    /** The average against the goal with its sign: "−90", "+120", "0". */
    fun kcalDifference(avgKcal: Int, targetKcal: Int): String = AppNumberFormat.signedInteger(avgKcal - targetKcal)

    /** Whole grams of protein: "148". */
    fun proteinGrams(grams: Double): String = AppNumberFormat.integer(grams.roundToInt())

    /** Grams of protein per kilogram of body weight, up to two decimals: "1,8". */
    fun perKg(value: Double): String = AppNumberFormat.decimal(value, maxDecimals = 2)

    /** Groups with at least some sets; a group with 0 is omitted. */
    fun visibleGroups(groups: List<GroupSets>): List<GroupSets> = groups.filter { it.sets > 0 }

    /** The weekly sets of one group fit the singular form only at exactly 1. */
    fun isSingular(sets: Double): Boolean = sets == 1.0

    /** The deload line is shown only when the changes do not already list the deload as a row. */
    fun showsSeparateDeload(summary: WeekSummary): Boolean = summary.deload && summary.changes.none { it.isDeloadRow() }

    private fun CoachChange.isDeloadRow(): Boolean = kind.isDeload
}
