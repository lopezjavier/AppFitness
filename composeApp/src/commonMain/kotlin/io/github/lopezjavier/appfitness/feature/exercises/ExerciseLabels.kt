package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_band
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_barbell
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_bodyweight
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_cable
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_dumbbell
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_kettlebell
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_machine
import io.github.lopezjavier.appfitness.composeapp.resources.equipment_other
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_carry
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_core
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_hinge
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_horizontal_pull
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_horizontal_push
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_isolation
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_lunge
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_other
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_squat
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_vertical_pull
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_vertical_push
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_back
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_biceps
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_calves
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_chest
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_core
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_forearms
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_full_body
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_glutes
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_hamstrings
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_hamstrings_short
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_quads
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_shoulders
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_triceps
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.jetbrains.compose.resources.StringResource

/** Short axis label for the statistics radar only ("Isquios"); everywhere else [label] applies. */
internal val MuscleGroup.radarLabel: StringResource
    get() = if (this == MuscleGroup.Hamstrings) Res.string.muscle_hamstrings_short else label

/** Spanish label of this muscle group. */
internal val MuscleGroup.label: StringResource
    get() =
        when (this) {
            MuscleGroup.Chest -> Res.string.muscle_chest
            MuscleGroup.Back -> Res.string.muscle_back
            MuscleGroup.Shoulders -> Res.string.muscle_shoulders
            MuscleGroup.Biceps -> Res.string.muscle_biceps
            MuscleGroup.Triceps -> Res.string.muscle_triceps
            MuscleGroup.Forearms -> Res.string.muscle_forearms
            MuscleGroup.Quads -> Res.string.muscle_quads
            MuscleGroup.Hamstrings -> Res.string.muscle_hamstrings
            MuscleGroup.Glutes -> Res.string.muscle_glutes
            MuscleGroup.Calves -> Res.string.muscle_calves
            MuscleGroup.Core -> Res.string.muscle_core
            MuscleGroup.FullBody -> Res.string.muscle_full_body
        }

/** Spanish label of this equipment. */
internal val Equipment.label: StringResource
    get() =
        when (this) {
            Equipment.Barbell -> Res.string.equipment_barbell
            Equipment.Dumbbell -> Res.string.equipment_dumbbell
            Equipment.Machine -> Res.string.equipment_machine
            Equipment.Cable -> Res.string.equipment_cable
            Equipment.Bodyweight -> Res.string.equipment_bodyweight
            Equipment.Kettlebell -> Res.string.equipment_kettlebell
            Equipment.Band -> Res.string.equipment_band
            Equipment.Other -> Res.string.equipment_other
        }

/** Spanish label of this movement pattern. */
internal val MovementPattern.label: StringResource
    get() =
        when (this) {
            MovementPattern.Squat -> Res.string.movement_pattern_squat
            MovementPattern.Hinge -> Res.string.movement_pattern_hinge
            MovementPattern.Lunge -> Res.string.movement_pattern_lunge
            MovementPattern.HorizontalPush -> Res.string.movement_pattern_horizontal_push
            MovementPattern.VerticalPush -> Res.string.movement_pattern_vertical_push
            MovementPattern.HorizontalPull -> Res.string.movement_pattern_horizontal_pull
            MovementPattern.VerticalPull -> Res.string.movement_pattern_vertical_pull
            MovementPattern.Carry -> Res.string.movement_pattern_carry
            MovementPattern.Core -> Res.string.movement_pattern_core
            MovementPattern.Isolation -> Res.string.movement_pattern_isolation
            MovementPattern.Cardio -> Res.string.movement_pattern_cardio
            MovementPattern.Other -> Res.string.movement_pattern_other
        }

/** Test-tag suffix of an enum value: `FullBody` gives `full_body`. */
internal fun Enum<*>.tagName(): String = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
