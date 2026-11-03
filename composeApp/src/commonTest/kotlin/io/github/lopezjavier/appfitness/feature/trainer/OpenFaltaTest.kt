package io.github.lopezjavier.appfitness.feature.trainer

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenFaltaTest {
    private val review = review(
        falta = listOf(falta("a"), falta("b")),
        changes = listOf(change("c1", fixes = "a"), change("c2", fixes = null)),
    )

    @Test
    fun withEveryChangeAccepted_theFixedFaltaIsClosed() {
        assertEquals(setOf("b"), openFaltaCodes(review, setOf("c1", "c2")))
    }

    @Test
    fun rejectingTheFixingChange_reopensTheFalta() {
        assertEquals(setOf("a", "b"), openFaltaCodes(review, setOf("c2")))
    }

    @Test
    fun withNoFalta_nothingIsOpen() {
        assertEquals(emptySet(), openFaltaCodes(review(), emptySet()))
    }

    @Test
    fun defaultSelection_checksEverythingButTheLaterMembersOfAConflictGroup() {
        val conflicting = review(
            changes = listOf(
                change("c1", conflictsWith = listOf("c2")),
                change("c2", conflictsWith = listOf("c1")),
                change("c3"),
            ),
        )

        assertEquals(setOf("c1", "c3"), defaultAccepted(conflicting))
    }

    @Test
    fun toggleOn_unchecksTheChangesOfItsConflictGroup() {
        val conflicting = review(
            changes = listOf(change("c1", conflictsWith = listOf("c2")), change("c2"), change("c3")),
        )

        val accepted = toggled(conflicting, setOf("c1", "c3"), "c2")

        assertEquals(setOf("c2", "c3"), accepted)
    }

    @Test
    fun toggleOff_removesOnlyThatChange() {
        assertEquals(setOf("c2"), toggled(review, setOf("c1", "c2"), "c1"))
    }

    @Test
    fun toggleOfUnknownId_changesNothing() {
        assertEquals(setOf("c1"), toggled(review, setOf("c1"), "zzz"))
    }
}
