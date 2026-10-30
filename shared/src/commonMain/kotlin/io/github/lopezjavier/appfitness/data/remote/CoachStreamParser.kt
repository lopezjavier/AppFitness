package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Reads a whole SSE body of the `coach` function: `delta` frames carry `{"text":...}`, a `done` frame ends the stream
 * and an `error` frame carries `{"code","scope"?}`. An `error` frame wins over anything before it; a stream without
 * `done` was cut and is [CoachFailure.Other].
 */
internal object CoachStreamParser {
    /** The concatenated `delta` text of a finished stream, or a failed result carrying a [CoachException]. */
    fun parse(body: String): Result<String> {
        val frames = body.replace("\r\n", "\n").split("\n\n").map { frame -> frame.lines() }
        val error = frames.firstOrNull { it.contains("event: error") }
        return when {
            error != null -> Result.failure(CoachException(CoachErrorParser.parse(error.data())))

            frames.none { it.contains("event: done") } -> Result.failure(CoachException(CoachFailure.Other))

            else -> Result.success(
                frames.filter { it.contains("event: delta") }.joinToString("") { deltaText(it.data()) },
            )
        }
    }

    private fun List<String>.data(): String =
        firstOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim().orEmpty()

    private fun deltaText(data: String): String {
        val root = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return ""
        return (root["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
