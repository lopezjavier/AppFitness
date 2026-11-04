@file:Suppress("TooManyFunctions") // One small text formatter per builder concept.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_pick_already
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_pick_no_pattern
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_reps_range
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_reps_single
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_rest_min
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_rest_min_s
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_rest_s
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_section_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_section_main
import io.github.lopezjavier.appfitness.composeapp.resources.block_sets_count
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_reason_only_routine
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_reason_three_times
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_reason_three_times_short
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_reason_three_times_similar
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_reason_twice_a_week
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_reason_twice_three_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_every_day
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_back
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_chest
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_glutes
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_hamstrings
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_other
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_quads
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_group_shoulders
import io.github.lopezjavier.appfitness.composeapp.resources.block_type_leaves_untrained
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderDay
import io.github.lopezjavier.appfitness.domain.coach.builder.DisabledReason
import io.github.lopezjavier.appfitness.domain.coach.builder.FilledSlot
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerDisabledReason
import io.github.lopezjavier.appfitness.domain.coach.builder.Slot
import io.github.lopezjavier.appfitness.domain.coach.builder.SlotKind
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitReason
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val SECONDS_PER_MINUTE = 60

/** One section of a builder day: consecutive required slots of one muscle group (the main lift stands apart). */
internal data class BuilderSection(
    val group: MuscleGroup,
    val main: Boolean,
    val slots: List<IndexedValue<FilledSlot>>,
) {
    val sets: Int get() = slots.sumOf { it.value.slot.sets }
    val first: Slot get() = slots.first().value.slot

    /** Tag suffix of the section: `chest`, `chest_main`. */
    val key: String get() = group.name.lowercase() + if (main) "_main" else ""
}

/** The sections of this day in slot order; the slot indices stay those of [BuilderDay.slots]. */
internal fun BuilderDay.sections(): List<BuilderSection> {
    val result = mutableListOf<BuilderSection>()
    slots.withIndex().forEach { indexed ->
        val slot = indexed.value.slot
        val main = slot.kind == SlotKind.Main
        val last = result.lastOrNull()
        if (last != null && last.group == slot.group && last.main == main) {
            result[result.lastIndex] = last.copy(slots = last.slots + indexed)
        } else {
            result += BuilderSection(slot.group, main, listOf(indexed))
        }
    }
    return result
}

/** "Series" text of [sets] ("3 series", "1 serie"). */
@Composable
internal fun setsText(sets: Int): String = pluralStringResource(Res.plurals.block_sets_count, sets, sets)

@Composable
private fun repsText(slot: Slot): String = if (slot.repMin == slot.repMax) {
    stringResource(Res.string.block_builder_reps_single, slot.repMin)
} else {
    stringResource(Res.string.block_builder_reps_range, slot.repMin, slot.repMax)
}

@Composable
private fun restText(seconds: Int): String = when {
    seconds < SECONDS_PER_MINUTE -> stringResource(Res.string.block_builder_rest_s, seconds)

    seconds % SECONDS_PER_MINUTE == 0 -> stringResource(Res.string.block_builder_rest_min, seconds / SECONDS_PER_MINUTE)

    else -> stringResource(
        Res.string.block_builder_rest_min_s,
        seconds / SECONDS_PER_MINUTE,
        seconds % SECONDS_PER_MINUTE,
    )
}

/** The section's name: the muscle group, or "Básico" for the main lift. */
@Composable
internal fun sectionTitle(section: BuilderSection): String = if (section.main) {
    stringResource(Res.string.block_builder_section_main)
} else {
    stringResource(section.group.label)
}

/** "Pecho · 5 series · 8-12 repeticiones · RPE 8 · descanso 2 min". */
@Composable
internal fun sectionHeaderText(section: BuilderSection): String = stringResource(
    Res.string.block_builder_section_header,
    sectionTitle(section),
    setsText(section.sets),
    repsText(section.first),
    section.first.rpe,
    restText(section.first.restSeconds),
)

/** "62,5" for a planned weight. */
internal fun weightText(kg: Double): String = AppNumberFormat.decimal(kg)

@Composable
internal fun splitReasonText(reason: SplitReason): String = stringResource(
    when (reason) {
        SplitReason.OnlyRoutineThatTrainsEveryMuscle -> Res.string.block_split_reason_only_routine
        SplitReason.EveryMuscleTwiceAWeek -> Res.string.block_split_reason_twice_a_week
        SplitReason.EveryMuscleThreeTimesSimilarLength -> Res.string.block_split_reason_three_times_similar
        SplitReason.EveryMuscleTwiceThreeExercises -> Res.string.block_split_reason_twice_three_exercises
        SplitReason.EveryMuscleThreeTimes -> Res.string.block_split_reason_three_times
        SplitReason.EveryMuscleThreeTimesShortSessions -> Res.string.block_split_reason_three_times_short
    },
)

@Composable
private fun groupWithArticle(group: MuscleGroup): String = stringResource(
    when (group) {
        MuscleGroup.Chest -> Res.string.block_type_group_chest
        MuscleGroup.Back -> Res.string.block_type_group_back
        MuscleGroup.Shoulders -> Res.string.block_type_group_shoulders
        MuscleGroup.Quads -> Res.string.block_type_group_quads
        MuscleGroup.Hamstrings -> Res.string.block_type_group_hamstrings
        MuscleGroup.Glutes -> Res.string.block_type_group_glutes
        else -> Res.string.block_type_group_other
    },
)

/** "Dejaría el pecho sin entrenar" for a day type that cannot be chosen. */
@Composable
internal fun disabledReasonText(reason: DisabledReason): String = when (reason) {
    is DisabledReason.LeavesGroupUntrained ->
        stringResource(Res.string.block_type_leaves_untrained, groupWithArticle(reason.group))

    is DisabledReason.GroupEveryDay -> stringResource(Res.string.block_type_every_day, groupWithArticle(reason.group))
}

/** Why a picker row cannot be chosen. */
@Composable
internal fun pickerReasonText(reason: PickerDisabledReason): String = stringResource(
    when (reason) {
        PickerDisabledReason.AlreadyInRoutine -> Res.string.block_builder_pick_already
        PickerDisabledReason.NoMovementPattern -> Res.string.block_builder_pick_no_pattern
    },
)
