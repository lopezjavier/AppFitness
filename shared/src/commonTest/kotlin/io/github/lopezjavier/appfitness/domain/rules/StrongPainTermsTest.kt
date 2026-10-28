package io.github.lopezjavier.appfitness.domain.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrongPainTermsTest {
    @Test
    fun theListHasTwelveTerms() {
        assertEquals(12, StrongPainTerms.TERMS.size)
    }

    @Test
    fun everyTermMatchesOnItsOwn() {
        StrongPainTerms.TERMS.forEach { assertTrue(StrongPainTerms.matches(it), it) }
    }

    @Test
    fun caseAndAccentsAreIgnored() {
        assertTrue(StrongPainTerms.matches("Dolor FUERTE"))
        assertTrue(StrongPainTerms.matches("HINCHAZON"))
        assertTrue(StrongPainTerms.matches("lesion"))
        assertTrue(StrongPainTerms.matches("LESIÓN"))
    }

    @Test
    fun aTermInsideASentenceMatches() {
        assertTrue(StrongPainTerms.matches("Ayer sentí un pinchazo, en el hombro."))
        assertTrue(StrongPainTerms.matches("tengo dolor   fuerte al bajar"))
    }

    @Test
    fun onlyWholeWordsMatch() {
        assertFalse(StrongPainTerms.matches("dolor fuertemente"))
        assertFalse(StrongPainTerms.matches("antilesión"))
    }

    @Test
    fun pluralsDoNotMatchBecauseTheListIsLiteral() {
        assertFalse(StrongPainTerms.matches("pinchazos"))
        assertFalse(StrongPainTerms.matches("lesiones"))
    }

    @Test
    fun plainDolorAndMolestiaAreNotTerms() {
        assertFalse(StrongPainTerms.matches("dolor"))
        assertFalse(StrongPainTerms.matches("una molestia en la rodilla"))
    }

    @Test
    fun nullEmptyAndBlankDoNotMatch() {
        assertFalse(StrongPainTerms.matches(null))
        assertFalse(StrongPainTerms.matches(""))
        assertFalse(StrongPainTerms.matches("   "))
    }
}
