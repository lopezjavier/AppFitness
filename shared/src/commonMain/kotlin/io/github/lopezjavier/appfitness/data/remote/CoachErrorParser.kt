package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.MissingField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses the `coach` function's error: the JSON body `{"error":{"code","scope"?,"missing"?}}` sent before
 * streaming, or the `{"code":...}` data of an SSE `error` event. Anything unreadable is [CoachFailure.Other].
 */
object CoachErrorParser {
    private val MISSING_FIELDS = mapOf(
        "intention" to MissingField.Intention,
        "sex" to MissingField.Sex,
        "weight" to MissingField.Weight,
        "expenditure" to MissingField.Expenditure,
        "place" to MissingField.Place,
    )

    fun parse(body: String): CoachFailure {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return CoachFailure.Other
        val error = root["error"] as? JsonObject ?: root
        return when (error.string("code")) {
            "coach_limit_reached" -> CoachFailure.LimitReached(
                if (error.string("scope") == "global") CoachFailure.Scope.Global else CoachFailure.Scope.User,
            )

            "coach_no_credit" -> CoachFailure.NoCredit

            "coach_timeout" -> CoachFailure.Timeout

            "coach_invalid_output" -> CoachFailure.Invalid

            "coach_missing_data" -> CoachFailure.Missing(error.missingFields())

            "coach_plan_infeasible" -> CoachFailure.Infeasible

            else -> CoachFailure.Other
        }
    }

    private fun JsonObject.missingFields(): Set<MissingField> = (get("missing") as? JsonArray).orEmpty()
        .mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
        .mapNotNull { MISSING_FIELDS[it] }
        .toSet()

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
