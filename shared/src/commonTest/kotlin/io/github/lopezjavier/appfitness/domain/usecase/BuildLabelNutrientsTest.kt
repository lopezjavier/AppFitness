package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.OffMicronutrientsMapper
import io.github.lopezjavier.appfitness.domain.model.LabelNutrient
import io.github.lopezjavier.appfitness.domain.model.LabelNutrientKey
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildLabelNutrientsTest {
    private val nutrition = NutritionPer100(kcal = 100.0, proteinG = 5.0, carbsG = 10.0, fatG = 3.0)

    private fun extra(kind: MicronutrientKind, amount: Double = 1.0, unit: MicronutrientUnit = kind.labelUnit) =
        Micronutrient(kind, amount, unit)

    private fun keys(rows: List<LabelNutrient>) = rows.map { it.key }

    @Test
    fun macrosAreAlwaysShownInLabelOrderWithoutExtras() {
        val rows = BuildLabelNutrients(nutrition, Micronutrients.Empty)

        assertEquals(
            listOf(LabelNutrientKey.Energy, LabelNutrientKey.Fat, LabelNutrientKey.Carbs, LabelNutrientKey.Protein),
            keys(rows),
        )
        assertEquals(listOf(100.0, 3.0, 10.0, 5.0), rows.map { it.amount })
    }

    @Test
    fun fullLabelFollowsAnnexFifteenThenVitaminsAndMinerals() {
        val all = Micronutrients(MicronutrientKind.entries.reversed().map { extra(it) })

        val ordered = keys(BuildLabelNutrients(nutrition, all))

        val expectedHead = listOf(
            LabelNutrientKey.Energy,
            LabelNutrientKey.Fat,
            LabelNutrientKey.Extra(MicronutrientKind.SaturatedFat),
            LabelNutrientKey.Extra(MicronutrientKind.MonounsaturatedFat),
            LabelNutrientKey.Extra(MicronutrientKind.PolyunsaturatedFat),
            LabelNutrientKey.Carbs,
            LabelNutrientKey.Extra(MicronutrientKind.Sugars),
            LabelNutrientKey.Extra(MicronutrientKind.Polyols),
            LabelNutrientKey.Extra(MicronutrientKind.Starch),
            LabelNutrientKey.Extra(MicronutrientKind.Fiber),
            LabelNutrientKey.Protein,
            LabelNutrientKey.Extra(MicronutrientKind.Salt),
            LabelNutrientKey.Extra(MicronutrientKind.Sodium),
        )
        assertEquals(expectedHead, ordered.take(expectedHead.size))
        val tail = ordered.drop(expectedHead.size).map { (it as LabelNutrientKey.Extra).kind }
        assertEquals(MicronutrientKind.entries.drop(MicronutrientKind.Sodium.ordinal + 1), tail)
        assertEquals(MicronutrientKind.VitaminA, tail.first())
        assertEquals(MicronutrientKind.Iodine, tail.last())
    }

    @Test
    fun absentExtrasAreHiddenAndARealZeroOfALabelNutrientIsShown() {
        val rows = BuildLabelNutrients(
            nutrition,
            Micronutrients(listOf(extra(MicronutrientKind.Sugars, amount = 0.0))),
        )

        assertTrue(LabelNutrientKey.Extra(MicronutrientKind.Sugars) in keys(rows))
        assertFalse(LabelNutrientKey.Extra(MicronutrientKind.Salt) in keys(rows))
    }

    @Test
    fun usualLabelRowsAreFlaggedAndTheRestIsNot() {
        val rows = BuildLabelNutrients(
            nutrition,
            Micronutrients(
                listOf(
                    extra(MicronutrientKind.SaturatedFat),
                    extra(MicronutrientKind.Starch),
                    extra(MicronutrientKind.VitaminC),
                ),
            ),
        )

        val usual = rows.filter { it.inUsualLabel }.map { it.key }
        assertEquals(
            listOf(
                LabelNutrientKey.Energy,
                LabelNutrientKey.Fat,
                LabelNutrientKey.Extra(MicronutrientKind.SaturatedFat),
                LabelNutrientKey.Carbs,
                LabelNutrientKey.Protein,
            ),
            usual,
        )
    }

    @Test
    fun aManualAndAnOffFoodWithTheSameNutrientsListTheSameOrder() {
        val off = OffMicronutrientsMapper.fromNutriments(
            JsonObject(
                mapOf(
                    "salt_100g" to JsonPrimitive(0.5),
                    "vitamin-c_100g" to JsonPrimitive(0.0008),
                    "sugars_100g" to JsonPrimitive(4.0),
                ),
            ),
            "g",
        )
        val manual = Micronutrients(
            listOf(
                extra(MicronutrientKind.VitaminC, 0.8, MicronutrientUnit.MG),
                extra(MicronutrientKind.Salt, 0.5),
                extra(MicronutrientKind.Sugars, 4.0),
            ),
        )

        assertEquals(
            keys(BuildLabelNutrients(nutrition, manual)),
            keys(BuildLabelNutrients(nutrition, off)),
        )
    }
}
