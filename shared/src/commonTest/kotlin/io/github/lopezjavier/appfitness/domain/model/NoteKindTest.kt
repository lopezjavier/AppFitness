package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteKindTest {
    @Test
    fun setNoteKind_wireNamesRoundTrip() {
        assertEquals(
            listOf("technique", "range", "grip", "assisted", "other"),
            SetNoteKind.entries.map { it.wire },
        )
        SetNoteKind.entries.forEach { assertEquals(it, SetNoteKind.fromWire(it.wire)) }
        assertNull(SetNoteKind.fromWire("pain"))
        assertNull(SetNoteKind.fromWire(null))
    }

    @Test
    fun exerciseNoteKind_wireNamesRoundTrip() {
        assertEquals(
            listOf("weight_up", "weight_down", "technique", "setup", "other"),
            ExerciseNoteKind.entries.map { it.wire },
        )
        ExerciseNoteKind.entries.forEach { assertEquals(it, ExerciseNoteKind.fromWire(it.wire)) }
        assertNull(ExerciseNoteKind.fromWire("range"))
    }

    @Test
    fun isValid_otherNeedsText() {
        assertFalse(Note.isValid(SetNoteKind.Other, null))
        assertFalse(Note.isValid(SetNoteKind.Other, "   "))
        assertTrue(Note.isValid(SetNoteKind.Other, "pausa larga"))
    }

    @Test
    fun isValid_otherKindsMayOmitText() {
        assertTrue(Note.isValid(SetNoteKind.Technique, null))
        assertTrue(Note.isValid(ExerciseNoteKind.WeightUp, ""))
        assertTrue(Note.isValid(SetNoteKind.Grip, "x".repeat(200)))
    }

    @Test
    fun isValid_refusesMoreThan200Characters() {
        assertFalse(Note.isValid(SetNoteKind.Other, "x".repeat(201)))
        assertFalse(Note.isValid(SetNoteKind.Technique, "x".repeat(201)))
    }

    @Test
    fun isValid_refusesTextWithoutKind() {
        assertFalse(Note.isValid<SetNoteKind>(null, "texto"))
        assertTrue(Note.isValid<SetNoteKind>(null, null))
    }

    @Test
    fun of_trimsTextAndBlankBecomesNull() {
        assertEquals(Note(SetNoteKind.Range, "corto"), Note.of(SetNoteKind.Range, "  corto "))
        assertEquals(Note(SetNoteKind.Range, null), Note.of(SetNoteKind.Range, " "))
    }

    @Test
    fun of_isNullWhenThereIsNoNoteOrItIsInvalid() {
        assertNull(Note.of<SetNoteKind>(null, null))
        assertNull(Note.of<SetNoteKind>(null, "texto"))
        assertNull(Note.of(SetNoteKind.Other, null))
    }

    @Test
    fun resolveKind_legacyTextWithoutKindIsOther() {
        assertEquals(SetNoteKind.Other, resolveNoteKind(null, "vieja", SetNoteKind.Other))
        assertNull(resolveNoteKind<SetNoteKind>(null, null, SetNoteKind.Other))
        assertNull(resolveNoteKind<SetNoteKind>(null, "  ", SetNoteKind.Other))
        assertEquals(SetNoteKind.Grip, resolveNoteKind(SetNoteKind.Grip, null, SetNoteKind.Other))
    }
}
