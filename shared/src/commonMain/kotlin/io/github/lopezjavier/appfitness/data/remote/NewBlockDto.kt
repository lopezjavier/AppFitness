package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.GoalChange
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

@Serializable
internal data class OpenBlockNewParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class WeeksInPhaseParams(@SerialName("p_today") val today: LocalDate)

@Serializable
internal data class GoalSwitchRequestBody(val kind: String, val input: GoalSwitchRequestInput)

@Serializable
internal data class GoalSwitchRequestInput(val today: LocalDate)

private fun goalChangeOf(obj: JsonObject?) = GoalChange(
    changed = obj?.truth("changed") ?: false,
    planIntention = obj?.text("plan_intention"),
    savedIntention = obj?.text("saved_intention"),
    currentKcal = obj?.number("current_kcal")?.roundToInt(),
)

/**
 * Reads the `open_coach_block_new` answer: JSON null, a non-object body or a review without plan or end date yield
 * null; a missing goal reads as "not changed".
 */
internal fun newBlockEntryOf(element: JsonElement): NewBlockEntry? {
    val obj = element.obj()
    val review = obj?.get("review").obj()?.let(::reviewBodyOf)
    return if (obj == null || review == null) null else NewBlockEntry(review, goalChangeOf(obj["goal"].obj()))
}

/** Reads the plan event of `plan_goal_switch`; null when the plan id is missing. */
internal fun goalSwitchPreviewOf(element: JsonElement): GoalSwitchPreview? {
    val obj = element.obj()
    val planId = obj?.text("plan_id")
    val preview = obj?.get("preview").obj()
    return planId?.let {
        GoalSwitchPreview(
            planId = it,
            currentKcal = preview?.number("current_kcal")?.roundToInt(),
            newKcal = preview?.number("new_kcal")?.roundToInt(),
            phase = phaseNamed(preview?.text("phase")),
        )
    }
}

/** Reads `coach_weeks_in_phase`; null for JSON null or an unknown phase. */
internal fun phaseWeeksOf(element: JsonElement): PhaseWeeks? {
    val obj = element.obj()
    val phase = phaseNamed(obj?.text("phase"))
    return if (obj == null || phase == null) null else PhaseWeeks(phase, obj.count("weeks"))
}
