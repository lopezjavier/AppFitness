package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals

class SectionPrescriptionTargetsTest {
    private val types = listOf(RoutineType.Upper, RoutineType.Lower, RoutineType.Upper, RoutineType.Lower)

    private fun chestSetsPerDay(plans: List<RoutinePlan>) =
        plans.map { plan -> plan.slots.filter { it.group == MuscleGroup.Chest }.sumOf { it.sets } }

    @Test
    fun `setsPerSession splits a raised target by floor and gives the remainder to the earliest days`() {
        // 12 over 2 days = 6 + 6; 14 over 3 days = 5 + 5 + 4; 4 over 2 days = 2 + 2; 6 over 4 days = 2 + 2 + 1 + 1.
        assertEquals(listOf(6, 6), List(2) { SectionPrescription.setsPerSession(2, it, 12) })
        assertEquals(listOf(5, 5, 4), List(3) { SectionPrescription.setsPerSession(3, it, 14) })
        assertEquals(listOf(2, 2), List(2) { SectionPrescription.setsPerSession(2, it, 4) })
        assertEquals(listOf(2, 2, 1, 1), List(4) { SectionPrescription.setsPerSession(4, it, 6) })
    }

    @Test
    fun `setsPerSession keeps 10 weekly sets by default`() {
        assertEquals(listOf(4, 3, 3), List(3) { SectionPrescription.setsPerSession(3, it) })
    }

    @Test
    fun `prescribe without targets equals prescribe with every group at 10`() {
        val all = MuscleGroup.entries.associateWith { 10 }
        assertEquals(
            SectionPrescription.prescribe(types, TrainingIntention.Muscle),
            SectionPrescription.prescribe(types, TrainingIntention.Muscle, targets = all),
        )
    }

    @Test
    fun `prescribe uses the group target for the weekly sets`() {
        val plans = SectionPrescription.prescribe(
            types,
            TrainingIntention.Muscle,
            targets = mapOf(MuscleGroup.Chest to 12),
        )
        assertEquals(12, chestSetsPerDay(plans).sum())
        assertEquals(listOf(6, 0, 6, 0), chestSetsPerDay(plans))
    }

    @Test
    fun `prescribe lowers a maintained group to its target and leaves the others at 10`() {
        val plans = SectionPrescription.prescribe(
            types,
            TrainingIntention.Muscle,
            targets = mapOf(MuscleGroup.Chest to 4),
        )
        assertEquals(4, chestSetsPerDay(plans).sum())
        val back = plans.sumOf { p -> p.slots.filter { it.group == MuscleGroup.Back }.sumOf { it.sets } }
        assertEquals(10, back)
    }
}
