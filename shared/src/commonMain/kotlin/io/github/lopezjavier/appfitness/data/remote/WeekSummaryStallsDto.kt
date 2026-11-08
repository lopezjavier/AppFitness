package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

private fun muscleGroupOf(code: String?): MuscleGroup? = code?.let {
    runCatching { coachPlanJson.decodeFromJsonElement(MuscleGroup.serializer(), JsonPrimitive(it)) }.getOrNull()
}

private fun causeOf(obj: JsonObject): StallCause? = when (obj.text("cause")) {
    "sessions" -> obj.number("avg_sessions")?.let { StallCause.Sessions(it, obj.count("plan_days")) }
    "sets" -> setsCauseOf(obj)
    "maintain" -> muscleGroupOf(obj.text("group"))?.let { StallCause.GroupOnMaintain(it) }
    "protein" -> proteinCauseOf(obj)
    "sleep" -> obj.number("avg_sleep_h")?.let { StallCause.Sleep(it) }
    "none" -> StallCause.NoClearCause(obj.truth("protein_gate_missing"))
    else -> null
}

private fun setsCauseOf(obj: JsonObject): StallCause? {
    val avg = obj.number("avg_sets")
    val group = muscleGroupOf(obj.text("group"))
    return if (avg == null || group == null) null else StallCause.Sets(avg, group)
}

private fun proteinCauseOf(obj: JsonObject): StallCause? {
    val grams = obj.number("avg_protein_g")
    val perKg = obj.number("protein_g_per_kg")
    val target = obj.number("target_g")
    return if (grams == null || perKg == null || target == null) {
        null
    } else {
        StallCause.Protein(grams.roundToInt(), perKg, target.roundToInt())
    }
}

/** Stalls of an old row (no key) or with an unknown cause or missing figures are dropped, never an error. */
internal fun stallsOf(element: JsonElement?): List<StallDiagnosis> =
    ((element as? JsonArray) ?: emptyList<JsonElement>()).mapNotNull { item ->
        val obj = item.obj()
        val id = obj?.text("exercise_id")
        val cause = obj?.let(::causeOf)
        if (id == null || cause == null) null else StallDiagnosis(id, cause)
    }
