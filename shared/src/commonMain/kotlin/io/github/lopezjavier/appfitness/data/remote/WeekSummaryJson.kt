package io.github.lopezjavier.appfitness.data.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Tolerant readers: a missing, null or mistyped key is null (or the zero value of the `count`/`truth` readers).

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

private fun JsonObject.prim(key: String): JsonPrimitive? = (get(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }

internal fun JsonObject.text(key: String): String? = prim(key)?.contentOrNull

internal fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull

internal fun JsonObject.number(key: String): Double? = prim(key)?.doubleOrNull

internal fun JsonObject.flag(key: String): Boolean? = prim(key)?.booleanOrNull

internal fun JsonObject.count(key: String): Int = int(key) ?: 0

internal fun JsonObject.truth(key: String): Boolean = flag(key) ?: false
