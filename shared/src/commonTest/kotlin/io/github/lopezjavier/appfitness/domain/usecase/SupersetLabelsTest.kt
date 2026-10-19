package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class SupersetLabelsTest {
    private fun labels(vararg groups: Int?) =
        supersetLabels(groups.toList()).map { it?.let { l -> "${l.letter}${l.number}" } }

    @Test
    fun noGroups_allNull() {
        assertEquals(listOf(null, null, null), labels(null, null, null))
    }

    @Test
    fun oneGroupOfTwo_isA1A2() {
        assertEquals(listOf(null, "A1", "A2"), labels(null, 7, 7))
    }

    @Test
    fun twoGroups_getLettersByOrderOfAppearance_notByValue() {
        assertEquals(listOf("A1", "A2", "B1", "B2"), labels(9, 9, 2, 2))
    }

    @Test
    fun groupOfThree_isA1ToA3() {
        assertEquals(listOf("A1", "A2", "A3"), labels(1, 1, 1))
    }

    @Test
    fun loneMember_isNotLabelled() {
        assertEquals(listOf(null, null, null), labels(null, 1, null))
    }

    @Test
    fun nonConsecutiveSameValue_isNotLabelled() {
        assertEquals(listOf(null, null, null), labels(1, null, 1))
    }

    @Test
    fun emptyList_isEmpty() {
        assertEquals(emptyList(), labels())
    }
}

class RoutineOrderingTest {
    @Test
    fun moveExercise_lastToFirst() {
        assertEquals(listOf("C", "A", "B"), moveExercise(listOf("A", "B", "C"), from = 2, to = 0))
    }

    @Test
    fun moveExercise_toSameIndex_changesNothing() {
        assertEquals(listOf("A", "B", "C"), moveExercise(listOf("A", "B", "C"), from = 1, to = 1))
    }

    @Test
    fun moveExercise_outOfRange_isIgnored() {
        val list = listOf("A", "B", "C")
        assertEquals(list, moveExercise(list, from = 3, to = 0))
        assertEquals(list, moveExercise(list, from = 0, to = -1))
        assertEquals(list, moveExercise(list, from = 0, to = 3))
    }

    @Test
    fun normalizeGroups_dissolvesAGroupLeftWithOneMember() {
        // [g, g, x] after moving the second member to the end: [g, x, g].
        assertEquals(listOf(null, null, null), normalizeGroups(listOf(4, null, 4)))
    }

    @Test
    fun normalizeGroups_keepsRunsOfTwoOrMore_andRenumbersFromOne() {
        assertEquals(listOf(1, 1, null, 2, 2, 2), normalizeGroups(listOf(8, 8, null, 3, 3, 3)))
    }

    @Test
    fun normalizeGroups_splitsTheSameValueIntoSeparateGroupsWhenNotConsecutive() {
        assertEquals(listOf(1, 1, null, 2, 2), normalizeGroups(listOf(5, 5, null, 5, 5)))
    }

    @Test
    fun groupWithNext_ofTwoUngrouped_createsAGroup() {
        val grouped = groupWithNext(listOf(null, null, null), index = 0)

        assertEquals(listOf(1, 1, null), grouped)
    }

    @Test
    fun groupWithNext_onTheLastItem_isANoOp() {
        assertEquals(listOf(null, null), groupWithNext(listOf(null, null), index = 1))
    }

    @Test
    fun groupWithNext_joinsAnExistingGroup() {
        assertEquals(listOf(1, 1, 1), groupWithNext(listOf(1, 1, null), index = 1))
    }

    @Test
    fun groupWithNext_ofAnUngroupedItemBeforeAGroup_joinsThatGroup() {
        assertEquals(listOf(1, 1, 1), groupWithNext(listOf(null, 1, 1), index = 0))
    }

    @Test
    fun groupWithNext_mergesTwoGroups() {
        assertEquals(listOf(1, 1, 1, 1), groupWithNext(listOf(1, 1, 2, 2), index = 1))
    }

    @Test
    fun ungroup_clearsTheWholeGroup() {
        assertEquals(listOf(null, null, null, 1, 1), ungroup(listOf(5, 5, 5, 6, 6), index = 1))
    }

    @Test
    fun ungroup_ofAnUngroupedItem_changesNothing() {
        assertEquals(listOf(null, 1, 1), ungroup(listOf(null, 1, 1), index = 0))
    }
}
