package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.json.Json

/** JSON form of a [NewFoodDraft], used as a navigation argument. A malformed value decodes to null. */
object NewFoodDraftCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(draft: NewFoodDraft): String = json.encodeToString(NewFoodDraft.serializer(), draft)

    fun decode(text: String?): NewFoodDraft? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(NewFoodDraft.serializer(), text)
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
