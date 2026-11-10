package io.github.lopezjavier.appfitness.feature.food.photo

import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import kotlin.random.Random

/**
 * What a retry resends: a photo (an opaque [PhotoCapture] handle) with its [mode] and dish [comment], or, in
 * [FoodPhotoMode.Text] mode, the typed [text] (then [handle] is null). Exactly one of [handle] and [text] is set.
 */
data class RetryPhoto(val handle: String?, val mode: FoodPhotoMode, val comment: String?, val text: String? = null)

/**
 * In-memory hand-over of a photo from the photo flow to the create-food form, so the AI estimate can be repeated
 * from the form when it fails the energy check (ADR-104). Memory only: the image is never persisted (privacy rule),
 * so after process death the store is empty and a token no longer resolves. [release] deletes the file.
 */
class FoodPhotoRetryStore(private val photoCapture: PhotoCapture) {
    private val held = mutableMapOf<String, RetryPhoto>()

    /** Keeps [handle] for a retry and returns the token the form reads it by. */
    fun put(handle: String, mode: FoodPhotoMode, comment: String?): String = keep(RetryPhoto(handle, mode, comment))

    /** Keeps the typed meal [text] (memory only) for a retry and returns the token the form reads it by. */
    fun putText(text: String): String = keep(RetryPhoto(handle = null, mode = FoodPhotoMode.Text, comment = null, text))

    private fun keep(retry: RetryPhoto): String {
        val token = "retry-${Random.nextLong().toULong().toString(HEX)}"
        held[token] = retry
        return token
    }

    fun get(token: String): RetryPhoto? = held[token]

    fun has(token: String): Boolean = token in held

    /** Forgets [token] and deletes its photo file; unknown tokens are ignored. */
    fun release(token: String) {
        held.remove(token)?.handle?.let(photoCapture::discard)
    }

    private companion object {
        const val HEX = 16
    }
}
