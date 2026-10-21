package io.github.lopezjavier.appfitness.domain.model

/**
 * The nutrients shown as extra information of a food, in display order. [offKey] is the Open Food Facts nutriment key
 * (without the `_100g`/`_100ml` suffix). [isLabel] marks the label nutrients, whose real zero is worth showing.
 */
enum class MicronutrientKind(val offKey: String, val isLabel: Boolean = false) {
    Fiber("fiber", isLabel = true),
    Sugars("sugars", isLabel = true),
    SaturatedFat("saturated-fat", isLabel = true),
    Salt("salt", isLabel = true),
    Sodium("sodium", isLabel = true),
    VitaminA("vitamin-a"),
    VitaminD("vitamin-d"),
    VitaminE("vitamin-e"),
    VitaminK("vitamin-k"),
    VitaminC("vitamin-c"),
    VitaminB1("vitamin-b1"),
    VitaminB2("vitamin-b2"),
    VitaminB3("vitamin-pp"),
    VitaminB6("vitamin-b6"),
    VitaminB9("vitamin-b9"),
    VitaminB12("vitamin-b12"),
    Biotin("biotin"),
    PantothenicAcid("pantothenic-acid"),
    Calcium("calcium"),
    Iron("iron"),
    Magnesium("magnesium"),
    Potassium("potassium"),
    Phosphorus("phosphorus"),
    Zinc("zinc"),
    Iodine("iodine"),
    Selenium("selenium"),
    Copper("copper"),
    Manganese("manganese"),
    Chromium("chromium"),
    Molybdenum("molybdenum"),
    Fluoride("fluoride"),
    Chloride("chloride"),
}

enum class MicronutrientUnit { G, MG, UG }

/** One nutrient per 100 g / 100 ml, already in its best display [unit]. */
data class Micronutrient(val kind: MicronutrientKind, val amount: Double, val unit: MicronutrientUnit)

/** The nutrients to show, in display order; empty means the section is not shown. */
data class Micronutrients(val items: List<Micronutrient>) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        val Empty = Micronutrients(emptyList())
    }
}
