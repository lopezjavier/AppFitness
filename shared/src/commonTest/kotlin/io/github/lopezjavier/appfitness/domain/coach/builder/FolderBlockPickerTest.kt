package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderBlockPickerTest {
    private fun fe(
        id: String,
        group: MuscleGroup,
        sets: Int = 5,
        pattern: MovementPattern? = MovementPattern.Isolation,
        counts: Boolean = true,
    ) = FolderBlockExercise(id, group, emptyList(), counts, pattern, Equipment.Machine, sets, 8, 12, 8.0, 120, null)

    private fun day(suffix: String, sets: Int = 5, skip: MuscleGroup? = null) = FolderBlockRoutine(
        "R$suffix",
        SplitCatalog.ALL_GROUPS.filter { it != skip }.map { fe("${it.name.lowercase()}-$suffix", it, sets) },
    )

    private fun draft(vararg routines: FolderBlockRoutine, days: Int = routines.size) =
        FolderBlockDraft(routines.toList(), TrainingIntention.Muscle, days, emptySet())

    @Test
    fun addableGroups_listsOnlyUnmetGroupsWithTheirMissingSets() {
        val d = draft(day("1", skip = MuscleGroup.Hamstrings), day("2", sets = 5))
        val groups = d.addableGroups()
        assertEquals(listOf(MuscleGroup.Hamstrings), groups.map { it.group })
        assertTrue(groups.single().missingSets > 0)
    }

    @Test
    fun addableGroups_isEmptyAtOneHundredPercent() {
        assertTrue(draft(day("1"), day("2")).addableGroups().isEmpty())
    }

    @Test
    fun pickerSlot_forAnUnmetGroup_usesTheValuesAddWouldUse() {
        val d = draft(day("1", skip = MuscleGroup.Hamstrings), day("2", skip = MuscleGroup.Hamstrings))
        val slot = d.pickerSlot(MuscleGroup.Hamstrings, 0)
        assertEquals(MuscleGroup.Hamstrings, slot?.group)
        assertEquals(8, slot?.repMin)
        assertEquals(12, slot?.repMax)
        assertEquals(true, (slot?.sets ?: 0) in 2..5)
    }

    @Test
    fun pickerSlot_forAMissingRoutine_isNull() {
        assertEquals(null, draft(day("1"), day("2")).pickerSlot(MuscleGroup.Chest, 7))
    }

    @Test
    fun swapSlot_keepsTheGroupSetsAndReps_ofTheSwappedRow() {
        val d = draft(day("1", sets = 3), day("2"))
        val slot = d.swapSlot(0, 1)
        assertEquals(d.routines[0].exercises[1].muscleGroup, slot?.group)
        assertEquals(3, slot?.sets)
        assertEquals(8, slot?.repMin)
        assertEquals(null, d.swapSlot(0, 99))
    }

    @Test
    fun setsOverTen_blocksTheSave_butTenDoesNot() {
        val ok = draft(day("1"), day("2"))
        assertTrue(ok.status().canSave)
        val ten = ok.copy(
            routines = ok.routines.map { r ->
                r.copy(
                    exercises = r.exercises.mapIndexed { i, e ->
                        if (i ==
                            0
                        ) {
                            e.copy(sets = 10)
                        } else {
                            e
                        }
                    },
                )
            },
        )
        assertTrue(ten.status().canSave)
        val eleven = ten.copy(
            routines = ten.routines.map { r ->
                r.copy(
                    exercises = r.exercises.mapIndexed { i, e ->
                        if (i ==
                            0
                        ) {
                            e.copy(sets = 11)
                        } else {
                            e
                        }
                    },
                )
            },
        )
        val status = eleven.status()
        assertFalse(status.canSave)
        assertTrue(status.gate.any { it is GateIssue.SetsOverLimit && it.routineIndex == 0 })
    }

    @Test
    fun leftOutRoutines_namesTheRoutinesWithoutLiftingExercises_andDaysIgnoreThem() {
        val stretch = FolderBlockRoutine("Estirar", listOf(fe("stretch", MuscleGroup.Back, counts = false)))
        val d = draft(day("1"), day("2"), stretch, days = 2)
        val status = d.status()
        assertEquals(listOf("Estirar"), status.leftOutRoutines)
        assertEquals(DaysCheck.Ok, status.days)
        assertTrue(draft(day("1"), day("2")).status().leftOutRoutines.isEmpty())
    }
}

class FolderBlockNameTest {
    @Test
    fun freeName_isTheFolderNameWithTheBlockSuffix() {
        assertEquals("Push Pull · bloque", FolderBlockName.unique("Push Pull", emptySet()))
    }

    @Test
    fun takenName_getsTwoThenThree() {
        assertEquals("A · bloque 2", FolderBlockName.unique("A", setOf("A · bloque")))
        assertEquals("A · bloque 3", FolderBlockName.unique("A", setOf("A · bloque", "A · bloque 2")))
    }

    @Test
    fun comparison_ignoresCaseAndSurroundingSpaces() {
        assertEquals("A · bloque 2", FolderBlockName.unique(" A ", setOf("a · BLOQUE ")))
    }

    @Test
    fun blankFolderName_stillProducesANonBlankName() {
        assertEquals("Carpeta · bloque", FolderBlockName.unique("  ", emptySet()))
    }
}

class FolderBlockOpsTest {
    private fun fe(id: String, group: MuscleGroup, sets: Int = 5) = FolderBlockExercise(
        id, group, emptyList(), true, MovementPattern.Isolation, Equipment.Machine, sets, 8, 12, 8.0, 120, null,
    )

    private fun base() = FolderBlockDraft(
        listOf(
            FolderBlockRoutine("A", SplitCatalog.ALL_GROUPS.map { fe("${it.name.lowercase()}-a", it) }),
        ),
        TrainingIntention.Muscle,
        1,
        emptySet(),
    )

    @Test
    fun encodeDecode_roundTrips() {
        val ops = listOf(
            FolderBlockOp.Add(0, "x1"),
            FolderBlockOp.Swap(0, 1, "x2"),
            FolderBlockOp.RepFix(0, 2),
            FolderBlockOp.Remove(0, 6),
        )
        assertEquals(ops, FolderBlockOps.decode(FolderBlockOps.encode(ops)))
        assertEquals("", FolderBlockOps.encode(emptyList()))
        assertEquals(emptyList(), FolderBlockOps.decode(""))
    }

    @Test
    fun decode_ignoresInvalidOps() {
        val ops = FolderBlockOps.decode("add 0 x1;bogus;swap a b c;rep 1;remove 0 2")
        assertEquals(listOf(FolderBlockOp.Add(0, "x1"), FolderBlockOp.Remove(0, 2)), ops)
    }

    @Test
    fun replay_equalsTheLiveDraft() {
        val cat = catalogue()
        val start = base().copy(
            routines = base().routines.map { r ->
                r.copy(exercises = r.exercises.filter { it.muscleGroup != MuscleGroup.Hamstrings })
            },
        )
        val add = cat.first { it.id == "hamstrings-iso" }
        val swapTo = cat.first { it.id == "chest-iso2" }
        val live = start.add(0, add, null).swap(0, 0, swapTo, null)
        val ops = listOf(
            FolderBlockOp.Add(0, "hamstrings-iso"),
            FolderBlockOp.Swap(0, 0, "chest-iso2"),
            FolderBlockOp.Remove(0, 0),
        )
        // The remove targets a folder row (not added), so it is ignored like the live draft would.
        assertEquals(live, FolderBlockOps.replay(start, ops, cat, emptyMap()))
    }

    @Test
    fun replay_withAnUnknownExercise_ignoresThatOp() {
        val replayed = FolderBlockOps.replay(base(), listOf(FolderBlockOp.Add(0, "nope")), emptyList(), emptyMap())
        assertEquals(base(), replayed)
    }
}
