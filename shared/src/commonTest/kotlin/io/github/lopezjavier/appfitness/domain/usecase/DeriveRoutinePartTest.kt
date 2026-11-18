package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import kotlin.test.Test
import kotlin.test.assertEquals

class DeriveRoutinePartTest {
    private val derive = DeriveRoutinePart()

    private fun ex(
        group: MuscleGroup,
        strength: Boolean = true,
        pattern: MovementPattern? = null,
        equipment: Equipment = Equipment.Barbell,
        custom: Boolean = false,
    ) = Exercise(
        id = "e-$group-$pattern-$strength",
        key = if (custom) null else "k-$group",
        name = if (custom) "Mio" else null,
        muscleGroup = group,
        secondaryMuscles = emptyList(),
        equipment = equipment,
        metricType = ExerciseMetricType.WeightReps,
        isCustom = custom,
        movementPattern = pattern,
        countsAsStrength = strength,
    )

    private fun byName(name: String) = derive(name, emptyList())

    private fun byExercises(vararg groups: MuscleGroup) = derive("Mi rutina", groups.map { ex(it) })

    private fun assertNames(part: RoutinePart, vararg names: String) =
        names.forEach { assertEquals(part, byName(it), "name: $it") }

    // ---- Name rule, one test per part ----

    @Test
    fun fullBodyWords() =
        assertNames(RoutinePart.FullBody, "Cuerpo completo", "todo el cuerpo", "Full body", "fullbody", "Total")

    @Test
    fun upperWords() =
        assertNames(RoutinePart.Upper, "Torso", "Tren superior", "Superior", "Upper", "Hombros", "Hombro")

    @Test
    fun lowerWords() = assertNames(
        RoutinePart.Lower,
        "Pierna",
        "Piernas",
        "Tren inferior",
        "Cuádriceps",
        "Femoral",
        "Isquios",
        "Gemelos",
        "Legs",
    )

    @Test
    fun pushWords() = assertNames(RoutinePart.Push, "Empuje", "Push", "Pecho", "Chest")

    @Test
    fun pullWords() = assertNames(RoutinePart.Pull, "Tirón", "Pull", "Espalda", "Back", "Dorsal")

    @Test
    fun armsWords() = assertNames(RoutinePart.Arms, "Brazos", "Brazo", "Bíceps", "Tríceps", "Antebrazo", "Arms")

    @Test
    fun glutesWords() = assertNames(RoutinePart.Glutes, "Glúteos", "Glúteo", "Glute", "Glutes")

    @Test
    fun coreWords() = assertNames(RoutinePart.Core, "Core", "Abdominales", "Abdomen", "Abs")

    @Test
    fun cardioWords() =
        assertNames(RoutinePart.Cardio, "Cardio", "Correr", "Carrera", "Running", "Bici", "Caminar", "Elíptica")

    @Test
    fun everyBuilderRoutineTypeNameGivesItsOwnPart() {
        val expected = mapOf(
            RoutineType.FullBody to RoutinePart.FullBody,
            RoutineType.Upper to RoutinePart.Upper,
            RoutineType.Lower to RoutinePart.Lower,
            RoutineType.Push to RoutinePart.Push,
            RoutineType.Pull to RoutinePart.Pull,
        )
        assertEquals(RoutineType.entries.toSet(), expected.keys)
        expected.forEach { (type, part) ->
            assertEquals(part, byName(type.displayName), type.displayName)
            assertEquals(part, byName(type.displayName + " A"), type.displayName + " A")
        }
    }

    // ---- Normalisation ----

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(RoutinePart.Pull, byName("tiron"))
        assertEquals(RoutinePart.Lower, byName("PIERNA"))
        assertEquals(RoutinePart.Lower, byName("PIERNA b"))
        assertEquals(RoutinePart.Pull, byName("tiron 2"))
    }

    @Test
    fun matchesDecomposedAccents() {
        assertEquals(RoutinePart.Pull, byName("Tirón"))
        assertEquals(RoutinePart.Upper, byName("Hombrós"))
        assertEquals(RoutinePart.Glutes, byName("Glúteos"))
    }

    @Test
    fun ignoresATrailingLetterOrNumber() {
        assertEquals(RoutinePart.Upper, byName("Torso A"))
        assertEquals(RoutinePart.Lower, byName("Pierna B"))
        assertEquals(RoutinePart.Push, byName("Empuje 2"))
    }

    @Test
    fun matchesWholeWordsOnly() {
        assertEquals(RoutinePart.Generic, byName("superiormente"))
        assertEquals(RoutinePart.Generic, byName("Corea"))
    }

    @Test
    fun extraSpacesAndPunctuationDoNotMatter() {
        assertEquals(RoutinePart.Lower, byName("  Pierna!!  "))
        assertEquals(RoutinePart.FullBody, byName("Cuerpo   completo - A"))
        assertEquals(RoutinePart.Upper, byName("Torso (lunes)"))
    }

    // ---- Several parts (07 rule 2) ----

    @Test
    fun lowerAndUpperPartsGiveFullBody() {
        assertEquals(RoutinePart.FullBody, byName("Pierna y espalda"))
        assertEquals(RoutinePart.FullBody, byName("Glúteo y pecho"))
    }

    @Test
    fun severalUpperPartsGiveUpper() {
        assertEquals(RoutinePart.Upper, byName("Pecho y hombros"))
        assertEquals(RoutinePart.Upper, byName("Pecho y espalda"))
    }

    @Test
    fun lowerAndGlutesGiveLower() = assertEquals(RoutinePart.Lower, byName("Glúteo y pierna"))

    @Test
    fun coreAndCardioGiveWayToTheOtherPart() {
        assertEquals(RoutinePart.Lower, byName("Core y pierna"))
        assertEquals(RoutinePart.Upper, byName("Cardio y torso"))
    }

    @Test
    fun coreAndCardioTogetherGiveCore() = assertEquals(RoutinePart.Core, byName("Core y cardio"))

    @Test
    fun aFullBodyWordAlwaysWins() = assertEquals(RoutinePart.FullBody, byName("Full body y pierna"))

    // ---- Exercise fallback (07 rule 3) ----

    @Test
    fun chestShouldersTricepsOnlyGivePush() =
        assertEquals(RoutinePart.Push, byExercises(MuscleGroup.Chest, MuscleGroup.Shoulders, MuscleGroup.Triceps))

    @Test
    fun backBicepsForearmsOnlyGivePull() =
        assertEquals(RoutinePart.Pull, byExercises(MuscleGroup.Back, MuscleGroup.Biceps, MuscleGroup.Forearms))

    @Test
    fun bicepsAndTricepsOnlyGiveArms() =
        assertEquals(RoutinePart.Arms, byExercises(MuscleGroup.Biceps, MuscleGroup.Triceps))

    @Test
    fun glutesWithHamstringsGiveGlutes() =
        assertEquals(RoutinePart.Glutes, byExercises(MuscleGroup.Glutes, MuscleGroup.Hamstrings))

    @Test
    fun glutesWithQuadsGiveLower() = assertEquals(RoutinePart.Lower, byExercises(MuscleGroup.Glutes, MuscleGroup.Quads))

    @Test
    fun quadsHamstringsCalvesGiveLower() =
        assertEquals(RoutinePart.Lower, byExercises(MuscleGroup.Quads, MuscleGroup.Hamstrings, MuscleGroup.Calves))

    @Test
    fun chestAndBackGiveUpper() = assertEquals(RoutinePart.Upper, byExercises(MuscleGroup.Chest, MuscleGroup.Back))

    @Test
    fun upperAndLowerGroupsGiveFullBody() =
        assertEquals(RoutinePart.FullBody, byExercises(MuscleGroup.Chest, MuscleGroup.Quads))

    @Test
    fun coreOnlyGivesCore() = assertEquals(RoutinePart.Core, byExercises(MuscleGroup.Core, MuscleGroup.Core))

    @Test
    fun allCardioGivesCardio() {
        val byPattern = listOf(ex(MuscleGroup.FullBody, pattern = MovementPattern.Cardio))
        val byEquipment = listOf(ex(MuscleGroup.FullBody, equipment = Equipment.CardioMachine))
        assertEquals(RoutinePart.Cardio, derive("Mi rutina", byPattern))
        assertEquals(RoutinePart.Cardio, derive("Mi rutina", byEquipment))
    }

    @Test
    fun exercisesThatDoNotCountAsStrengthAreIgnored() {
        val exercises = listOf(ex(MuscleGroup.Chest), ex(MuscleGroup.Quads, strength = false))
        assertEquals(RoutinePart.Push, derive("Mi rutina", exercises))
    }

    @Test
    fun coreCalvesForearmsAdductorsAreIgnoredUnlessTheOnlyOnes() {
        assertEquals(RoutinePart.Push, byExercises(MuscleGroup.Chest, MuscleGroup.Core, MuscleGroup.Forearms))
        assertEquals(RoutinePart.Lower, byExercises(MuscleGroup.Quads, MuscleGroup.Core, MuscleGroup.Calves))
        assertEquals(RoutinePart.Push, byExercises(MuscleGroup.Chest, MuscleGroup.Calves))
        assertEquals(RoutinePart.Lower, byExercises(MuscleGroup.Calves))
        assertEquals(RoutinePart.Arms, byExercises(MuscleGroup.Forearms))
        assertEquals(RoutinePart.Lower, byExercises(MuscleGroup.Adductors))
    }

    @Test
    fun customExercisesAreClassifiedByTheirMuscleGroup() {
        val exercises = listOf(ex(MuscleGroup.Back, custom = true), ex(MuscleGroup.Biceps, custom = true))
        assertEquals(RoutinePart.Pull, derive("Mi rutina", exercises))
    }

    // ---- Boundaries ----

    @Test
    fun nothingToClassifyGivesGeneric() {
        assertEquals(RoutinePart.Generic, derive("", emptyList()))
        assertEquals(RoutinePart.Generic, derive("   ", emptyList()))
        assertEquals(RoutinePart.Generic, derive("Mi rutina", emptyList()))
    }

    @Test
    fun onlyUnclassifiableExercisesGiveGeneric() {
        assertEquals(RoutinePart.Generic, byExercises(MuscleGroup.FullBody, MuscleGroup.FullBody))
        assertEquals(RoutinePart.Generic, derive("Mi rutina", listOf(ex(MuscleGroup.Chest, strength = false))))
    }

    @Test
    fun aVeryLongNameDoesNotThrow() {
        assertEquals(RoutinePart.Generic, byName("x".repeat(10_000)))
        assertEquals(RoutinePart.Lower, byName("rutina ".repeat(500) + "pierna"))
    }

    @Test
    fun theNameWinsOverConflictingExercises() {
        val exercises = listOf(ex(MuscleGroup.Chest), ex(MuscleGroup.Triceps))
        assertEquals(RoutinePart.Lower, derive("Pierna", exercises))
    }
}
