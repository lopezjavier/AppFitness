package io.github.lopezjavier.appfitness.domain

import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToLong

private const val MG_PER_G = 1_000.0
private const val UG_PER_G = 1_000_000.0
private const val ONE_MG_IN_G = 0.001
private const val ROUND_FACTOR = 100.0

/** Maps Open Food Facts `nutriments` to the displayable [Micronutrients]. */
object OffMicronutrientsMapper {
    /** [nutriments] is the `nutriments` object of an OFF product; values are grams per 100 g / 100 ml. */
    fun fromNutriments(nutriments: JsonObject?, baseUnit: String): Micronutrients {
        if (nutriments == null) return Micronutrients.Empty
        return map(baseUnit) { key -> (nutriments[key] as? JsonPrimitive)?.content?.toDoubleOrNull() }
    }

    private fun map(baseUnit: String, read: (String) -> Double?): Micronutrients {
        val primary = if (baseUnit == "ml") "_100ml" else "_100g"
        val fallback = if (baseUnit == "ml") "_100g" else "_100ml"
        val items = MicronutrientKind.entries.mapNotNull { kind ->
            val grams = valid(read(kind.offKey + primary)) ?: valid(read(kind.offKey + fallback))
            grams?.let { toDisplay(kind, it) }
        }
        return Micronutrients(items)
    }

    private fun valid(value: Double?): Double? = value?.takeIf { it.isFinite() && it >= 0.0 }

    private fun toDisplay(kind: MicronutrientKind, grams: Double): Micronutrient? {
        // Label nutrients are always grams, as printed on a label (0.5 g of salt, not 500 mg).
        val (amount, unit) = when {
            kind.isLabel || grams >= 1.0 -> grams to MicronutrientUnit.G
            grams >= ONE_MG_IN_G -> grams * MG_PER_G to MicronutrientUnit.MG
            else -> grams * UG_PER_G to MicronutrientUnit.UG
        }
        val rounded = (amount * ROUND_FACTOR).roundToLong() / ROUND_FACTOR
        return if (rounded == 0.0 && !kind.isLabel) null else Micronutrient(kind, rounded, unit)
    }
}
