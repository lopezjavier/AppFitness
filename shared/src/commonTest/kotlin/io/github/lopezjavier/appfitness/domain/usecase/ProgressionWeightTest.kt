package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressionWeightTest {
    private val smallest25 = CalculatorSettings(plateKg = listOf(25.0, 10.0, 5.0, 2.5))
    private val smallest125 = CalculatorSettings(plateKg = listOf(25.0, 5.0, 2.5, 1.25))

    private fun suggestion(action: ProgressionAction, base: Double) =
        ProgressionSuggestion("e1", action, base, 6, ProgressionReason.BelowMax, emptyList())

    private fun shown(action: ProgressionAction, base: Double, settings: CalculatorSettings, eq: Equipment? = null) =
        ProgressionWeight.apply(suggestion(action, base), eq, settings).weightKg

    @Test
    fun raise_addsTwiceTheSmallestPlate() {
        assertEquals(85.0, shown(ProgressionAction.RaiseWeight, 80.0, smallest25))
    }

    @Test
    fun raise_withAOnePointTwoFivePlateAddsTwoAndAHalf() {
        assertEquals(82.5, shown(ProgressionAction.RaiseWeight, 80.0, smallest125))
    }

    @Test
    fun raise_onADumbbellAddsTheHandleStep() {
        val settings = CalculatorSettings(dumbbellHandleKg = 2.0)
        assertEquals(22.0, shown(ProgressionAction.RaiseWeight, 20.0, settings, Equipment.Dumbbell))
    }

    @Test
    fun raise_onAMachineUsesThePlateStep() {
        assertEquals(65.0, shown(ProgressionAction.RaiseWeight, 60.0, smallest25, Equipment.Machine))
    }

    @Test
    fun lower_dropsFivePercentRoundedToTheStep() {
        assertEquals(75.0, shown(ProgressionAction.LowerWeight, 80.0, smallest25))
    }

    @Test
    fun lower_withASmallerStepKeepsTheFinerValue() {
        assertEquals(77.5, shown(ProgressionAction.LowerWeight, 82.0, smallest125))
    }

    @Test
    fun lower_neverStaysAtTheBaseWeight() {
        // 5 % of 20 kg is 1 kg, which rounds back to 20 with a 5 kg step: it must still go down one step.
        assertEquals(15.0, shown(ProgressionAction.LowerWeight, 20.0, smallest25))
    }

    @Test
    fun lower_neverGoesBelowZero() {
        assertEquals(0.0, shown(ProgressionAction.LowerWeight, 2.0, smallest25))
    }

    @Test
    fun addRep_keepsTheBaseWeight() {
        assertEquals(80.0, shown(ProgressionAction.AddRep, 80.0, smallest25))
    }

    @Test
    fun apply_keepsGoalRepsAndReason() {
        val result = ProgressionWeight.apply(suggestion(ProgressionAction.RaiseWeight, 80.0), null, smallest25)
        assertEquals(6, result.goalReps)
        assertEquals(ProgressionReason.BelowMax, result.reason)
    }
}
