package io.github.lopezjavier.appfitness.feature.diary

private const val COOKING_SEPARATOR = " · "

/** The supporting line of a logged food: its portion and/or amount, then the cooking method when it is not raw. */
object FoodEntryDetail {
    /**
     * "1 vaso (200 ml)" for a portion with the base amount, "220 g" for the raw base unit, then " · [cookingLabel]"
     * when the entry is cooked. A portion without a base unit reads as the portion alone.
     */
    fun format(portionLabel: String?, amount: Double, baseUnit: String?, cookingLabel: String?): String {
        val base = listOf(amountText(amount), baseUnit.orEmpty()).filter { it.isNotEmpty() }.joinToString(" ")
        val quantity = when {
            portionLabel == null -> base
            baseUnit.isNullOrEmpty() -> portionLabel
            else -> "$portionLabel ($base)"
        }
        return if (cookingLabel == null) quantity else "$quantity$COOKING_SEPARATOR$cookingLabel"
    }
}
