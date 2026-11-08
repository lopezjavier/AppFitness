package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import kotlinx.datetime.LocalDate

/** The section of "Tus datos" an input belongs to, by where its value comes from. */
enum class PlanInputOrigin { Profile, MyTraining, Weighing, Estimate }

/** What the screens show next to an input: a blocking flag, a caution, an optional gap or the provisional chip. */
enum class PlanInputMarker {
    /** Nothing to flag. */
    None,

    /** Missing and the plan cannot be created without it ("Falta:"). */
    Flag,

    /** Missing but the coach can estimate it: a caution note, not a flag. */
    NoData,

    /** Old weigh-in: a caution note with the date. */
    Stale,

    /** Left for the coach to choose ("Sin definir · Lo elige el entrenador"). */
    Optional,

    /** An estimate the user has not confirmed yet. */
    Provisional,
}

/** One input of "Antes de empezar" / "Tus datos", already classified; the UI only formats the text. */
data class PlanInputRow(
    val key: PlanInputKey,
    val value: PlanInputValue,
    val source: PlanInputSource,
    val origin: PlanInputOrigin,
    val marker: PlanInputMarker,
    val fixTarget: PlanFixTarget?,
    /** Date of the last weigh-in; only the weight row has one. */
    val date: LocalDate?,
    /** True when the server refuses to accept a plan while this input is provisional. */
    val blocksAccept: Boolean = false,
) {
    val isFlag: Boolean get() = marker == PlanInputMarker.Flag
    val isProvisional: Boolean get() = marker == PlanInputMarker.Provisional

    /** True when there is no value to show ("Sin definir"). */
    val isEmpty: Boolean
        get() = when (value) {
            PlanInputValue.Absent -> true
            is PlanInputValue.Items -> value.items.isEmpty()
            else -> false
        }
}

fun PlanInput.toRow(): PlanInputRow = PlanInputRow(
    key = key,
    value = value,
    source = source,
    origin = source.toOrigin(),
    marker = markerOf(),
    fixTarget = fixTarget,
    date = date,
    blocksAccept = blocksAccept,
)

private fun PlanInput.markerOf(): PlanInputMarker = when (status) {
    PlanInputStatus.Missing -> if (blocking) PlanInputMarker.Flag else PlanInputMarker.NoData
    PlanInputStatus.Stale -> PlanInputMarker.Stale
    PlanInputStatus.UnsetOptional -> PlanInputMarker.Optional
    PlanInputStatus.Provisional -> PlanInputMarker.Provisional
    PlanInputStatus.Ok, PlanInputStatus.Unknown -> PlanInputMarker.None
}

private fun PlanInputSource.toOrigin(): PlanInputOrigin = when (this) {
    PlanInputSource.Profile -> PlanInputOrigin.Profile
    PlanInputSource.MyTraining -> PlanInputOrigin.MyTraining
    PlanInputSource.WeighIn -> PlanInputOrigin.Weighing
    PlanInputSource.CoachEstimate, PlanInputSource.Intake, PlanInputSource.Unknown -> PlanInputOrigin.Estimate
}

/** The intake entries have their own rows (see [IntakeRowKind]); they are not input rows. */
fun PlanInputs.rows(): List<PlanInputRow> = inputs.filter { it.key !in INTAKE_KEYS }.map { it.toRow() }

private val INTAKE_KEYS = setOf(PlanInputKey.TrainingYears, PlanInputKey.KnownMaintenance, PlanInputKey.FreeText)

/**
 * The summary lines of "Antes de empezar": each line shows its keys joined ("Sexo y edad"). Plan: goal, sex and age,
 * height and weight, activity level. Block: goal, place, equipment, days per week, session length.
 */
fun PlanInputKind.summaryLines(): List<List<PlanInputKey>> = when (this) {
    PlanInputKind.Plan -> listOf(
        listOf(PlanInputKey.Intention),
        listOf(PlanInputKey.Sex, PlanInputKey.Age),
        listOf(PlanInputKey.Height, PlanInputKey.Weight),
        listOf(PlanInputKey.ActivityLevel),
    )

    PlanInputKind.Block -> listOf(
        listOf(PlanInputKey.Intention),
        listOf(PlanInputKey.Place),
        listOf(PlanInputKey.Equipment),
        listOf(PlanInputKey.Cardio),
        listOf(PlanInputKey.DaysPerWeek),
        listOf(PlanInputKey.SessionMinutes),
    )
}

/** Merges the plan and block lists for "Tus datos": one row per key, the plan's row wins; [Unknown] keys all stay. */
fun mergeRows(plan: List<PlanInputRow>, block: List<PlanInputRow>): List<PlanInputRow> {
    val seen = mutableSetOf<PlanInputKey>()
    return (plan + block).filter { it.key == PlanInputKey.Unknown || seen.add(it.key) }
}
