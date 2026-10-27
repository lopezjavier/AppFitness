package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateMealSlotsTest {
    private val validate = ValidateMealSlots()

    private fun draft(label: String) = MealSlotDraft(null, null, label)

    @Test
    fun validList_isValidAndLabelsAreTrimmed() {
        val result = validate(listOf(draft("  Desayuno "), draft("Cena")))

        assertTrue(result.isValid)
        assertEquals(listOf("Desayuno", "Cena"), result.slots.map { it.label })
    }

    @Test
    fun blankLabel_isFlaggedAtItsIndex() {
        val result = validate(listOf(draft("Desayuno"), draft("   ")))

        assertFalse(result.isValid)
        assertEquals(mapOf(1 to MealSlotError.Blank), result.errors)
    }

    @Test
    fun label_of50Characters_isAccepted() {
        assertTrue(validate(listOf(draft("a".repeat(50)))).isValid)
    }

    @Test
    fun label_of51Characters_isTooLong() {
        val result = validate(listOf(draft("a".repeat(51))))

        assertEquals(mapOf(0 to MealSlotError.TooLong), result.errors)
    }

    @Test
    fun label_lengthIsMeasuredAfterTrimming() {
        assertTrue(validate(listOf(draft(" " + "a".repeat(50) + " "))).isValid)
    }

    @Test
    fun repeatedLabel_flagsTheLaterOccurrenceAsDuplicate() {
        val result = validate(listOf(draft("Extra"), draft("Cena"), draft("Extra")))

        assertFalse(result.isValid)
        assertEquals(mapOf(2 to MealSlotError.Duplicate), result.errors)
    }

    @Test
    fun duplicate_ignoresCaseAndSurroundingSpaces() {
        val result = validate(listOf(draft("Cena"), draft("  cENA ")))

        assertEquals(mapOf(1 to MealSlotError.Duplicate), result.errors)
    }

    @Test
    fun blankLabels_areNotReportedAsDuplicates() {
        val result = validate(listOf(draft(" "), draft("")))

        assertEquals(mapOf(0 to MealSlotError.Blank, 1 to MealSlotError.Blank), result.errors)
    }

    @Test
    fun isDuplicate_usesTheSameRuleAsTheNameSheet() {
        // Mirrors NameSheetDefaults.isDuplicate (composeApp): trim + lowercase, inner spaces kept.
        assertTrue(ValidateMealSlots.isDuplicate("  Torso ", listOf("torso")))
        assertFalse(ValidateMealSlots.isDuplicate("torso 2", listOf("torso")))
        assertFalse(ValidateMealSlots.isDuplicate("Torso  A", listOf("Torso A")))
        assertFalse(ValidateMealSlots.isDuplicate("Torso", emptyList()))
    }

    @Test
    fun emptyList_isInvalid() {
        assertFalse(validate(emptyList()).isValid)
    }
}
