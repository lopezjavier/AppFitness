package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem

/** The template items of a diary section: its entries in order, one item per entry. */
class BuildTemplateItems {
    operator fun invoke(entries: List<FoodLogEntry>): List<NewTemplateItem> =
        entries.map { NewTemplateItem(it.foodId, it.amount, it.portionLabel) }
}
