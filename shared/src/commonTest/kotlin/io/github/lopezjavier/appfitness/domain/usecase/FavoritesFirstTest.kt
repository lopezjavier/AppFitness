package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class FavoritesFirstTest {
    private fun sort(items: List<String>, favorites: Set<String>) = FavoritesFirst(items, favorites) { it }

    @Test
    fun emptyList_staysEmpty() {
        assertEquals(emptyList(), sort(emptyList(), setOf("a")))
    }

    @Test
    fun noFavourites_keepsTheOrder() {
        assertEquals(listOf("c", "a", "b"), sort(listOf("c", "a", "b"), emptySet()))
    }

    @Test
    fun allFavourites_keepsTheOrder() {
        assertEquals(listOf("c", "a", "b"), sort(listOf("c", "a", "b"), setOf("a", "b", "c")))
    }

    @Test
    fun mixed_putsFavouritesFirstKeepingEachGroupOrder() {
        assertEquals(listOf("b", "d", "a", "c"), sort(listOf("a", "b", "c", "d"), setOf("d", "b")))
    }

    @Test
    fun idsNotInTheList_areIgnored() {
        assertEquals(listOf("b", "a"), sort(listOf("a", "b"), setOf("b", "zzz")))
    }
}
