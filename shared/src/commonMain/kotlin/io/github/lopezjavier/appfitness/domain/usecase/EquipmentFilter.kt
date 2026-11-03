package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences

/**
 * The equipment rule of the Trainer swaps (`04` §9.4, ADR-92), mirroring the SQL `coach_user_equipment()` and
 * `equipment_allowed()`. Pure: no I/O.
 */
object EquipmentFilter {
    /** Catalogue keys that need no equipment at all, so they are always allowed. */
    private val alwaysAllowedKeys = setOf("walking", "running")

    /**
     * The equipment the user has, as the server resolves it: the saved set; empty at home or elsewhere means only
     * bodyweight; empty at the gym (or without a place) means everything (null).
     */
    fun selectedFrom(equipment: Set<Equipment>, place: TrainingPlace?): Set<Equipment>? = when {
        equipment.isNotEmpty() -> equipment
        place == TrainingPlace.Home || place == TrainingPlace.Other -> setOf(Equipment.Bodyweight)
        else -> null
    }

    fun selectedFrom(preferences: TrainingPreferences): Set<Equipment>? =
        selectedFrom(preferences.equipment, preferences.place)

    /** Whether [exercise] can be offered with [selected] equipment (null = everything). */
    fun allows(exercise: Exercise, selected: Set<Equipment>?): Boolean = when {
        selected == null -> true
        exercise.equipment == Equipment.Bodyweight -> true
        exercise.key in alwaysAllowedKeys -> true
        else -> exercise.equipment in selected
    }

    fun filter(alternatives: List<ExerciseAlternative>, selected: Set<Equipment>?): List<ExerciseAlternative> =
        if (selected == null) alternatives else alternatives.filter { allows(it.exercise, selected) }
}
