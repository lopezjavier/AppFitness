package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.RoutinePart

/**
 * The training part of a routine, for its icon (`07` "Routine part, for the routine icon"). Pure and total: the name
 * words first (rule 2; the builder's day-type names are in the table, which stands in for rule 1 because no routine
 * stores its day type), then the primary muscles of the exercises (rule 3), else [RoutinePart.Generic] (rule 4).
 */
class DeriveRoutinePart {
    operator fun invoke(name: String, exercises: List<Exercise>): RoutinePart =
        fromName(name) ?: fromExercises(exercises)

    private fun fromName(name: String): RoutinePart? {
        val tokens = tokensOf(name)
        val matched = mutableSetOf<RoutinePart>()
        var index = 0
        while (index < tokens.size) {
            val hit = NAME_WORDS.firstOrNull { (words, _) -> tokens.startsWithAt(index, words) }
            if (hit == null) {
                index++
            } else {
                matched += hit.second
                index += hit.first.size
            }
        }
        return combine(matched)
    }

    /** Rule 2 for the parts found in a name; null when none. */
    private fun combine(matched: Set<RoutinePart>): RoutinePart? {
        val others = matched - setOf(RoutinePart.Core, RoutinePart.Cardio)
        return when {
            matched.isEmpty() -> null
            RoutinePart.FullBody in matched -> RoutinePart.FullBody
            others.isEmpty() -> if (RoutinePart.Core in matched) RoutinePart.Core else RoutinePart.Cardio
            others.size == 1 -> others.single()
            others.any { it in LOWER_PARTS } && others.any { it in UPPER_PARTS } -> RoutinePart.FullBody
            others.all { it in UPPER_PARTS } -> RoutinePart.Upper
            else -> RoutinePart.Lower
        }
    }

    private fun fromExercises(exercises: List<Exercise>): RoutinePart {
        if (exercises.isNotEmpty() && exercises.all { it.isCardio() }) return RoutinePart.Cardio
        val primaries = exercises.filter { it.countsAsStrength && !it.isCardio() }
            .map { it.muscleGroup }
            .filter { it != MuscleGroup.FullBody }
            .toSet()
        val main = primaries - IGNORED_UNLESS_ALONE
        return classify(main.ifEmpty { primaries })
    }

    private fun classify(groups: Set<MuscleGroup>): RoutinePart {
        val hasUpper = groups.any { it in UPPER_GROUPS }
        val hasLower = groups.any { it in LOWER_GROUPS }
        return when {
            groups.isEmpty() -> RoutinePart.Generic

            groups == setOf(MuscleGroup.Core) -> RoutinePart.Core

            hasUpper && hasLower -> RoutinePart.FullBody

            hasLower -> if (MuscleGroup.Glutes in groups && MuscleGroup.Quads !in groups) {
                RoutinePart.Glutes
            } else {
                RoutinePart.Lower
            }

            groups.all { it in ARMS_GROUPS } -> RoutinePart.Arms

            groups.all { it in PUSH_GROUPS } -> RoutinePart.Push

            groups.all { it in PULL_GROUPS } -> RoutinePart.Pull

            hasUpper -> RoutinePart.Upper

            else -> RoutinePart.Generic
        }
    }

    private fun Exercise.isCardio() = movementPattern == MovementPattern.Cardio || equipment.isCardio

    private fun tokensOf(name: String): List<String> {
        // Combining marks (a decomposed "o" + U+0301) are dropped; precomposed accents are mapped.
        val tokens = name.lowercase().filter { it !in COMBINING_MARKS }.map { ACCENTS[it] ?: it }
            .joinToString("") { if (it.isLetterOrDigit()) it.toString() else " " }
            .split(' ')
            .filter { it.isNotEmpty() }
        // A trailing single letter or number ("Torso A", "Pierna 2") is a label, not a word.
        return if (tokens.size > 1 && tokens.last().length == 1) tokens.dropLast(1) else tokens
    }

    private fun List<String>.startsWithAt(index: Int, words: List<String>): Boolean =
        index + words.size <= size && words.indices.all { this[index + it] == words[it] }

    private companion object {
        val COMBINING_MARKS = '̀'..'ͯ'
        val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u')

        val LOWER_PARTS = setOf(RoutinePart.Lower, RoutinePart.Glutes)
        val UPPER_PARTS = setOf(RoutinePart.Upper, RoutinePart.Push, RoutinePart.Pull, RoutinePart.Arms)

        val IGNORED_UNLESS_ALONE =
            setOf(MuscleGroup.Core, MuscleGroup.Calves, MuscleGroup.Forearms, MuscleGroup.Adductors)
        val UPPER_GROUPS = setOf(
            MuscleGroup.Chest,
            MuscleGroup.Back,
            MuscleGroup.Shoulders,
            MuscleGroup.Biceps,
            MuscleGroup.Triceps,
            MuscleGroup.Forearms,
        )
        val LOWER_GROUPS = setOf(
            MuscleGroup.Quads,
            MuscleGroup.Hamstrings,
            MuscleGroup.Glutes,
            MuscleGroup.Calves,
            MuscleGroup.Adductors,
        )
        val ARMS_GROUPS = setOf(MuscleGroup.Biceps, MuscleGroup.Triceps, MuscleGroup.Forearms)
        val PUSH_GROUPS = setOf(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Triceps)
        val PULL_GROUPS = setOf(MuscleGroup.Back, MuscleGroup.Biceps, MuscleGroup.Forearms)

        /** `07` closed list, accent-free; multi-word phrases are matched first by being listed before their words. */
        val NAME_WORDS: List<Pair<List<String>, RoutinePart>> = listOf(
            "cuerpo completo" to RoutinePart.FullBody,
            "todo el cuerpo" to RoutinePart.FullBody,
            "full body" to RoutinePart.FullBody,
            "fullbody" to RoutinePart.FullBody,
            "total" to RoutinePart.FullBody,
            "tren superior" to RoutinePart.Upper,
            "torso" to RoutinePart.Upper,
            "superior" to RoutinePart.Upper,
            "upper" to RoutinePart.Upper,
            "hombro" to RoutinePart.Upper,
            "hombros" to RoutinePart.Upper,
            "tren inferior" to RoutinePart.Lower,
            "pierna" to RoutinePart.Lower,
            "piernas" to RoutinePart.Lower,
            "inferior" to RoutinePart.Lower,
            "lower" to RoutinePart.Lower,
            "leg" to RoutinePart.Lower,
            "legs" to RoutinePart.Lower,
            "cuadriceps" to RoutinePart.Lower,
            "femoral" to RoutinePart.Lower,
            "isquios" to RoutinePart.Lower,
            "gemelos" to RoutinePart.Lower,
            "empuje" to RoutinePart.Push,
            "push" to RoutinePart.Push,
            "pecho" to RoutinePart.Push,
            "chest" to RoutinePart.Push,
            "tiron" to RoutinePart.Pull,
            "pull" to RoutinePart.Pull,
            "espalda" to RoutinePart.Pull,
            "back" to RoutinePart.Pull,
            "dorsal" to RoutinePart.Pull,
            "brazo" to RoutinePart.Arms,
            "brazos" to RoutinePart.Arms,
            "biceps" to RoutinePart.Arms,
            "triceps" to RoutinePart.Arms,
            "antebrazo" to RoutinePart.Arms,
            "arms" to RoutinePart.Arms,
            "gluteo" to RoutinePart.Glutes,
            "gluteos" to RoutinePart.Glutes,
            "glute" to RoutinePart.Glutes,
            "glutes" to RoutinePart.Glutes,
            "core" to RoutinePart.Core,
            "abdominales" to RoutinePart.Core,
            "abdomen" to RoutinePart.Core,
            "abs" to RoutinePart.Core,
            "cardio" to RoutinePart.Cardio,
            "correr" to RoutinePart.Cardio,
            "carrera" to RoutinePart.Cardio,
            "running" to RoutinePart.Cardio,
            "bici" to RoutinePart.Cardio,
            "caminar" to RoutinePart.Cardio,
            "eliptica" to RoutinePart.Cardio,
        ).map { (phrase, part) -> phrase.split(' ') to part }
    }
}
