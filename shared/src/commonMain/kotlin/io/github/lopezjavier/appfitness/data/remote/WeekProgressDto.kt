@file:Suppress("TooManyFunctions") // One small reader per block of the contract.

package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ExerciseWeekProgress
import io.github.lopezjavier.appfitness.domain.model.LiftSeries
import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.LiftWeek
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProgressContext
import io.github.lopezjavier.appfitness.domain.model.ProgressSession
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.RecordType
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.StrengthProgress
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WaistProgress
import io.github.lopezjavier.appfitness.domain.model.WeekProgress
import io.github.lopezjavier.appfitness.domain.model.WeekRecordBadge
import io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend
import io.github.lopezjavier.appfitness.domain.model.WeightOutcome
import io.github.lopezjavier.appfitness.domain.model.WeightPoint
import io.github.lopezjavier.appfitness.domain.model.WeightProgress
import io.github.lopezjavier.appfitness.domain.model.phaseOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.time.Instant

/*
 * Tolerant reader of `progress` in snapshot v2 (`coach_week_progress`, T-313). A missing or non-object `progress`
 * (a v1 snapshot, a paused week) yields null; an unknown state drops that exercise or lift; a missing block hides
 * only that block.
 */

private val PROGRESS_STATES = mapOf(
    "up" to ProgressState.Up,
    "same" to ProgressState.Same,
    "down" to ProgressState.Down,
    "stalled" to ProgressState.Stalled,
    "paused" to ProgressState.Paused,
    "uncompared" to ProgressState.Uncompared,
)

private val LIFT_STATES = mapOf(
    "up" to LiftStatus.Up,
    "margin" to LiftStatus.Margin,
    "stalled" to LiftStatus.Stalled,
    "provisional" to LiftStatus.Provisional,
)

private val RECORD_KINDS = mapOf("e1rm" to RecordType.E1rm, "heaviest" to RecordType.Heaviest)

// `diary` is the server's code for the 5-complete-days gate; `food` is accepted as its alias.
private val MISSING_KINDS = mapOf(
    "weigh_ins" to MissingKind.WeighIns,
    "diary" to MissingKind.Food,
    "food" to MissingKind.Food,
    "deleted_sessions" to MissingKind.DeletedSessions,
    "deleted_weigh_ins" to MissingKind.DeletedWeighIns,
)

private val INTENTIONS = mapOf(
    "strength" to TrainingIntention.Strength,
    "muscle" to TrainingIntention.Muscle,
    "fat_loss" to TrainingIntention.FatLoss,
    "recomposition" to TrainingIntention.Recomposition,
    "maintenance" to TrainingIntention.Maintenance,
)

private fun JsonElement?.items(): List<JsonObject> = ((this as? JsonArray) ?: emptyList<JsonElement>()).mapNotNull {
    it.obj()
}

internal fun progressOf(element: JsonElement?): WeekProgress? {
    val obj = element.obj() ?: return null
    return WeekProgress(
        asOf = obj.day("as_of"),
        refreshedAt = obj.text("refreshed_at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
        final = obj.truth("final"),
        intention = obj.text("intention")?.let(INTENTIONS::get),
        phase = phaseOrNull(obj.text("phase")),
        missing = ((obj["missing"] as? JsonArray) ?: emptyList<JsonElement>())
            .mapNotNull { (it as? JsonPrimitive)?.content?.let(MISSING_KINDS::get) }
            .distinct(),
        context = contextOf(obj["context"].obj()),
        strength = strengthOf(obj["strength"].obj()),
        lifts = obj["lifts"].items().mapNotNull(::liftOf),
        records = obj["records"].items().mapNotNull(::recordOf),
        weight = weightProgressOf(obj["weight"].obj()),
        waist = waistOf(obj["waist"].obj()),
    )
}

private fun contextOf(obj: JsonObject?) = ProgressContext(
    sessionsDone = obj?.count("sessions_done") ?: 0,
    sessionsPlanned = obj?.count("sessions_planned") ?: 0,
    setsDone = obj?.count("sets_done") ?: 0,
    setsPlanned = obj?.int("sets_planned"),
)

private fun strengthOf(obj: JsonObject?) = StrengthProgress(
    blockWeek = obj?.count("block_week") ?: 0,
    groups = obj?.get("groups").items().mapNotNull(::groupOf),
)

private fun groupOf(obj: JsonObject): StrengthGroup? {
    val group = obj.text("muscle_group")?.let { code ->
        runCatching { coachPlanJson.decodeFromJsonElement(MuscleGroup.serializer(), JsonPrimitive(code)) }.getOrNull()
    }
    return group?.let { StrengthGroup(it, obj["exercises"].items().mapNotNull(::exerciseOf)) }
}

private fun exerciseOf(obj: JsonObject): ExerciseWeekProgress? {
    val id = obj.text("exercise_id")
    val state = obj.text("state")?.let(PROGRESS_STATES::get)
    return if (id == null || state == null) {
        null
    } else {
        ExerciseWeekProgress(
            exerciseId = id,
            mainLift = obj.truth("main_lift"),
            state = state,
            baseline = sessionOf(obj["baseline"].obj()),
            best = sessionOf(obj["best"].obj()),
            discomfortId = obj.text("discomfort_id"),
        )
    }
}

private fun sessionOf(obj: JsonObject?): ProgressSession? {
    val load = obj?.number("load_kg")
    return if (obj == null || load == null) {
        null
    } else {
        ProgressSession(
            date = obj.day("date"),
            loadKg = load,
            reps = ((obj["reps"] as? JsonArray) ?: emptyList<JsonElement>())
                .mapNotNull { (it as? JsonPrimitive)?.intOrNull },
            rpe = obj.number("rpe"),
        )
    }
}

private fun liftOf(obj: JsonObject): LiftSeries? {
    val id = obj.text("exercise_id")
    val state = obj.text("state")?.let(LIFT_STATES::get)
    return if (id == null || state == null) {
        null
    } else {
        LiftSeries(
            exerciseId = id,
            weeks = obj["weeks"].items().mapNotNull { week ->
                week.int("week")?.let { LiftWeek(it, week.number("e1rm_kg"), week.truth("deload")) }
            },
            state = state,
            changeKg = obj.number("change_kg"),
            changePct = obj.number("change_pct"),
        )
    }
}

@Suppress("ReturnCount") // One guard per required figure.
private fun recordOf(obj: JsonObject): WeekRecordBadge? {
    val id = obj.text("exercise_id") ?: return null
    val kind = obj.text("kind")?.let(RECORD_KINDS::get) ?: return null
    val value = obj.number("value_kg") ?: return null
    val previous = obj.number("previous_kg") ?: return null
    return WeekRecordBadge(id, kind, value, previous, obj.truth("main_lift"))
}

private fun weightProgressOf(obj: JsonObject?): WeightProgress {
    if (obj == null) return WeightProgress.Empty
    val outcome = obj["outcome"].obj()
    val trend = obj["trend"].obj()
    val outcomeKg = outcome?.number("kg")
    val deadline = outcome?.day("deadline")
    return WeightProgress(
        avgKg = obj.number("avg_kg"),
        days = obj.count("days"),
        prevAvgKg = obj.number("prev_avg_kg"),
        prevDays = obj.count("prev_days"),
        changeKg = obj.number("change_kg"),
        startAvgKg = obj.number("start_avg_kg"),
        phaseWeeks = obj.count("phase_weeks"),
        changeSinceStartKg = obj.number("change_since_start_kg"),
        series = obj["series"].items().mapNotNull { point ->
            point.number("avg_kg")?.let { WeightPoint(point.day("start_date"), it, point.count("days")) }
        },
        outcome = if (outcomeKg != null && deadline != null) WeightOutcome(outcomeKg, deadline) else null,
        trend = trend?.let {
            WeekWeightTrend(it.number("slope"), it.number("low"), it.number("high"), it.count("weeks"))
        },
        rateKgWeek = obj.number("rate_kg_week"),
    )
}

@Suppress("ReturnCount") // One guard per required figure.
private fun waistOf(obj: JsonObject?): WaistProgress? {
    if (obj == null) return null
    val cm = obj.number("cm") ?: return null
    val date = obj.day("date") ?: return null
    val refCm = obj.number("ref_cm") ?: return null
    val refDate = obj.day("ref_date") ?: return null
    val change = obj.number("change_cm") ?: return null
    return WaistProgress(cm, date, refCm, refDate, change)
}
