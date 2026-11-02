package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultEquipmentTest {
    @Test
    fun home_isDumbbellBothBandsAndBodyweight() {
        assertEquals(
            setOf(Equipment.Dumbbell, Equipment.Band, Equipment.BandLong, Equipment.Bodyweight),
            DefaultEquipment.forPlace(TrainingPlace.Home),
        )
    }

    @Test
    fun gym_isEveryEquipment() {
        val gym = DefaultEquipment.forPlace(TrainingPlace.Gym)

        assertEquals(11, gym.size)
        assertEquals(Equipment.entries.toSet(), gym)
        assertTrue(Equipment.BandLong in gym)
    }

    @Test
    fun gym_includesTheEzBarAndTheCardioMachine() {
        val gym = DefaultEquipment.forPlace(TrainingPlace.Gym)

        assertEquals(listOf(Equipment.CardioMachine), Equipment.cardio)
        assertTrue(gym.containsAll(Equipment.cardio))
        assertTrue(Equipment.EzBar in gym)
    }

    @Test
    fun homeAndOther_includeNoCardioMachine() {
        assertTrue(DefaultEquipment.forPlace(TrainingPlace.Home).none { it.isCardio })
        assertTrue(DefaultEquipment.forPlace(TrainingPlace.Other).none { it.isCardio })
    }

    @Test
    fun other_doesNotIncludeLongBand() {
        assertFalse(Equipment.BandLong in DefaultEquipment.forPlace(TrainingPlace.Other))
    }

    @Test
    fun other_isBodyweightOnly() {
        assertEquals(setOf(Equipment.Bodyweight), DefaultEquipment.forPlace(TrainingPlace.Other))
    }

    @Test
    fun sessionMinutes_acceptOnlyMultiplesOfFiveBetween15And180() {
        assertTrue(MyTraining.isValidSessionMinutes(15))
        assertTrue(MyTraining.isValidSessionMinutes(180))
        assertTrue(MyTraining.isValidSessionMinutes(null))
        assertFalse(MyTraining.isValidSessionMinutes(10))
        assertFalse(MyTraining.isValidSessionMinutes(185))
        assertFalse(MyTraining.isValidSessionMinutes(47))
        assertEquals(34, MyTraining.SESSION_MINUTES.count())
    }

    @Test
    fun days_acceptOneToSeven() {
        assertTrue(MyTraining.isValidDays(1))
        assertTrue(MyTraining.isValidDays(7))
        assertTrue(MyTraining.isValidDays(null))
        assertFalse(MyTraining.isValidDays(0))
        assertFalse(MyTraining.isValidDays(8))
    }
}
