package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_complete
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_fair
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_good
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_poor
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_days_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_days_one
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_days_other
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_days_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_example_back
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_example_chest
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_example_glutes
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_example_hamstrings
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_example_quads
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_example_shoulders
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_add_none
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_add_one
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_add_other
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_none
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_over
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_sets_one
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_group_sets_other
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_line
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_line
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_title
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val HEAVY_REPS_SUGGESTION_CAP = 6

/** Test-tag suffix of a row: the group key, `days`, `reps` or `order`. */
internal val ReviewItem.tagSuffix: String
    get() = when (this) {
        is ReviewItem.Group -> when (group) {
            MuscleGroup.Chest -> "chest"
            MuscleGroup.Back -> "back"
            MuscleGroup.Shoulders -> "shoulders"
            MuscleGroup.Quads -> "quads"
            MuscleGroup.Hamstrings -> "hamstrings"
            MuscleGroup.Glutes -> "glutes"
            else -> group.name.lowercase()
        }

        is ReviewItem.Days -> "days"

        is ReviewItem.Reps -> "reps"

        is ReviewItem.Order -> "order"
    }

internal val ScoreBand.label: StringResource
    get() = when (this) {
        ScoreBand.Poor -> Res.string.folder_review_result_band_poor
        ScoreBand.Fair -> Res.string.folder_review_result_band_fair
        ScoreBand.Good -> Res.string.folder_review_result_band_good
        ScoreBand.Complete -> Res.string.folder_review_result_band_complete
    }

/** The row's headline (07 section 3 copy): the group name, or the title of the days and strength rows. */
@Composable
internal fun ReviewItem.headline(): String = when (this) {
    is ReviewItem.Group -> stringResource(group.label)
    is ReviewItem.Days -> stringResource(Res.string.folder_review_result_days_title)
    is ReviewItem.Reps -> stringResource(Res.string.folder_review_result_reps_title)
    is ReviewItem.Order -> stringResource(Res.string.folder_review_result_order_title)
}

/** The row's recommendation when unmet, or what it checked when met; strength rows list one line per offender. */
@Composable
internal fun ReviewItem.supporting(): String = when (this) {
    is ReviewItem.Group -> groupLines().joinToString("\n")

    is ReviewItem.Days -> when {
        met -> stringResource(Res.string.folder_review_result_days_met, days)
        days == 1 -> stringResource(Res.string.folder_review_result_days_one)
        else -> stringResource(Res.string.folder_review_result_days_other, days)
    }

    is ReviewItem.Reps -> if (met) {
        stringResource(Res.string.folder_review_result_reps_met)
    } else {
        lines.map { line ->
            val range = if (line.repMin == line.repMax) "${line.repMin}" else "${line.repMin}-${line.repMax}"
            val target = minOf(line.repMin, HEAVY_REPS_SUGGESTION_CAP).toString()
            stringResource(
                Res.string.folder_review_result_reps_line,
                line.exerciseName,
                line.routineName,
                range,
                target,
            )
        }.joinToString("\n")
    }

    is ReviewItem.Order -> if (met) {
        stringResource(Res.string.folder_review_result_order_met)
    } else {
        lines.map { line ->
            stringResource(
                Res.string.folder_review_result_order_line,
                line.routineName,
                line.mainLift,
                line.earlier,
                line.mainLifts.joinToString(", "),
            )
        }.joinToString("\n")
    }
}

/** The group row's lines in order: the figure, if unmet what to add and an example, then the over-20 note. */
@Composable
private fun ReviewItem.Group.groupLines(): List<String> = buildList {
    add(
        when {
            none -> stringResource(Res.string.folder_review_result_group_none)
            sets == 1.0 -> stringResource(Res.string.folder_review_result_group_sets_one, AppNumberFormat.decimal(sets))
            else -> stringResource(Res.string.folder_review_result_group_sets_other, AppNumberFormat.decimal(sets))
        },
    )
    if (!met) {
        add(
            when {
                none -> stringResource(Res.string.folder_review_result_group_add_none)
                missing == 1 -> stringResource(Res.string.folder_review_result_group_add_one)
                else -> stringResource(Res.string.folder_review_result_group_add_other, missing)
            },
        )
        exampleRes()?.let { add(stringResource(it)) }
    }
    if (over) add(stringResource(Res.string.folder_review_result_group_over))
}

private fun ReviewItem.Group.exampleRes(): StringResource? = when (group) {
    MuscleGroup.Chest -> Res.string.folder_review_result_example_chest
    MuscleGroup.Back -> Res.string.folder_review_result_example_back
    MuscleGroup.Shoulders -> Res.string.folder_review_result_example_shoulders
    MuscleGroup.Quads -> Res.string.folder_review_result_example_quads
    MuscleGroup.Hamstrings -> Res.string.folder_review_result_example_hamstrings
    MuscleGroup.Glutes -> Res.string.folder_review_result_example_glutes
    else -> null
}
