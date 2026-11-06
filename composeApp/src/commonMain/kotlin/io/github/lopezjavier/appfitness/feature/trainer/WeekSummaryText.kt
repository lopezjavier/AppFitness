package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.WeekSummary

/**
 * The figures of the weekly summary as the copy shows them (04 section 5): decimal comma, the real minus sign, one
 * decimal for kilograms and for sets ("10" or "2,5"), integers for counts. The sentences live in `strings.xml`.
 */
internal object WeekSummaryText {
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

    /** Groups with at least some sets; a group with 0 is omitted. */
    fun visibleGroups(groups: List<GroupSets>): List<GroupSets> = groups.filter { it.sets > 0 }

    /** The weekly sets of one group fit the singular form only at exactly 1. */
    fun isSingular(sets: Double): Boolean = sets == 1.0

    /** The deload line is shown only when the changes do not already list the deload as a row. */
    fun showsSeparateDeload(summary: WeekSummary): Boolean = summary.deload && summary.changes.none { it.isDeloadRow() }

    private fun CoachChange.isDeloadRow(): Boolean = kind.isDeload
}
