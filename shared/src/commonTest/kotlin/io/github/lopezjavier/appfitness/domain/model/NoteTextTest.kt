package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTextTest {
    @Test
    fun normalise_blankBecomesNull() {
        assertNull(NoteText.normalise(null))
        assertNull(NoteText.normalise(""))
        assertNull(NoteText.normalise("  \n\t "))
    }

    @Test
    fun normalise_trimsTheText() {
        assertEquals("pausa larga", NoteText.normalise("  pausa larga \n"))
    }

    @Test
    fun isValid_acceptsUpTo200Characters() {
        assertTrue(NoteText.isValid("x".repeat(200)))
        assertTrue(NoteText.isValid(null))
        assertTrue(NoteText.isValid("   "))
    }

    @Test
    fun isValid_rejects201Characters() {
        assertFalse(NoteText.isValid("x".repeat(201)))
    }

    @Test
    fun isValid_ignoresSurroundingSpaces() {
        assertTrue(NoteText.isValid(" " + "x".repeat(200) + " "))
    }

    @Test
    fun isValid_countsMultibyteCharactersAsOneEach() {
        assertTrue(NoteText.isValid("ñ".repeat(200)))
        assertTrue(NoteText.isValid("á".repeat(200)))
        assertFalse(NoteText.isValid("ñ".repeat(201)))
    }

    @Test
    fun isValid_countsAnEmojiAsOneCharacter() {
        val emoji = "💪"
        assertTrue(NoteText.isValid(emoji.repeat(200)))
        assertFalse(NoteText.isValid(emoji.repeat(201)))
    }

    @Test
    fun max_is200() {
        assertEquals(200, NoteText.MAX)
    }
}
