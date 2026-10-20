package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateMealSlotsTest {
    private val validate = ValidateMealSlots()

    private fun draft(label: String, enabled: Boolean = true) = MealSlotDraft(null, null, label, enabled)

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
    fun noEnabledSlot_isInvalid() {
        val result = validate(listOf(draft("Desayuno", enabled = false)))

        assertTrue(result.noneEnabled)
        assertFalse(result.isValid)
    }

    @Test
    fun repeatedLabels_areAllowed() {
        assertTrue(validate(listOf(draft("Extra"), draft("Extra"))).isValid)
    }

    @Test
    fun emptyList_isInvalid() {
        assertFalse(validate(emptyList()).isValid)
    }
}
