package io.github.lopezjavier.appfitness.domain.model

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
    val estimatedGrams: Double?,
    val confidence: FoodPhotoConfidence?,
)

/** Prefill of the create-food form: nothing is saved until the user confirms it. */
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
    /** Always null: a photo never provides a barcode to save. */
    val barcode: String? = null,
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
    )
}
