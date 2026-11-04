package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TimeFitTest {
    private fun slot(
        group: MuscleGroup,
        sets: Int,
        min: Int = 10,
        max: Int = 12,
        rest: Int = 90,
        kind: SlotKind = SlotKind.Standard,
    ) = Slot(group, kind, sets, min, max, rest)

    // Golden values copied from routineChecker.test.ts (routineSeconds).
    @Test
    fun seconds_sixIsolations_matchesTheTypeScriptEstimate() {
        val slots = List(6) { slot(MuscleGroup.Biceps, 3) }
        assertEquals(2592, TimeFit.seconds(slots))
    }

    @Test
    fun seconds_supersetPair_matchesTheTypeScriptEstimate() {
        val slots = listOf(slot(MuscleGroup.Chest, 3, 10, 10), slot(MuscleGroup.Back, 3, 10, 10))
        assertEquals(570, TimeFit.seconds(slots, listOf(SupersetPair(0, 1))))
        assertEquals(840, TimeFit.seconds(slots))
    }

    @Test
    fun seconds_pairUsesTheLongerRestAndTheMoreSets() {
        val slots = listOf(slot(MuscleGroup.Chest, 4, 10, 10, 60), slot(MuscleGroup.Back, 2, 10, 10, 120))
        // 60 + 4 x (40 + 40 + 120)
        assertEquals(860, TimeFit.seconds(slots, listOf(SupersetPair(0, 1))))
    }

    private fun plan(type: RoutineType, vararg sets: Pair<MuscleGroup, Int>) =
        RoutinePlan(type, sets.map { slot(it.first, it.second) })

    @Test
    fun fits_returnsTheSlotsUnchanged() {
        val plan = plan(RoutineType.Upper, MuscleGroup.Chest to 3, MuscleGroup.Back to 3)
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Muscle, 60))
        assertEquals(plan.slots, fit.slots)
        assertTrue(fit.supersets.isEmpty())
        assertFalse(fit.cutSets)
    }

    @Test
    fun supersetsComeBeforeCuts_withTheFixedPairForTheType() {
        val plan = plan(RoutineType.Upper, MuscleGroup.Chest to 3, MuscleGroup.Back to 3, MuscleGroup.Shoulders to 3)
        val plain = TimeFit.seconds(plan.slots)
        val limit = (plain - 60) / 60 + 1
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Muscle, limit))
        assertEquals(listOf(SupersetPair(0, 1)), fit.supersets)
        assertFalse(fit.cutSets)
        assertTrue(fit.seconds <= limit * 60)
    }

    @Test
    fun fullBody_pairsChestBackQuadsHamstringsShouldersGlutes_inThatOrderUntilItFits() {
        val groups = RoutineType.FullBody.groups
        val plan = RoutinePlan(RoutineType.FullBody, groups.map { slot(it, 3) })
        val all = listOf(SupersetPair(0, 2), SupersetPair(1, 3), SupersetPair(4, 5))
        val fit =
            assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Muscle, (TimeFit.seconds(plan.slots, all) + 59) / 60))
        assertEquals(3, fit.supersets.size)
        val pairedGroups = fit.supersets.map { setOf(fit.slots[it.first].group, fit.slots[it.second].group) }.toSet()
        assertEquals(
            setOf(
                setOf(MuscleGroup.Chest, MuscleGroup.Back),
                setOf(MuscleGroup.Quads, MuscleGroup.Hamstrings),
                setOf(MuscleGroup.Shoulders, MuscleGroup.Glutes),
            ),
            pairedGroups,
        )
    }

    @Test
    fun supersetsOnlyPairEqualSets() {
        val plan = plan(RoutineType.Upper, MuscleGroup.Chest to 4, MuscleGroup.Back to 3)
        val minimum = TimeFit.seconds(plan.slots.map { it.copy(sets = 2) })
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Muscle, (minimum + 59) / 60))
        assertTrue(fit.supersets.isEmpty())
    }

    @Test
    fun cutsTheBiggestSlot_laterFirst_neverBelowTwo_andFlagsTheCut() {
        val plan = plan(RoutineType.Pull, MuscleGroup.Back to 4, MuscleGroup.Hamstrings to 4, MuscleGroup.Glutes to 3)
        val total = TimeFit.seconds(plan.slots)
        val limit = (total - 1) / 60
        // Strength never pairs, so the cut is the first reduction left.
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Strength, limit))
        assertTrue(fit.cutSets)
        assertEquals(listOf(4, 3, 3), fit.slots.map { it.sets })
        assertTrue(fit.slots.all { it.sets >= 2 })
    }

    @Test
    fun cut_accessoryBeforeMain_onTies() {
        val main = Slot(MuscleGroup.Chest, SlotKind.Main, 3, 4, 6, 120)
        val accessory = Slot(MuscleGroup.Back, SlotKind.Accessory, 3, 6, 8, 120)
        val plan = RoutinePlan(RoutineType.Upper, listOf(main, accessory))
        val limit = (TimeFit.seconds(plan.slots) - 1) / 60
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Strength, limit))
        assertEquals(listOf(3, 2), fit.slots.map { it.sets })
    }

    @Test
    fun strength_reducesMainRestFirst_andNeverUsesSupersets() {
        val main = Slot(MuscleGroup.Chest, SlotKind.Main, 4, 4, 6, 180)
        val back = Slot(MuscleGroup.Back, SlotKind.Main, 4, 4, 6, 180)
        val plan = RoutinePlan(RoutineType.Upper, listOf(main, back))
        val saved = TimeFit.seconds(plan.slots.map { it.copy(restSeconds = 120) })
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Strength, saved / 60 + 1))
        assertTrue(fit.slots.all { it.restSeconds == 120 })
        assertFalse(fit.cutSets)
        val floor = TimeFit.seconds(plan.slots.map { it.copy(sets = 2, restSeconds = 120) })
        val tight = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Strength, (floor + 59) / 60))
        assertTrue(tight.supersets.isEmpty())
    }

    @Test
    fun strength_untouchedWhenItFits() {
        val main = Slot(MuscleGroup.Chest, SlotKind.Main, 3, 4, 6, 180)
        val fit =
            assertIs<Fit>(TimeFit.fit(RoutinePlan(RoutineType.Upper, listOf(main)), TrainingIntention.Strength, 60))
        assertEquals(180, fit.slots.single().restSeconds)
    }

    @Test
    fun doesNotFit_whenTwoSetsPerSlotExceedTheLimit_evenForStrengthWithoutSupersets() {
        val plan = RoutinePlan(RoutineType.FullBody, RoutineType.FullBody.groups.map { slot(it, 5) })
        val result = assertIs<DoesNotFit>(TimeFit.fit(plan, TrainingIntention.Strength, 10))
        assertEquals(10, result.limitMinutes)
        assertEquals((TimeFit.seconds(plan.slots.map { it.copy(sets = 2) }) + 59) / 60, result.minutesNeeded)
    }

    @Test
    fun doesNotFit_reportsMinutesNeededAtTwoSetsPerSlot() {
        val slots = listOf(slot(MuscleGroup.Chest, 2), slot(MuscleGroup.Back, 2))
        val plan = RoutinePlan(RoutineType.Lower, slots) // no pair for chest/back in Lower
        val result = assertIs<DoesNotFit>(TimeFit.fit(plan, TrainingIntention.Muscle, 5))
        assertEquals((TimeFit.seconds(slots) + 59) / 60, result.minutesNeeded)
    }

    @Test
    fun cutOfASupersetSlot_cutsItsPartnerToo() {
        val plan = plan(RoutineType.Upper, MuscleGroup.Chest to 4, MuscleGroup.Back to 4)
        val paired = TimeFit.seconds(plan.slots, listOf(SupersetPair(0, 1)))
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Muscle, (paired - 1) / 60))
        assertEquals(listOf(SupersetPair(0, 1)), fit.supersets)
        assertEquals(fit.slots[0].sets, fit.slots[1].sets)
        assertTrue(fit.cutSets)
    }

    @Test
    fun extras_doNotEnterTheFit_butAreReportedAsOverTime() {
        val plan = plan(RoutineType.Upper, MuscleGroup.Chest to 3)
        val fit = assertIs<Fit>(TimeFit.fit(plan, TrainingIntention.Muscle, 60))
        val extras = listOf(slot(MuscleGroup.Biceps, 2))
        assertEquals(0, TimeFit.overMinutes(fit, extras, 60))
        val tightLimit = fit.seconds / 60 // minutes floor: extras push it over
        assertTrue(TimeFit.overMinutes(fit, extras, tightLimit) >= 3)
        assertEquals(fit.slots, plan.slots)
    }
}
