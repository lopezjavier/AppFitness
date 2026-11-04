package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotEligibilityTest {
    private val standard = Slot(MuscleGroup.Chest, SlotKind.Standard, 5, 8, 12, 120)
    private val main = Slot(MuscleGroup.Chest, SlotKind.Main, 3, 4, 6, 180)

    private fun ids(
        slot: Slot,
        cat: List<io.github.lopezjavier.appfitness.domain.model.CatalogExercise>,
        ctx: PickerContext = context(),
        used: Set<String> = emptySet(),
    ) = SlotEligibility.picker(slot, cat, ctx, used).map { it.exercise.id }

    @Test
    fun onlyThePrimaryGroupOfTheSlot() {
        val cat = listOf(
            exercise("chest-1", MuscleGroup.Chest),
            exercise("back-1", MuscleGroup.Back, secondary = listOf(MuscleGroup.Chest)),
        )
        assertEquals(listOf("chest-1"), ids(standard, cat))
    }

    @Test
    fun equipmentFilter_andEmptyEquipmentFiltersNothing() {
        val cat = listOf(
            exercise("bar", MuscleGroup.Chest, equipment = Equipment.Barbell),
            exercise("cab", MuscleGroup.Chest, equipment = Equipment.Cable),
        )
        assertEquals(listOf("cab"), ids(standard, cat, context(equipment = setOf(Equipment.Cable))))
        assertEquals(2, ids(standard, cat).size)
    }

    @Test
    fun customExercisesAreIncluded_butTimedOnesAndNonStrengthAreNot() {
        val cat = listOf(
            exercise("mine", MuscleGroup.Chest, custom = true),
            exercise("timed", MuscleGroup.Chest, custom = true, metric = ExerciseMetricType.Duration),
            exercise("plank-like", MuscleGroup.Chest, countsAsStrength = false),
        )
        assertEquals(listOf("mine"), ids(standard, cat))
    }

    @Test
    fun noDiscomfortFilter_anExerciseIsListedWhateverTheUserFelt() {
        // The picker has no discomfort input at all: every eligible exercise is listed.
        val cat = listOf(exercise("sore", MuscleGroup.Chest))
        assertEquals(listOf("sore"), ids(standard, cat))
    }

    @Test
    fun pickerOrder_favourite_thenSessions_thenEquipmentOrder_thenName() {
        val cat = listOf(
            exercise("c", MuscleGroup.Chest, equipment = Equipment.Cable, name = "b"),
            exercise("m", MuscleGroup.Chest, equipment = Equipment.Machine, name = "b"),
            exercise("fav", MuscleGroup.Chest, equipment = Equipment.Band, name = "z"),
            exercise("busy", MuscleGroup.Chest, equipment = Equipment.Band, name = "y"),
            exercise("d2", MuscleGroup.Chest, equipment = Equipment.Dumbbell, name = "b"),
            exercise("d1", MuscleGroup.Chest, equipment = Equipment.Dumbbell, name = "a"),
        )
        val ctx = context(
            history = mapOf("busy" to ExerciseHistory(5, null, null)),
            favourites = setOf("fav"),
        )
        assertEquals(listOf("fav", "busy", "d1", "d2", "m", "c"), ids(standard, cat, ctx))
    }

    @Test
    fun strengthMainSlot_acceptsMainLiftPatternsOnly() {
        val cat = listOf(
            exercise("bench", MuscleGroup.Chest, MovementPattern.HorizontalPush),
            exercise("fly", MuscleGroup.Chest, MovementPattern.Isolation),
            exercise("nopattern", MuscleGroup.Chest, null),
        )
        assertEquals(listOf("bench"), ids(main, cat))
        assertTrue(SlotEligibility.hasMainLift(MuscleGroup.Chest, cat, context()))
    }

    @Test
    fun noMainLiftInTheEquipment_hasMainLiftIsFalse() {
        val cat = listOf(exercise("fly", MuscleGroup.Chest, MovementPattern.Isolation))
        assertFalse(SlotEligibility.hasMainLift(MuscleGroup.Chest, cat, context()))
    }

    @Test
    fun repeatInTheSameRoutine_isListedDisabled_andAllowedElsewhere() {
        val cat = listOf(exercise("a", MuscleGroup.Chest), exercise("b", MuscleGroup.Chest))
        val sameRoutine = SlotEligibility.picker(standard, cat, context(), setOf("a"))
        assertEquals(listOf("a" to true, "b" to false), sameRoutine.map { it.exercise.id to it.disabled })
        val otherDay = SlotEligibility.picker(standard, cat, context(), emptySet())
        assertTrue(otherDay.none { it.disabled })
    }

    @Test
    fun extraAcceptsAnyGroup_exceptCardio() {
        val extra = SectionPrescription.extraSlot(MuscleGroup.Biceps, null)
        val cat = listOf(
            exercise("curl", MuscleGroup.Biceps),
            exercise("calf", MuscleGroup.Calves),
            exercise("chest", MuscleGroup.Chest),
            exercise("bike", MuscleGroup.FullBody, MovementPattern.Cardio),
        )
        assertEquals(setOf("curl", "calf", "chest"), ids(extra, cat).toSet())
    }
}
