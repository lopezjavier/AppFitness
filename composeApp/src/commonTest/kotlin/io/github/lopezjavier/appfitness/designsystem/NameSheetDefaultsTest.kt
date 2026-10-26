package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NameSheetDefaultsTest {
    private val existing = listOf("Torso", "Pierna")

    @Test
    fun normalize_trimsSurroundingSpaces() = assertEquals("Torso", NameSheetDefaults.normalize("  Torso "))

    @Test
    fun normalize_keepsInternalSpaces() = assertEquals("Torso  A", NameSheetDefaults.normalize(" Torso  A "))

    @Test
    fun isDuplicate_ignoresCaseAndSurroundingSpaces() =
        assertTrue(NameSheetDefaults.isDuplicate("  Torso ", listOf("torso")))

    @Test
    fun isDuplicate_differentNameIsNot() = assertFalse(NameSheetDefaults.isDuplicate("torso 2", existing))

    @Test
    fun isDuplicate_internalDoubleSpacesAreNotCollapsed() =
        assertFalse(NameSheetDefaults.isDuplicate("Torso  A", listOf("Torso A")))

    @Test
    fun isDuplicate_emptyListIsNeverDuplicate() = assertFalse(NameSheetDefaults.isDuplicate("Torso", emptyList()))

    @Test
    fun isDuplicate_renameKeepingTheSameNameIsAllowed() {
        assertFalse(NameSheetDefaults.isDuplicate("torso", existing, ignore = "Torso"))
        assertTrue(NameSheetDefaults.isDuplicate("Pierna", existing, ignore = "Torso"))
    }

    @Test
    fun canSave_falseForBlankOrDuplicate() {
        assertFalse(NameSheetDefaults.canSave("", duplicate = false))
        assertFalse(NameSheetDefaults.canSave("   ", duplicate = false))
        assertFalse(NameSheetDefaults.canSave("Torso", duplicate = true))
    }

    @Test
    fun canSave_trueForAValidName() = assertTrue(NameSheetDefaults.canSave("Torso A", duplicate = false))
}
