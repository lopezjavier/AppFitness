package io.github.lopezjavier.appfitness.domain.model

import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlinx.serialization.Serializable

/** What the photo shows: a plated dish, a nutrition label or a barcode on a package. */
enum class FoodPhotoMode { Dish, Label, Barcode }

/** How sure the model is about its estimate. */
enum class FoodPhotoConfidence { Low, Medium, High }

/**
 * A food estimated from a photo by the `food-photo` function. Values are per 100 [baseUnit]; a value the photo did not
 * show is null (never 0). [micronutrients] are grams per 100 [baseUnit] keyed `<offKey>_100g`, like Open Food Facts.
 */
data class FoodPhotoDraft(
    val name: String,
    val brand: String?,
    val baseUnit: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val micronutrients: Map<String, Double>,
    val alcohol: Double? = null,
    val estimatedGrams: Double?,
    val confidence: FoodPhotoConfidence?,
)

/** Why an estimate failed the energy check: [result] is the level, [computed] the kcal the macros add up to. */
data class EnergyIssue(val result: EnergyCheckLevel, val kcal: Double, val computed: Double, val gap: Double)

/**
 * A failed estimate sent back to `food-photo` so the model corrects it (ADR-104). [fibre] and [polyols] are grams per
 * 100 [baseUnit]; null macros stay null (not typed).
 */
data class PreviousEstimate(
    val name: String,
    val baseUnit: String,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val alcohol: Double?,
    val fibre: Double?,
    val polyols: Double?,
    val reason: EnergyIssue,
)

/** Prefill of the create-food form: nothing is saved until the user confirms it. */
@Serializable
data class NewFoodDraft(
    val name: String,
    val brand: String?,
    val baseUnit: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val micronutrients: NewMicronutrients,
    val portions: List<NewPortion>,
    /** Null for a photo; set only when an Open Food Facts food is corrected into the user's own copy. */
    val barcode: String? = null,
    /** Grams of alcohol per 100 base units; null when unknown. */
    val alcohol: Double? = null,
)

private const val DISH_PORTION_LABEL = "1 ración"

/**
 * The form prefill for this draft. Only a dish with a positive estimate gets a portion ("1 ración"); micronutrients
 * (grams per 100) are converted to each nutrient's label unit, and keys the app does not know are ignored.
 */
fun FoodPhotoDraft.toNewFoodDraft(mode: FoodPhotoMode): NewFoodDraft {
    val portion = estimatedGrams?.takeIf { mode == FoodPhotoMode.Dish && it.isFinite() && it > 0.0 }
    val values = buildMap {
        MicronutrientKind.entries.filter { it.editable }.forEach { kind ->
            val grams = micronutrients[kind.offKey + "_100g"]?.takeIf { it.isFinite() && it >= 0.0 }
            if (grams != null) put(kind, kind.labelUnit.fromGrams(grams))
        }
    }
    return NewFoodDraft(
        name = name,
        brand = brand,
        baseUnit = baseUnit,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        micronutrients = NewMicronutrients(values),
        portions = listOfNotNull(portion?.let { NewPortion(DISH_PORTION_LABEL, it) }),
        alcohol = alcohol?.takeIf { it.isFinite() && it >= 0.0 },
    )
}
