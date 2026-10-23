package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RoutineDraftWarmUpTest {
    private val sixty = listOf(WarmUpSet(60.0, 3))
    private val two = listOf(WarmUpSet(40.0, 5), WarmUpSet(60.0, 3))

    private fun draftOf(vararg exercises: String) = exercises.fold(RoutineDraft()) { draft, id -> draft.withNewRow(id) }

    @Test
    fun warmUps_areInsertedBeforeTheFirstWorkingSet() {
        val result = draftOf("a").withWarmUpSets(0, two)

        val sets = result.rows[0].sets
        assertEquals(5, sets.size)
        assertEquals(listOf(SetType.WarmUp, SetType.WarmUp), sets.take(2).map { it.type })
        assertEquals(listOf("40", "60"), sets.take(2).map { it.weight })
        assertEquals(listOf("5", "3"), sets.take(2).map { it.reps })
        assertTrue(sets.drop(2).none { it.type == SetType.WarmUp })
    }

    @Test
    fun warmUps_goAfterTheLeadingWarmUps() {
        val first = draftOf("a").withWarmUpSets(0, sixty)

        val result = first.withWarmUpSets(0, listOf(WarmUpSet(80.0, 2)))

        assertEquals(listOf("60", "80"), result.rows[0].sets.take(2).map { it.weight })
        assertEquals(listOf(SetType.WarmUp, SetType.WarmUp), result.rows[0].sets.take(2).map { it.type })
    }

    @Test
    fun theSetIds_stayUnique() {
        val result = draftOf("a", "b").withWarmUpSets(0, two)

        val ids = result.rows.flatMap { row -> row.sets.map { it.setId } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun exceedingTheSetCap_changesNothing() {
        var draft = draftOf("a")
        while (draft.rows[0].sets.size < RoutineBounds.SETS_MAX) draft = draft.withAddedSet(0)

        assertSame(draft, draft.withWarmUpSets(0, sixty))
        assertFalse(draft.canAddWarmUps(0, 1))
    }

    @Test
    fun reachingExactlyTheCap_isAllowed() {
        var draft = draftOf("a")
        while (draft.rows[0].sets.size < RoutineBounds.SETS_MAX - 1) draft = draft.withAddedSet(0)

        assertTrue(draft.canAddWarmUps(0, 1))
        assertEquals(RoutineBounds.SETS_MAX, draft.withWarmUpSets(0, sixty).rows[0].sets.size)
    }

    @Test
    fun inASuperset_theOtherMembersAreLeveledWithCopiesOfTheirLastSet() {
        val grouped = draftOf("a", "b").withGroups(listOf(1, 1))

        val result = grouped.withWarmUpSets(0, two)

        assertEquals(5, result.rows[0].sets.size)
        assertEquals(5, result.rows[1].sets.size)
        assertEquals(listOf(SetType.WarmUp, SetType.WarmUp), result.rows[0].sets.take(2).map { it.type })
        assertTrue(result.rows[1].sets.none { it.type == SetType.WarmUp })
    }

    @Test
    fun anInvalidRowOrEmptyList_changesNothing() {
        val draft = draftOf("a")

        assertSame(draft, draft.withWarmUpSets(3, sixty))
        assertSame(draft, draft.withWarmUpSets(0, emptyList()))
    }

    @Test
    fun theInsertedWarmUps_travelInTheSavedRoutine() {
        val routine = draftOf("a").withWarmUpSets(0, sixty).toRoutine(null)

        assertEquals(SetType.WarmUp, routine.exercises[0].plannedSets.first().type)
        assertEquals(60.0, routine.exercises[0].plannedSets.first().weightKg)
    }
}
