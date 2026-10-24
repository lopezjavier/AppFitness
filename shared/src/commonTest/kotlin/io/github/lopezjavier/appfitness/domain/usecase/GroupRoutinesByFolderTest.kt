package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupRoutinesByFolderTest {
    private val legs = RoutineFolder("f1", "Pierna")
    private val push = RoutineFolder("f2", "Empuje")

    private fun routine(id: String, folder: String? = null) = RoutineSummary(id, id, 1, folder)

    @Test
    fun groupRoutines_putsFoldersFirstInFolderOrderAndTheNoFolderGroupLast() {
        val groups = groupRoutines(
            listOf(legs, push),
            listOf(routine("a"), routine("b", "f2"), routine("c", "f1"), routine("d", "f2")),
        )

        assertEquals(listOf<RoutineFolder?>(legs, push, null), groups.map { it.folder })
        assertEquals(listOf("c"), groups[0].routines.map { it.id })
        assertEquals(listOf("b", "d"), groups[1].routines.map { it.id })
        assertEquals(listOf("a"), groups[2].routines.map { it.id })
    }

    @Test
    fun groupRoutines_keepsEmptyFolders() {
        val groups = groupRoutines(listOf(legs), listOf(routine("a")))

        assertEquals(legs, groups[0].folder)
        assertEquals(emptyList(), groups[0].routines)
    }

    @Test
    fun groupRoutines_withoutFolders_returnsOnlyTheNoFolderGroup() {
        val groups = groupRoutines(emptyList(), listOf(routine("a"), routine("b")))

        assertEquals(1, groups.size)
        assertEquals(null, groups.single().folder)
        assertEquals(listOf("a", "b"), groups.single().routines.map { it.id })
    }

    @Test
    fun groupRoutines_routineOfAnUnknownFolder_fallsIntoTheNoFolderGroup() {
        val groups = groupRoutines(listOf(legs), listOf(routine("a", "gone")))

        assertEquals(listOf("a"), groups.last().routines.map { it.id })
    }

    @Test
    fun moveWithinGroup_movesOnePositionUpAndDown() {
        val ids = listOf("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), moveWithinGroup(ids, "b", -1))
        assertEquals(listOf("a", "c", "b"), moveWithinGroup(ids, "b", 1))
    }

    @Test
    fun moveWithinGroup_atTheBoundaries_isANoOp() {
        val ids = listOf("a", "b", "c")

        assertEquals(ids, moveWithinGroup(ids, "a", -1))
        assertEquals(ids, moveWithinGroup(ids, "c", 1))
    }

    @Test
    fun moveWithinGroup_unknownId_isANoOp() {
        val ids = listOf("a", "b")

        assertEquals(ids, moveWithinGroup(ids, "zzz", 1))
    }

    @Test
    fun moveWithinGroup_largeDelta_clampsToTheEnd() {
        assertEquals(listOf("b", "c", "a"), moveWithinGroup(listOf("a", "b", "c"), "a", 5))
    }
}
