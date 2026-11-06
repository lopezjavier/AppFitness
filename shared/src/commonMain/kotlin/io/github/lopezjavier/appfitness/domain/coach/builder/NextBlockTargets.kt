package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** What the user chose for a muscle group at the end of a block ("Priorizar" / "Mantener" / neither). */
enum class GroupPriority { Prioritise, Maintain, None }

/**
 * Weekly sets per muscle group of the next block (07 section 3 "Block builder" (e), section 12.4); pure.
 * Only the six H1 groups are offered (Q-B8).
 */
object NextBlockTargets {
    const val DEFAULT_BASE = SectionPrescription.WEEKLY_SETS
    const val GAIN = 2
    const val CAP = 20
    const val MAINTAIN_SETS = 4
    const val MAINTAIN_SETS_FROM_65 = 6
    const val OLDER_AGE = 65
    const val MAX_PRIORITISED = 3
    const val MIN_ADHERENCE_PCT = 80

    /** Why a group gets no own gain or cannot be prioritised; [ThreeChosen] is a hard refusal of a fourth choice. */
    enum class Blocked { ThreeChosen, Cut, LowAdherence }

    data class Row(
        val group: MuscleGroup,
        val choice: GroupPriority,
        val before: Int,
        val after: Int,
        val blocked: Blocked?,
    )

    data class Result(val targets: Map<MuscleGroup, Int>, val rows: List<Row>)

    /**
     * [base] is the previous block's weekly sets per group (10 when absent). [accepted] are the groups whose
     * "Sugerencia: +2 series" is applied. A "Priorizar" gains [GAIN] only with adherence of at least 80 % and no cut;
     * the sets freed by "Mantener" go to the prioritised groups in chunks of [GAIN], in group order, up to [CAP].
     */
    @Suppress("LongParameterList") // The inputs of the rule.
    fun compute(
        base: Map<MuscleGroup, Int>,
        choices: Map<MuscleGroup, GroupPriority>,
        adherencePct: Int?,
        cut: Boolean,
        age: Int?,
        accepted: Set<MuscleGroup>,
    ): Result {
        val groups = SplitCatalog.ALL_GROUPS
        val before = groups.associateWith { (base[it] ?: DEFAULT_BASE).coerceIn(0, CAP) }
        val maintained = groups.filter { choices[it] == GroupPriority.Maintain }
        val prioritised = groups.filter { choices[it] == GroupPriority.Prioritise }.take(MAX_PRIORITISED)
        val lowAdherence = adherencePct == null || adherencePct < MIN_ADHERENCE_PCT
        val maintainSets = if (age != null && age >= OLDER_AGE) MAINTAIN_SETS_FROM_65 else MAINTAIN_SETS

        val after = before.toMutableMap()
        for (g in accepted) if (g in after && g !in maintained) after[g] = minOf(CAP, after.getValue(g) + GAIN)
        var freed = 0
        for (g in maintained) {
            val target = minOf(before.getValue(g), maintainSets)
            freed += before.getValue(g) - target
            after[g] = target
        }
        if (!cut && !lowAdherence) {
            for (g in prioritised) after[g] = minOf(CAP, after.getValue(g) + GAIN)
        }
        share(after, prioritised, freed) // What exceeds the cap is unused.

        val rows = groups.map { g ->
            val blocked = blockedReason(g in prioritised, prioritised.size, cut, lowAdherence)
            val choice = if (g in maintained || g in prioritised) choices.getValue(g) else GroupPriority.None
            Row(g, choice, before.getValue(g), after.getValue(g), blocked)
        }
        return Result(after, rows)
    }

    private fun blockedReason(chosen: Boolean, prioritisedCount: Int, cut: Boolean, lowAdherence: Boolean): Blocked? =
        when {
            prioritisedCount >= MAX_PRIORITISED && !chosen -> Blocked.ThreeChosen
            cut -> Blocked.Cut
            lowAdherence -> Blocked.LowAdherence
            else -> null
        }

    /** Hands [freed] sets to [groups] in chunks of [GAIN], round robin, until none is left or all are at the cap. */
    private fun share(targets: MutableMap<MuscleGroup, Int>, groups: List<MuscleGroup>, freed: Int): Int {
        var left = freed
        var progressed = true
        while (left > 0 && progressed) {
            progressed = false
            for (g in groups) {
                if (left == 0) break
                val give = minOf(GAIN, left, CAP - targets.getValue(g))
                if (give > 0) {
                    targets[g] = targets.getValue(g) + give
                    left -= give
                    progressed = true
                }
            }
        }
        return left
    }

    /**
     * "Sugerencia: +2 series" is offered on a stalled group with adherence of at least 80 %, no open discomfort on it,
     * no cut, room under the cap and no "Me ha costado" block (E14 Q2).
     */
    @Suppress("LongParameterList") // The limits of 07 (6).
    fun suggestionEligible(
        group: MuscleGroup,
        stalledGroups: Set<MuscleGroup>,
        adherencePct: Int?,
        discomfortGroups: Set<MuscleGroup>,
        cut: Boolean,
        currentTarget: Int,
        hardBlock: Boolean,
    ): Boolean = group in stalledGroups && adherencePct != null && adherencePct >= MIN_ADHERENCE_PCT &&
        group !in discomfortGroups && !cut && currentTarget + GAIN <= CAP && !hardBlock
}
