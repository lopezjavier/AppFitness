package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.Equipment

/** The "bar" the calculators start from (ADR-55): the handle for dumbbells, the selected bar otherwise. */
object CalculatorBase {
    fun forEquipment(equipment: Equipment?, settings: CalculatorSettings): Double =
        if (equipment == Equipment.Dumbbell) settings.dumbbellHandleKg else settings.barWeightKg
}
