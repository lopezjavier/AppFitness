package io.github.lopezjavier.appfitness.feature.food.photo

import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import kotlin.random.Random

/** The photo (an opaque [PhotoCapture] handle), its [mode] and the dish [comment] kept for one retry. */
data class RetryPhoto(val handle: String, val mode: FoodPhotoMode, val comment: String?)

/**
 * In-memory hand-over of a photo from the photo flow to the create-food form, so the AI estimate can be repeated
 * from the form when it fails the energy check (ADR-104). Memory only: the image is never persisted (privacy rule),
 * so after process death the store is empty and a token no longer resolves. [release] deletes the file.
 */
class FoodPhotoRetryStore(private val photoCapture: PhotoCapture) {
    private val held = mutableMapOf<String, RetryPhoto>()

    /** Keeps [handle] for a retry and returns the token the form reads it by. */
    fun put(handle: String, mode: FoodPhotoMode, comment: String?): String {
        val token = "retry-${Random.nextLong().toULong().toString(HEX)}"
        held[token] = RetryPhoto(handle, mode, comment)
        return token
    }

    fun get(token: String): RetryPhoto? = held[token]

    fun has(token: String): Boolean = token in held

    /** Forgets [token] and deletes its photo file; unknown tokens are ignored. */
    fun release(token: String) {
        held.remove(token)?.let { photoCapture.discard(it.handle) }
    }

    private companion object {
        const val HEX = 16
    }
}
