package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem

/**
 * The template items of a diary section: its entries in order, one item per entry. Each item keeps the entry's
 * cooking method unless [methodOverrides] (keyed by entry id) says otherwise; drinks (`ml`) are always raw.
 */
class BuildTemplateItems {
    operator fun invoke(
        entries: List<FoodLogEntry>,
        methodOverrides: Map<String, CookingMethod> = emptyMap(),
    ): List<NewTemplateItem> = entries.map {
        val method = CookingImpact.effectiveMethod(methodOverrides[it.id] ?: it.cookingMethod, it.baseUnit)
        NewTemplateItem(it.foodId, it.amount, it.portionLabel, method)
    }
}
