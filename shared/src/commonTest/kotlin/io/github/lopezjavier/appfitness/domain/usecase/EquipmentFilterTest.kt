package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EquipmentFilterTest {
    private fun exercise(id: String, key: String?, equipment: Equipment, custom: Boolean = false) = Exercise(
        id,
        key,
        if (custom) "Custom" else null,
        MuscleGroup.Chest,
        emptyList(),
        equipment,
        ExerciseMetricType.WeightReps,
        custom,
    )

    private fun alt(id: String, key: String?, equipment: Equipment, custom: Boolean = false) =
        ExerciseAlternative(exercise(id, key, equipment, custom), 1.0)

    private val dumbbellOnly = setOf(Equipment.Dumbbell)

    @Test
    fun cableEzBarAndMachine_areExcludedWhenUnselected() {
        val list = listOf(
            alt("1", "fly", Equipment.Cable),
            alt("2", "curl", Equipment.EzBar),
            alt("3", "press", Equipment.Machine),
            alt("4", "db", Equipment.Dumbbell),
        )

        assertEquals(listOf("4"), EquipmentFilter.filter(list, dumbbellOnly).map { it.exercise.id })
    }

    @Test
    fun selectedEquipment_isKept() {
        val list = listOf(alt("1", "fly", Equipment.Cable), alt("2", "curl", Equipment.EzBar))

        val kept = EquipmentFilter.filter(list, setOf(Equipment.Cable, Equipment.EzBar))

        assertEquals(listOf("1", "2"), kept.map { it.exercise.id })
    }

    @Test
    fun noneSelectedAtTheGym_offersEverything() {
        val selected = EquipmentFilter.selectedFrom(emptySet(), TrainingPlace.Gym)
        val list = listOf(alt("1", "fly", Equipment.Cable), alt("2", "press", Equipment.Machine))

        assertNull(selected)
        assertEquals(2, EquipmentFilter.filter(list, selected).size)
    }

    @Test
    fun noneSelectedWithoutPlace_offersEverything() {
        assertNull(EquipmentFilter.selectedFrom(emptySet(), null))
    }

    @Test
    fun noneSelectedAtHomeOrOther_meansBodyweightOnly() {
        val list = listOf(alt("1", "fly", Equipment.Cable), alt("2", "pushup", Equipment.Bodyweight))

        listOf(TrainingPlace.Home, TrainingPlace.Other).forEach { place ->
            val selected = EquipmentFilter.selectedFrom(emptySet(), place)
            assertEquals(setOf(Equipment.Bodyweight), selected)
            assertEquals(listOf("2"), EquipmentFilter.filter(list, selected).map { it.exercise.id })
        }
    }

    @Test
    fun selectedFromPreferences_usesTheirEquipmentAndPlace() {
        val prefs = TrainingPreferences(place = TrainingPlace.Gym, equipment = dumbbellOnly)

        assertEquals(dumbbellOnly, EquipmentFilter.selectedFrom(prefs))
        assertNull(EquipmentFilter.selectedFrom(TrainingPreferences(place = TrainingPlace.Gym)))
    }

    @Test
    fun bodyweight_isAlwaysAllowed() {
        assertTrue(EquipmentFilter.allows(exercise("1", "pushup", Equipment.Bodyweight), dumbbellOnly))
    }

    @Test
    fun walkingAndRunning_areAlwaysAllowed() {
        assertTrue(EquipmentFilter.allows(exercise("1", "walking", Equipment.CardioMachine), dumbbellOnly))
        assertTrue(EquipmentFilter.allows(exercise("2", "running", Equipment.CardioMachine), dumbbellOnly))
    }

    @Test
    fun otherCardioMachines_needTheCardioMachineItem() {
        val bike = exercise("1", "stationary-bike", Equipment.CardioMachine)

        assertFalse(EquipmentFilter.allows(bike, dumbbellOnly))
        assertTrue(EquipmentFilter.allows(bike, setOf(Equipment.CardioMachine)))
    }

    @Test
    fun customExercise_followsTheSameTest() {
        val custom = alt("1", null, Equipment.Cable, custom = true)

        assertTrue(EquipmentFilter.filter(listOf(custom), dumbbellOnly).isEmpty())
        assertEquals(1, EquipmentFilter.filter(listOf(custom), setOf(Equipment.Cable)).size)
    }
}
