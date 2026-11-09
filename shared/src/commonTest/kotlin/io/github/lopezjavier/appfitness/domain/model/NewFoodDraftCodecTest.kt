package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewFoodDraftCodecTest {
    private val draft = NewFoodDraft(
        name = "Lentejas",
        brand = null,
        baseUnit = "g",
        kcal = 180.0,
        protein = 6.0,
        carbs = 15.0,
        fat = null,
        micronutrients = NewMicronutrients(mapOf(MicronutrientKind.Fiber to 2.5, MicronutrientKind.VitaminC to 4.0)),
        portions = listOf(NewPortion("1 ración", 250.0)),
    )

    @Test
    fun roundTripKeepsEverything() {
        assertEquals(draft, NewFoodDraftCodec.decode(NewFoodDraftCodec.encode(draft)))
    }

    @Test
    fun garbageDecodesToNull() {
        assertNull(NewFoodDraftCodec.decode("not json {"))
        assertNull(NewFoodDraftCodec.decode("""{"name":1}"""))
        assertNull(NewFoodDraftCodec.decode(""))
        assertNull(NewFoodDraftCodec.decode(null))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val text = NewFoodDraftCodec.encode(draft).removeSuffix("}") + ""","extra":1}"""
        assertEquals(draft, NewFoodDraftCodec.decode(text))
    }

    @Test
    fun roundTripKeepsAlcohol() {
        val withAlcohol = draft.copy(alcohol = 9.5)
        assertEquals(withAlcohol, NewFoodDraftCodec.decode(NewFoodDraftCodec.encode(withAlcohol)))
    }

    @Test
    fun oldJsonWithoutAlcoholDecodesWithNull() {
        val old = NewFoodDraftCodec.encode(draft)
        assertTrue(!old.contains("alcohol"))
        assertNull(NewFoodDraftCodec.decode(old)?.alcohol)
    }
}
