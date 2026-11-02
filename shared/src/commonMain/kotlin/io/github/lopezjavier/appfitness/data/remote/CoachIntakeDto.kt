package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val SITUATION_WIRE = mapOf(
    Situation.FirstTime to "first_time",
    Situation.Continuous to "continuous",
    Situation.ReturningBreak to "returning_break",
    Situation.ReturningInjury to "returning_injury",
)

private val AREA_WIRE = mapOf(
    InjuredArea.Shoulder to "shoulder",
    InjuredArea.Elbow to "elbow",
    InjuredArea.Wrist to "wrist",
    InjuredArea.LowerBack to "lower_back",
    InjuredArea.Hip to "hip",
    InjuredArea.Knee to "knee",
    InjuredArea.Ankle to "ankle",
    InjuredArea.Other to "other",
)

private val KIND_WIRE = mapOf(
    FreeTextKind.Intake to "intake",
    FreeTextKind.CheckIn to "check_in",
    FreeTextKind.Discomfort to "discomfort",
    FreeTextKind.Review to "review",
)

private val STATUS_WIRE = mapOf(
    "sent" to FreeTextStatus.Sent,
    "excluded" to FreeTextStatus.Excluded,
    "over_limit" to FreeTextStatus.OverLimit,
)

/** An unknown situation is "not answered", never a crash. */
internal fun String?.toSituation(): Situation? = SITUATION_WIRE.entries.firstOrNull { it.value == this }?.key

/** An unknown area is [InjuredArea.Other] when something was said, null when nothing was. */
private fun String?.toArea(): InjuredArea? =
    if (this == null) null else AREA_WIRE.entries.firstOrNull { it.value == this }?.key ?: InjuredArea.Other

@Serializable
internal data class PreBreakBestDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("weight_kg") val weightKg: Double,
    val reps: Int,
) {
    fun toDomain() = PreBreakBest(exerciseId, weightKg, reps)

    companion object {
        fun of(best: PreBreakBest) = PreBreakBestDto(best.exerciseId, best.weightKg, best.reps)
    }
}

/** A `coach_user_intake` row (owner-select RLS) or the jsonb `save_coach_intake` returns. */
@Serializable
internal data class CoachIntakeDto(
    @SerialName("training_years") val trainingYears: Double? = null,
    val situation: String? = null,
    @SerialName("injured_area") val injuredArea: String? = null,
    @SerialName("pre_break_bests") val preBreakBests: List<PreBreakBestDto> = emptyList(),
    @SerialName("known_maintenance_kcal") val knownMaintenanceKcal: Int? = null,
    @SerialName("known_cut_kcal") val knownCutKcal: Int? = null,
    @SerialName("free_text") val freeText: String? = null,
    @SerialName("maintenance_handover_at") val maintenanceHandoverAt: String? = null,
) {
    fun toDomain() = CoachIntake(
        trainingYears = trainingYears,
        situation = situation.toSituation(),
        injuredArea = injuredArea.toArea(),
        preBreakBests = preBreakBests.map { it.toDomain() },
        knownMaintenanceKcal = knownMaintenanceKcal,
        knownCutKcal = knownCutKcal,
        freeText = freeText,
        maintenanceHandoverPending = knownMaintenanceKcal != null && maintenanceHandoverAt == null,
    )
}

/** Body of `save_coach_intake`: null fields are omitted (the server treats a missing key as empty). */
@Serializable
internal data class IntakePayloadDto(
    @SerialName("training_years") val trainingYears: Double? = null,
    val situation: String? = null,
    @SerialName("injured_area") val injuredArea: String? = null,
    @SerialName("pre_break_bests") val preBreakBests: List<PreBreakBestDto>,
    @SerialName("known_maintenance_kcal") val knownMaintenanceKcal: Int? = null,
    @SerialName("known_cut_kcal") val knownCutKcal: Int? = null,
    @SerialName("free_text") val freeText: String? = null,
)

@Serializable
internal data class SaveIntakeParams(val p: IntakePayloadDto) {
    companion object {
        fun of(intake: CoachIntake) = SaveIntakeParams(
            IntakePayloadDto(
                trainingYears = intake.trainingYears,
                situation = intake.situation?.let(SITUATION_WIRE::getValue),
                injuredArea = intake.injuredArea?.let(AREA_WIRE::getValue),
                preBreakBests = intake.preBreakBests.map(PreBreakBestDto::of),
                knownMaintenanceKcal = intake.knownMaintenanceKcal,
                knownCutKcal = intake.knownCutKcal,
                freeText = intake.freeText,
            ),
        )
    }
}

/** The fields of the extraction request (already filled by the user) and of its answer. */
@Serializable
internal data class IntakeFieldsDto(
    @SerialName("training_years") val trainingYears: Double? = null,
    val situation: String? = null,
    @SerialName("injured_area") val injuredArea: String? = null,
    @SerialName("pre_break_bests") val preBreakBests: List<PreBreakBestDto>? = null,
    @SerialName("known_maintenance_kcal") val knownMaintenanceKcal: Int? = null,
    @SerialName("known_cut_kcal") val knownCutKcal: Int? = null,
) {
    fun toDomain() = CoachIntakeDraft(
        trainingYears = trainingYears,
        situation = situation.toSituation(),
        injuredArea = injuredArea.toArea(),
        preBreakBests = preBreakBests.orEmpty().map { it.toDomain() },
        knownMaintenanceKcal = knownMaintenanceKcal,
        knownCutKcal = knownCutKcal,
    )

    companion object {
        fun of(draft: CoachIntakeDraft) = IntakeFieldsDto(
            trainingYears = draft.trainingYears,
            situation = draft.situation?.let(SITUATION_WIRE::getValue),
            injuredArea = draft.injuredArea?.let(AREA_WIRE::getValue),
            preBreakBests = draft.preBreakBests.takeIf { it.isNotEmpty() }?.map(PreBreakBestDto::of),
            knownMaintenanceKcal = draft.knownMaintenanceKcal,
            knownCutKcal = draft.knownCutKcal,
        )
    }
}

@Serializable
internal data class ExtractInput(@SerialName("free_text") val freeText: String, val fields: IntakeFieldsDto? = null)

@Serializable
internal data class ExtractRequestBody(val kind: String, val input: ExtractInput, val model: String? = null) {
    companion object {
        fun of(freeText: String, fields: CoachIntakeDraft?, model: String?) = ExtractRequestBody(
            kind = "intake_extract",
            input = ExtractInput(freeText, fields?.let(IntakeFieldsDto::of)),
            model = model,
        )
    }
}

/** The `{fields}` answer of the `intake_extract` kind. */
@Serializable
internal data class ExtractResultDto(val fields: IntakeFieldsDto = IntakeFieldsDto()) {
    fun toDomain() = fields.toDomain()
}

@Serializable
internal data class FreeTextLineDto(
    val kind: String,
    @SerialName("source_id") val sourceId: String,
    val date: LocalDate,
    val text: String,
    val status: String,
) {
    fun toDomain() = FreeTextLine(
        kind = KIND_WIRE.entries.firstOrNull { it.value == kind }?.key ?: FreeTextKind.Unknown,
        sourceId = sourceId,
        date = date,
        text = text,
        // An unknown status is never treated as sent: the user must not believe a line is read when it may not be.
        status = STATUS_WIRE[status] ?: FreeTextStatus.OverLimit,
    )
}

@Serializable
internal data class FreeTextLinesParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class ExcludeLineParams(
    @SerialName("p_kind") val kind: String,
    @SerialName("p_id") val id: String,
    @SerialName("p_excluded") val excluded: Boolean,
) {
    companion object {
        fun of(kind: FreeTextKind, id: String, excluded: Boolean) =
            ExcludeLineParams(KIND_WIRE[kind] ?: "unknown", id, excluded)
    }
}
