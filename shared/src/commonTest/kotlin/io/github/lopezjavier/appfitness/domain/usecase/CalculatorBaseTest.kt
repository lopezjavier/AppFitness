package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.Equipment
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorBaseTest {
    private val settings = CalculatorSettings(barWeightKg = 15.0, dumbbellHandleKg = 1.5)

    @Test
    fun dumbbell_usesTheHandle() {
        assertEquals(1.5, CalculatorBase.forEquipment(Equipment.Dumbbell, settings))
    }

    @Test
    fun barbell_usesTheSelectedBar() {
        assertEquals(15.0, CalculatorBase.forEquipment(Equipment.Barbell, settings))
    }

    @Test
    fun otherOrUnknownEquipment_usesTheSelectedBar() {
        assertEquals(15.0, CalculatorBase.forEquipment(Equipment.Machine, settings))
        assertEquals(15.0, CalculatorBase.forEquipment(null, settings))
    }

    @Test
    fun defaultHandleIsOneKilo() {
        assertEquals(1.0, CalculatorSettings().dumbbellHandleKg)
    }
}
