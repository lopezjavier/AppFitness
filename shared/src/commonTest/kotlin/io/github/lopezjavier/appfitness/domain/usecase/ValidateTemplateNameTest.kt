package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateTemplateNameTest {
    private val validate = ValidateTemplateName()

    @Test
    fun emptyName_isBlank() {
        assertEquals(TemplateNameCheck.Blank, validate(""))
    }

    @Test
    fun spacesOnly_isBlank() {
        assertEquals(TemplateNameCheck.Blank, validate("   "))
    }

    @Test
    fun oneCharacter_isValid() {
        assertEquals(TemplateNameCheck.Valid, validate("A"))
    }

    @Test
    fun sixtyCharacters_isValid() {
        assertEquals(TemplateNameCheck.Valid, validate("a".repeat(60)))
    }

    @Test
    fun sixtyOneCharacters_isTooLong() {
        assertEquals(TemplateNameCheck.TooLong, validate("a".repeat(61)))
    }

    @Test
    fun surroundingSpaces_areTrimmedBeforeCounting() {
        assertEquals(TemplateNameCheck.Valid, validate("  " + "a".repeat(60) + "  "))
    }
}
