package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextBlockTargetsTest {
    private val base10 = SplitCatalog.ALL_GROUPS.associateWith { 10 }

    private fun compute(
        choices: Map<MuscleGroup, GroupPriority> = emptyMap(),
        base: Map<MuscleGroup, Int> = base10,
        adherence: Int? = 85,
        cut: Boolean = false,
        age: Int? = 30,
        accepted: Set<MuscleGroup> = emptySet(),
    ) = NextBlockTargets.compute(base, choices, adherence, cut, age, accepted)

    @Test
    fun `without choices every group keeps its base`() {
        val result = compute()
        assertEquals(base10, result.targets)
        assertTrue(result.rows.all { it.before == it.after })
    }

    @Test
    fun `example 1 - prioritise adds 2 sets with adherence 85`() {
        val result = compute(mapOf(MuscleGroup.Shoulders to GroupPriority.Prioritise))
        assertEquals(12, result.targets.getValue(MuscleGroup.Shoulders))
        assertEquals(10, result.row(MuscleGroup.Shoulders).before)
        assertEquals(12, result.row(MuscleGroup.Shoulders).after)
    }

    @Test
    fun `example 2 - prioritise is capped at 20`() {
        val base = base10 + (MuscleGroup.Chest to 19)
        assertEquals(20, compute(mapOf(MuscleGroup.Chest to GroupPriority.Prioritise), base).targets[MuscleGroup.Chest])
        val full = base10 + (MuscleGroup.Chest to 20)
        assertEquals(20, compute(mapOf(MuscleGroup.Chest to GroupPriority.Prioritise), full).targets[MuscleGroup.Chest])
    }

    @Test
    fun `example 3 - maintain sets 4 and 6 from age 65 and the freed sets go to the prioritised group`() {
        val choices = mapOf(
            MuscleGroup.Back to GroupPriority.Maintain,
            MuscleGroup.Shoulders to GroupPriority.Prioritise,
        )
        val young = compute(choices, age = 64)
        assertEquals(4, young.targets[MuscleGroup.Back])
        // own +2 and the 6 freed sets.
        assertEquals(18, young.targets[MuscleGroup.Shoulders])
        val older = compute(choices, age = 65)
        assertEquals(6, older.targets[MuscleGroup.Back])
        assertEquals(16, older.targets[MuscleGroup.Shoulders])
    }

    @Test
    fun `freed sets beyond the cap are unused`() {
        val choices = mapOf(
            MuscleGroup.Back to GroupPriority.Maintain,
            MuscleGroup.Quads to GroupPriority.Maintain,
            MuscleGroup.Shoulders to GroupPriority.Prioritise,
        )
        assertEquals(20, compute(choices).targets[MuscleGroup.Shoulders])
    }

    @Test
    fun `example 4 - low adherence blocks the own gain but freed sets still flow`() {
        val choices = mapOf(
            MuscleGroup.Back to GroupPriority.Maintain,
            MuscleGroup.Shoulders to GroupPriority.Prioritise,
        )
        val result = compute(choices, adherence = 70)
        assertEquals(16, result.targets[MuscleGroup.Shoulders])
        assertEquals(NextBlockTargets.Blocked.LowAdherence, result.row(MuscleGroup.Shoulders).blocked)
    }

    @Test
    fun `example 4 without maintain - low adherence adds nothing and shows the reason`() {
        val result = compute(mapOf(MuscleGroup.Shoulders to GroupPriority.Prioritise), adherence = 70)
        assertEquals(10, result.targets[MuscleGroup.Shoulders])
        assertEquals(NextBlockTargets.Blocked.LowAdherence, result.row(MuscleGroup.Shoulders).blocked)
    }

    @Test
    fun `adherence 79 80 and unknown`() {
        val choice = mapOf(MuscleGroup.Chest to GroupPriority.Prioritise)
        assertEquals(10, compute(choice, adherence = 79).targets[MuscleGroup.Chest])
        assertEquals(12, compute(choice, adherence = 80).targets[MuscleGroup.Chest])
        assertEquals(10, compute(choice, adherence = null).targets[MuscleGroup.Chest])
    }

    @Test
    fun `example 5 - in a cut a prioritised group receives only the freed sets`() {
        val choices = mapOf(
            MuscleGroup.Back to GroupPriority.Maintain,
            MuscleGroup.Shoulders to GroupPriority.Prioritise,
        )
        val result = compute(choices, cut = true)
        assertEquals(16, result.targets[MuscleGroup.Shoulders])
        assertEquals(NextBlockTargets.Blocked.Cut, result.row(MuscleGroup.Shoulders).blocked)
    }

    @Test
    fun `in a cut without maintain a prioritised group gets nothing`() {
        val result = compute(mapOf(MuscleGroup.Shoulders to GroupPriority.Prioritise), cut = true)
        assertEquals(10, result.targets[MuscleGroup.Shoulders])
    }

    @Test
    fun `example 6 - a fourth prioritise is refused`() {
        val groups = listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Shoulders)
        val three = groups.associateWith { GroupPriority.Prioritise }
        val result = compute(three)
        assertEquals(NextBlockTargets.Blocked.ThreeChosen, result.row(MuscleGroup.Quads).blocked)
        assertNull(result.row(MuscleGroup.Chest).blocked)
        val four = compute(three + (MuscleGroup.Quads to GroupPriority.Prioritise))
        assertEquals(10, four.targets[MuscleGroup.Quads])
        assertEquals(12, four.targets[MuscleGroup.Chest])
    }

    @Test
    fun `freed sets go to prioritised groups in group order, 2 at a time`() {
        val choices = mapOf(
            MuscleGroup.Quads to GroupPriority.Maintain,
            MuscleGroup.Chest to GroupPriority.Prioritise,
            MuscleGroup.Back to GroupPriority.Prioritise,
        )
        val result = compute(choices, adherence = 50)
        // 6 freed in chunks of 2 in group order: Chest, Back, Chest.
        assertEquals(14, result.targets[MuscleGroup.Chest])
        assertEquals(12, result.targets[MuscleGroup.Back])
    }

    @Test
    fun `example 7 - an accepted suggestion adds 2 and removing it restores the base`() {
        val applied = compute(accepted = setOf(MuscleGroup.Chest))
        assertEquals(12, applied.targets[MuscleGroup.Chest])
        assertEquals(10, compute().targets[MuscleGroup.Chest])
    }

    @Test
    fun `the result is deterministic`() {
        val choices = mapOf(MuscleGroup.Back to GroupPriority.Maintain, MuscleGroup.Chest to GroupPriority.Prioritise)
        assertEquals(compute(choices), compute(choices))
    }

    @Test
    fun `groups outside the six are not offered`() {
        val result = compute(mapOf(MuscleGroup.Biceps to GroupPriority.Prioritise))
        assertEquals(SplitCatalog.ALL_GROUPS, result.rows.map { it.group })
        assertNull(result.targets[MuscleGroup.Biceps])
    }

    @Test
    fun `suggestion needs stalled, high adherence, no discomfort, no cut, room under 20 and no hard block`() {
        fun eligible(
            stalled: Set<MuscleGroup> = setOf(MuscleGroup.Chest),
            adherence: Int? = 90,
            discomfort: Set<MuscleGroup> = emptySet(),
            cut: Boolean = false,
            target: Int = 10,
            hard: Boolean = false,
        ) = NextBlockTargets.suggestionEligible(MuscleGroup.Chest, stalled, adherence, discomfort, cut, target, hard)
        assertTrue(eligible())
        assertTrue(eligible(adherence = 80))
        assertTrue(eligible(target = 18))
        assertEquals(false, eligible(stalled = emptySet()))
        assertEquals(false, eligible(adherence = 79))
        assertEquals(false, eligible(adherence = null))
        assertEquals(false, eligible(discomfort = setOf(MuscleGroup.Chest)))
        assertEquals(false, eligible(cut = true))
        assertEquals(false, eligible(target = 19))
        assertEquals(false, eligible(hard = true))
    }

    private fun NextBlockTargets.Result.row(group: MuscleGroup) = rows.first { it.group == group }
}
