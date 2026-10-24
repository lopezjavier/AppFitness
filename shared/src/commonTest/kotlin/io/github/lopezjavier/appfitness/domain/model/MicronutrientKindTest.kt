package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MicronutrientKindTest {
    @Test
    fun offKeysAreUnique() {
        val keys = MicronutrientKind.entries.map { it.offKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun sodiumIsShownForOffFoodsButNotOfferedInTheForm() {
        assertFalse(MicronutrientKind.Sodium.editable)
        assertFalse(MicronutrientKind.Sodium in MicronutrientKind.editableKinds)
        assertEquals(MicronutrientKind.Salt.ordinal + 1, MicronutrientKind.Sodium.ordinal)
    }

    @Test
    fun theFormOffersTheLabelNutrientsInAnnexOrder() {
        val kinds = MicronutrientKind.editableKinds
        assertEquals(MicronutrientKind.SaturatedFat, kinds.first())
        assertEquals(MicronutrientKind.Iodine, kinds.last())
        assertEquals(35, kinds.size)
        assertTrue(kinds.indexOf(MicronutrientKind.Salt) < kinds.indexOf(MicronutrientKind.VitaminA))
        assertTrue(kinds.indexOf(MicronutrientKind.VitaminC) < kinds.indexOf(MicronutrientKind.Potassium))
    }

    @Test
    fun theWhitelistMatchesTheDatabaseFunction() {
        val expected = setOf(
            "saturated-fat", "monounsaturated-fat", "polyunsaturated-fat", "sugars", "polyols", "starch", "fiber",
            "salt", "vitamin-a", "vitamin-d", "vitamin-e", "vitamin-k", "vitamin-c", "vitamin-b1", "vitamin-b2",
            "vitamin-pp", "vitamin-b6", "vitamin-b9", "vitamin-b12", "biotin", "pantothenic-acid", "potassium",
            "chloride", "calcium", "phosphorus", "magnesium", "iron", "zinc", "copper", "manganese", "fluoride",
            "selenium", "chromium", "molybdenum", "iodine",
        )
        assertEquals(expected, MicronutrientKind.editableKinds.map { it.offKey }.toSet())
    }
}
