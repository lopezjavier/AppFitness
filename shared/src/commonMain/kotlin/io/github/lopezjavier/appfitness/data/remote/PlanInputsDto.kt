package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Params of `coach_plan_inputs`. */
@Serializable
internal data class PlanInputsParams(
    @SerialName("p_kind") val kind: String,
    @SerialName("p_today") val today: LocalDate,
) {
    companion object {
        fun of(kind: PlanInputKind, today: LocalDate) =
            PlanInputsParams(if (kind == PlanInputKind.Plan) "plan" else "block", today)
    }
}

/** Response of `coach_plan_inputs`. Keys, statuses, sources and fix targets stay strings so new ones never crash. */
@Serializable
internal data class PlanInputsDto(
    val kind: String,
    @SerialName("current_plan") val currentPlan: String? = null,
    val inputs: List<PlanInputDto> = emptyList(),
) {
    fun toDomain() = PlanInputs(
        kind = if (kind == "block") PlanInputKind.Block else PlanInputKind.Plan,
        currentPlan = when (currentPlan) {
            "accepted" -> CurrentPlan.Accepted
            "proposed" -> CurrentPlan.Proposed
            else -> null
        },
        inputs = inputs.map { it.toDomain() },
    )
}

@Serializable
internal data class PlanInputDto(
    val key: String,
    val value: JsonElement = JsonNull,
    val source: String? = null,
    val status: String? = null,
    val blocking: Boolean = false,
    @SerialName("fix_target") val fixTarget: String? = null,
    val date: LocalDate? = null,
) {
    fun toDomain() = PlanInput(
        key = KEYS[key] ?: PlanInputKey.Unknown,
        value = value.toPlanValue(),
        source = SOURCES[source] ?: PlanInputSource.Unknown,
        status = STATUSES[status] ?: PlanInputStatus.Unknown,
        blocking = blocking,
        fixTarget = if (fixTarget == "intake") INTAKE_FIX_BY_KEY[key] else FIX_TARGETS[fixTarget],
        date = date,
    )

    private companion object {
        val KEYS = mapOf(
            "intention" to PlanInputKey.Intention,
            "sex" to PlanInputKey.Sex,
            "age" to PlanInputKey.Age,
            "height" to PlanInputKey.Height,
            "weight" to PlanInputKey.Weight,
            "activity_level" to PlanInputKey.ActivityLevel,
            "total_expenditure" to PlanInputKey.TotalExpenditure,
            "weight_trend" to PlanInputKey.WeightTrend,
            "diary" to PlanInputKey.Diary,
            "place" to PlanInputKey.Place,
            "equipment" to PlanInputKey.Equipment,
            "cardio" to PlanInputKey.Cardio,
            "days_per_week" to PlanInputKey.DaysPerWeek,
            "session_minutes" to PlanInputKey.SessionMinutes,
            "experience" to PlanInputKey.Experience,
            "training_years" to PlanInputKey.TrainingYears,
            "known_maintenance" to PlanInputKey.KnownMaintenance,
            "free_text" to PlanInputKey.FreeText,
        )
        val SOURCES = mapOf(
            "profile" to PlanInputSource.Profile,
            "my_training" to PlanInputSource.MyTraining,
            "weigh_in" to PlanInputSource.WeighIn,
            "coach_estimate" to PlanInputSource.CoachEstimate,
            "intake" to PlanInputSource.Intake,
        )
        val STATUSES = mapOf(
            "ok" to PlanInputStatus.Ok,
            "missing" to PlanInputStatus.Missing,
            "unset_optional" to PlanInputStatus.UnsetOptional,
            "provisional" to PlanInputStatus.Provisional,
            "stale" to PlanInputStatus.Stale,
        )
        val FIX_TARGETS = mapOf(
            "my_training" to PlanFixTarget.MyTraining,
            "personal_data" to PlanFixTarget.PersonalData,
            "weight" to PlanFixTarget.Weight,
            "activity_sheet" to PlanFixTarget.ActivitySheet,
        )

        // The server marks the three intake entries with the same fix target; each one opens its own editor.
        val INTAKE_FIX_BY_KEY = mapOf(
            "training_years" to PlanFixTarget.TrainingYears,
            "known_maintenance" to PlanFixTarget.Maintenance,
            "free_text" to PlanFixTarget.FreeText,
        )
    }
}

private fun JsonElement.toPlanValue(): PlanInputValue = when (this) {
    is JsonArray -> PlanInputValue.Items(mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content })

    is JsonPrimitive -> when {
        this is JsonNull -> PlanInputValue.Absent

        isString -> {
            val date = runCatching { LocalDate.parse(content) }.getOrNull()
            if (date != null) PlanInputValue.Date(date) else PlanInputValue.Text(content)
        }

        booleanOrNull != null -> PlanInputValue.Flag(booleanOrNull == true)

        doubleOrNull != null -> PlanInputValue.Number(doubleOrNull ?: 0.0)

        else -> PlanInputValue.Absent
    }

    else -> PlanInputValue.Absent
}
