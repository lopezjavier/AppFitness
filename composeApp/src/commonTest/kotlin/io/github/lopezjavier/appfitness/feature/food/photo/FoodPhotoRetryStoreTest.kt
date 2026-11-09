package io.github.lopezjavier.appfitness.feature.food.photo

import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodPhotoRetryStoreTest {
    private val capture = FakePhotoCapture()
    private val store = FoodPhotoRetryStore(capture)

    @Test
    fun put_returnsATokenThatResolvesToThePhotoModeAndComment() {
        val token = store.put("photo-1", FoodPhotoMode.Dish, "con salsa")

        assertTrue(store.has(token))
        assertEquals(RetryPhoto("photo-1", FoodPhotoMode.Dish, "con salsa"), store.get(token))
    }

    @Test
    fun twoPuts_giveDifferentTokens() {
        assertNotEquals(
            store.put("photo-1", FoodPhotoMode.Dish, null),
            store.put("photo-2", FoodPhotoMode.Label, null),
        )
    }

    @Test
    fun release_forgetsTheTokenAndDiscardsTheFile() {
        val token = store.put("photo-1", FoodPhotoMode.Label, null)

        store.release(token)

        assertFalse(store.has(token))
        assertNull(store.get(token))
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun release_ofAnUnknownTokenDiscardsNothing() {
        store.release("retry-unknown")

        assertTrue(capture.discarded.isEmpty())
    }

    @Test
    fun aNewStoreKnowsNoTokenOfTheOldOne_asAfterProcessDeath() {
        val token = store.put("photo-1", FoodPhotoMode.Dish, null)

        assertFalse(FoodPhotoRetryStore(capture).has(token))
    }
}
