package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace

/** The equipment a training place preselects (US-65). */
object DefaultEquipment {
    fun forPlace(place: TrainingPlace): Set<Equipment> = when (place) {
        TrainingPlace.Home -> setOf(Equipment.Dumbbell, Equipment.Band, Equipment.Bodyweight)
        TrainingPlace.Gym -> Equipment.entries.toSet()
        TrainingPlace.Other -> setOf(Equipment.Bodyweight)
    }
}
