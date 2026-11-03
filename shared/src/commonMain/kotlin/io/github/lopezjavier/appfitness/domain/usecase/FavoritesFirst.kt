package io.github.lopezjavier.appfitness.domain.usecase

/**
 * Stable partition: the items whose id is in [favoriteIds] first, each group keeping the incoming order. The one
 * sort shared by the exercise picker and the block preview swap list (R-39).
 */
object FavoritesFirst {
    operator fun <T> invoke(items: List<T>, favoriteIds: Set<String>, idOf: (T) -> String): List<T> {
        if (favoriteIds.isEmpty()) return items
        val (favorites, others) = items.partition { idOf(it) in favoriteIds }
        return favorites + others
    }
}
