package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SectionPrescriptionTest {
    private fun setsOf(plan: RoutinePlan, group: MuscleGroup) = plan.slots.filter { it.group == group }.map { it.sets }

    @Test
    fun fourDaysUpperLower_fivePlusFive() {
        val types = SplitCatalog.splitsFor(4).first().types
        val plans = SectionPrescription.prescribe(types, TrainingIntention.Muscle)
        assertEquals(listOf(5), setsOf(plans[0], MuscleGroup.Chest))
        assertEquals(listOf(5), setsOf(plans[2], MuscleGroup.Chest))
        assertEquals(listOf(5), setsOf(plans[1], MuscleGroup.Quads))
    }

    @Test
    fun sixDays_fourThreeThree() {
        val types = SplitCatalog.splitsFor(6).first().types
        val plans = SectionPrescription.prescribe(types, TrainingIntention.Muscle)
        assertEquals(listOf(4, 3, 3), listOf(0, 2, 4).map { setsOf(plans[it], MuscleGroup.Chest).sum() })
    }

    @Test
    fun oneDay_tenSetsPerGroupInTwoSlotsOfFive() {
        val plan = SectionPrescription.prescribe(listOf(RoutineType.FullBody), TrainingIntention.Muscle).single()
        for (g in SplitCatalog.ALL_GROUPS) assertEquals(listOf(5, 5), setsOf(plan, g))
        assertEquals(12, plan.slots.size)
    }

    @Test
    fun fullBodyThreeDays_threeThreeFourWithRemainderToTheFirstDay() {
        val types = SplitCatalog.splitsFor(3).first().types
        val plans = SectionPrescription.prescribe(types, TrainingIntention.Muscle)
        assertEquals(listOf(4, 3, 3), plans.map { setsOf(it, MuscleGroup.Chest).sum() })
    }

    @Test
    fun distribute_boundaries() {
        assertEquals(listOf(1), SectionPrescription.distribute(1))
        assertEquals(listOf(2), SectionPrescription.distribute(2))
        assertEquals(listOf(5), SectionPrescription.distribute(5))
        assertEquals(listOf(3, 3), SectionPrescription.distribute(6))
        assertEquals(listOf(4, 3), SectionPrescription.distribute(7))
        assertEquals(listOf(5, 5), SectionPrescription.distribute(10))
        assertTrue(SectionPrescription.distribute(0).isEmpty())
    }

    @Test
    fun strengthMainSlot_followsTheSetsPerSession() {
        fun slots(s: Int) = SectionPrescription.prescribe(
            List(10 / s) { RoutineType.FullBody }.let { if (s == 10) listOf(RoutineType.FullBody) else it },
            TrainingIntention.Strength,
        ).first().slots.filter { it.group == MuscleGroup.Chest }
        // 10 per day on one day, 5 per day on two days, 4 on... (10 / 2 = 5)
        assertEquals(listOf(3, 4, 3), slots(10).map { it.sets })
        assertEquals(listOf(3, 2), slots(5).map { it.sets })
    }

    @Test
    fun strength_fourSetsIsOneMainSlot_oneSetIsOneMainSlot() {
        val four = SectionPrescription.prescribe(List(10) { RoutineType.FullBody }.take(3), TrainingIntention.Strength)
        assertEquals(listOf(4), four[0].slots.filter { it.group == MuscleGroup.Chest }.map { it.sets })
        val one = SectionPrescription.prescribe(
            listOf(
                RoutineType.Upper,
                RoutineType.Lower,
                RoutineType.Upper,
                RoutineType.Lower,
                RoutineType.Upper,
                RoutineType.Lower,
            ),
            TrainingIntention.Strength,
        )
        assertEquals(listOf(3), one[2].slots.filter { it.group == MuscleGroup.Chest }.map { it.sets })
    }

    @Test
    fun strength_mainSlotsFirstThenAccessories() {
        val plan = SectionPrescription.prescribe(listOf(RoutineType.FullBody), TrainingIntention.Strength).single()
        val kinds = plan.slots.map { it.kind }
        assertEquals(List(6) { SlotKind.Main } + List(12) { SlotKind.Accessory }, kinds)
    }

    @Test
    fun valuesPerIntention() {
        val types = listOf(RoutineType.Upper, RoutineType.Lower)
        for (i in listOf(
            TrainingIntention.Muscle,
            TrainingIntention.FatLoss,
            TrainingIntention.Recomposition,
            TrainingIntention.Maintenance,
        )) {
            val slot = SectionPrescription.prescribe(types, i).first().slots.first()
            assertEquals(
                listOf(SlotKind.Standard, 8, 12, 8, 120),
                listOf(slot.kind, slot.repMin, slot.repMax, slot.rpe, slot.restSeconds),
            )
        }
        val plan = SectionPrescription.prescribe(types, TrainingIntention.Strength).first()
        val main = plan.slots.first { it.kind == SlotKind.Main }
        val accessory = plan.slots.first { it.kind == SlotKind.Accessory }
        assertEquals(listOf(4, 6, 8, 180), listOf(main.repMin, main.repMax, main.rpe, main.restSeconds))
        assertEquals(
            listOf(6, 8, 8, 120),
            listOf(accessory.repMin, accessory.repMax, accessory.rpe, accessory.restSeconds),
        )
    }

    @Test
    fun strengthMainWithoutAMainLift_becomesAnAccessory() {
        val plan = SectionPrescription.prescribe(
            listOf(RoutineType.Upper, RoutineType.Lower),
            TrainingIntention.Strength,
        ) { it != MuscleGroup.Shoulders }.first()
        val shoulders = plan.slots.filter { it.group == MuscleGroup.Shoulders }
        assertTrue(shoulders.all { it.kind == SlotKind.Accessory && it.repMin == 6 && it.restSeconds == 120 })
        assertTrue(shoulders.first().downgraded)
        assertEquals(10, shoulders.sumOf { it.sets })
    }

    @Test
    fun extraSlot_isTwoSetsWithTheAccessoryValues() {
        val e = SectionPrescription.extraSlot(MuscleGroup.Biceps, TrainingIntention.Muscle)
        assertEquals(listOf(2, 8, 12, 120), listOf(e.sets, e.repMin, e.repMax, e.restSeconds))
        val s = SectionPrescription.extraSlot(MuscleGroup.Biceps, TrainingIntention.Strength)
        assertEquals(listOf(2, 6, 8, 120), listOf(s.sets, s.repMin, s.repMax, s.restSeconds))
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun everySplitAndIntention_reachesTenSetsPerGroup() {
        for (days in 1..6) {
            for (split in SplitCatalog.splitsFor(days)) {
                for (i in TrainingIntention.entries) {
                    val plans = SectionPrescription.prescribe(split.types, i)
                    for (g in SplitCatalog.ALL_GROUPS) {
                        assertEquals(
                            10,
                            plans.sumOf { p ->
                                p.slots.filter { it.group == g }.sumOf { it.sets }
                            },
                            "$days $i $g",
                        )
                    }
                }
            }
        }
    }
}
