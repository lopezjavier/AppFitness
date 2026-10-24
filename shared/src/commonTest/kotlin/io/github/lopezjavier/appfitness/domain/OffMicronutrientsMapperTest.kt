package io.github.lopezjavier.appfitness.domain

import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OffMicronutrientsMapperTest {
    private fun map(baseUnit: String = "g", build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        OffMicronutrientsMapper.fromNutriments(buildJsonObject(build), baseUnit).items

    @Test
    fun `maps fiber sugars salt and vitamin C per 100 g in display order`() {
        val items = map {
            put("vitamin-c_100g", 0.012)
            put("salt_100g", 0.5)
            put("sugars_100g", 4.1)
            put("fiber_100g", 2.5)
        }
        assertEquals(
            listOf(
                Micronutrient(MicronutrientKind.Sugars, 4.1, MicronutrientUnit.G),
                Micronutrient(MicronutrientKind.Fiber, 2.5, MicronutrientUnit.G),
                Micronutrient(MicronutrientKind.Salt, 0.5, MicronutrientUnit.G),
                Micronutrient(MicronutrientKind.VitaminC, 12.0, MicronutrientUnit.MG),
            ),
            items,
        )
    }

    @Test
    fun `one gram is shown in grams`() {
        assertEquals(
            listOf(Micronutrient(MicronutrientKind.Calcium, 1.0, MicronutrientUnit.G)),
            map { put("calcium_100g", 1.0) },
        )
    }

    @Test
    fun `below a milligram is shown in micrograms`() {
        assertEquals(
            listOf(Micronutrient(MicronutrientKind.VitaminD, 900.0, MicronutrientUnit.UG)),
            map { put("vitamin-d_100g", 0.0009) },
        )
    }

    @Test
    fun `fractions of a gram above a milligram are shown in milligrams`() {
        assertEquals(
            listOf(Micronutrient(MicronutrientKind.Iron, 1.5, MicronutrientUnit.MG)),
            map { put("iron_100g", 0.0015) },
        )
    }

    @Test
    fun `values are rounded to two decimals`() {
        assertEquals(
            listOf(Micronutrient(MicronutrientKind.Fiber, 2.35, MicronutrientUnit.G)),
            map { put("fiber_100g", 2.3456) },
        )
    }

    @Test
    fun `drinks read the per 100 ml value`() {
        val items = map("ml") {
            put("sugars_100g", 9.0)
            put("sugars_100ml", 10.6)
        }
        assertEquals(listOf(Micronutrient(MicronutrientKind.Sugars, 10.6, MicronutrientUnit.G)), items)
    }

    @Test
    fun `drinks fall back to the per 100 g value`() {
        assertEquals(
            listOf(Micronutrient(MicronutrientKind.Sugars, 9.0, MicronutrientUnit.G)),
            map("ml") { put("sugars_100g", 9.0) },
        )
    }

    @Test
    fun `solids fall back to the per 100 ml value`() {
        assertEquals(
            listOf(Micronutrient(MicronutrientKind.Sugars, 3.0, MicronutrientUnit.G)),
            map { put("sugars_100ml", 3.0) },
        )
    }

    @Test
    fun `absent nutrients are not listed`() {
        assertEquals(listOf(MicronutrientKind.Fiber), map { put("fiber_100g", 2.0) }.map { it.kind })
    }

    @Test
    fun `malformed values are ignored`() {
        val items = map {
            put("fiber_100g", "abc")
            put("sugars_100g", -1.0)
            put("salt_100g", JsonPrimitive(null as String?))
            put("sodium_100g", "NaN")
            put("calcium_100g", buildJsonObject { put("x", 1) })
            put("iron_100g", "0.0015")
        }
        assertEquals(listOf(MicronutrientKind.Iron), items.map { it.kind })
    }

    @Test
    fun `unknown keys are ignored`() {
        assertTrue(map { put("energy-kcal_100g", 250.0) }.isEmpty())
    }

    @Test
    fun `a real zero is kept for label nutrients and hidden for vitamins`() {
        val items = map {
            put("sugars_100g", 0.0)
            put("vitamin-c_100g", 0.0)
        }
        assertEquals(listOf(Micronutrient(MicronutrientKind.Sugars, 0.0, MicronutrientUnit.G)), items)
    }

    @Test
    fun `salt and sodium are both shown when both are present`() {
        val kinds = map {
            put("sodium_100g", 0.2)
            put("salt_100g", 0.5)
        }.map { it.kind }
        assertEquals(listOf(MicronutrientKind.Salt, MicronutrientKind.Sodium), kinds)
    }

    @Test
    fun `vitamins come before minerals`() {
        val kinds = map {
            put("calcium_100g", 0.12)
            put("vitamin-b12_100g", 0.000002)
            put("saturated-fat_100g", 1.2)
        }.map { it.kind }
        assertEquals(
            listOf(MicronutrientKind.SaturatedFat, MicronutrientKind.VitaminB12, MicronutrientKind.Calcium),
            kinds,
        )
    }

    @Test
    fun `mono and polyunsaturates polyols and starch follow label order and keep a real zero`() {
        val items = map {
            put("starch_100g", 0.0)
            put("polyols_100g", 1.5)
            put("polyunsaturated-fat_100g", 2.0)
            put("monounsaturated-fat_100g", 3.0)
        }
        assertEquals(
            listOf(
                MicronutrientKind.MonounsaturatedFat,
                MicronutrientKind.PolyunsaturatedFat,
                MicronutrientKind.Polyols,
                MicronutrientKind.Starch,
            ),
            items.map { it.kind },
        )
        assertEquals(0.0, items.last().amount)
    }

    @Test
    fun `missing or empty nutriments give an empty result`() {
        assertTrue(OffMicronutrientsMapper.fromNutriments(null, "g").isEmpty)
        assertTrue(OffMicronutrientsMapper.fromNutriments(JsonObject(emptyMap()), "g").isEmpty)
    }
}
