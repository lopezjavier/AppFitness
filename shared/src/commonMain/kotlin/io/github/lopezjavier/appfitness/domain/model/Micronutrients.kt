package io.github.lopezjavier.appfitness.domain.model

/**
 * The nutrients shown as extra information of a food, in label order (Annex XV, then Annex XIII vitamins and
 * minerals). [offKey] is the Open Food Facts nutriment key (without the `_100g`/`_100ml` suffix). [labelUnit] is the
 * unit printed on a label and typed in the form. [isLabel] marks the Annex XV nutrients, whose real zero is worth
 * showing. [editable] is false for nutrients only shown for Open Food Facts foods (sodium).
 */
enum class MicronutrientKind(
    val offKey: String,
    val labelUnit: MicronutrientUnit = MicronutrientUnit.MG,
    val isLabel: Boolean = false,
    val editable: Boolean = true,
) {
    SaturatedFat("saturated-fat", MicronutrientUnit.G, isLabel = true),
    MonounsaturatedFat("monounsaturated-fat", MicronutrientUnit.G, isLabel = true),
    PolyunsaturatedFat("polyunsaturated-fat", MicronutrientUnit.G, isLabel = true),
    Sugars("sugars", MicronutrientUnit.G, isLabel = true),
    Polyols("polyols", MicronutrientUnit.G, isLabel = true),
    Starch("starch", MicronutrientUnit.G, isLabel = true),
    Fiber("fiber", MicronutrientUnit.G, isLabel = true),
    Salt("salt", MicronutrientUnit.G, isLabel = true),
    Sodium("sodium", MicronutrientUnit.G, isLabel = true, editable = false),
    VitaminA("vitamin-a", MicronutrientUnit.UG),
    VitaminD("vitamin-d", MicronutrientUnit.UG),
    VitaminE("vitamin-e"),
    VitaminK("vitamin-k", MicronutrientUnit.UG),
    VitaminC("vitamin-c"),
    VitaminB1("vitamin-b1"),
    VitaminB2("vitamin-b2"),
    VitaminB3("vitamin-pp"),
    VitaminB6("vitamin-b6"),
    VitaminB9("vitamin-b9", MicronutrientUnit.UG),
    VitaminB12("vitamin-b12", MicronutrientUnit.UG),
    Biotin("biotin", MicronutrientUnit.UG),
    PantothenicAcid("pantothenic-acid"),
    Potassium("potassium"),
    Chloride("chloride"),
    Calcium("calcium"),
    Phosphorus("phosphorus"),
    Magnesium("magnesium"),
    Iron("iron"),
    Zinc("zinc"),
    Copper("copper"),
    Manganese("manganese"),
    Fluoride("fluoride"),
    Selenium("selenium", MicronutrientUnit.UG),
    Chromium("chromium", MicronutrientUnit.UG),
    Molybdenum("molybdenum", MicronutrientUnit.UG),
    Iodine("iodine", MicronutrientUnit.UG),
    ;

    companion object {
        /** The nutrients the form offers, in label order. */
        val editableKinds: List<MicronutrientKind> = entries.filter { it.editable }
    }
}

private const val MG_PER_G = 1_000.0
private const val UG_PER_G = 1_000_000.0

enum class MicronutrientUnit(private val perGram: Double) {
    G(1.0),
    MG(MG_PER_G),
    UG(UG_PER_G),
    ;

    /** [value] typed in this unit, as grams. */
    fun toGrams(value: Double): Double = value / perGram

    /** [grams] expressed in this unit. */
    fun fromGrams(grams: Double): Double = grams * perGram
}

/** One nutrient per 100 g / 100 ml, already in its best display [unit]. */
data class Micronutrient(val kind: MicronutrientKind, val amount: Double, val unit: MicronutrientUnit)

/** The nutrients to show, in display order; empty means the section is not shown. */
data class Micronutrients(val items: List<Micronutrient>) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val Empty = Micronutrients(emptyList())
    }
}
