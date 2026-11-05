package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_complete
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_fair
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_good
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_band_poor
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_days_help
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
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_help
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_line
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_order_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_help
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_line
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_met
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_reps_title
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_row_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_result_unmet
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_warning
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
            stringResource(
                Res.string.folder_review_result_reps_line,
                line.exerciseName,
                line.routineName,
                range,
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

/** The group row shows only the figure (R-57); the advice lives behind the row's `?` ([helpText]). */
@Composable
private fun ReviewItem.Group.groupLines(): List<String> = listOf(
    when {
        none -> stringResource(Res.string.folder_review_result_group_none)
        sets == 1.0 -> stringResource(Res.string.folder_review_result_group_sets_one, AppNumberFormat.decimal(sets))
        else -> stringResource(Res.string.folder_review_result_group_sets_other, AppNumberFormat.decimal(sets))
    },
)

/**
 * The text behind a row's `?` (R-57), or null when the row has none: for a group, if unmet what to add and an example,
 * then the over-20 note; for days, reps and order, the advice of each offender. One function for "Revisar tus
 * rutinas" and the folder block's "Qué falta".
 */
@Composable
internal fun ReviewItem.helpText(): String? = when (this) {
    is ReviewItem.Group -> groupHelpText()

    is ReviewItem.Days -> if (met) null else stringResource(Res.string.folder_review_result_days_help)

    is ReviewItem.Reps -> lines.map { line ->
        val target = minOf(line.repMin, HEAVY_REPS_SUGGESTION_CAP).toString()
        stringResource(Res.string.folder_review_result_reps_help, line.exerciseName, target)
    }.takeIf { it.isNotEmpty() }?.joinToString("\n")

    is ReviewItem.Order -> lines.map { line ->
        stringResource(
            Res.string.folder_review_result_order_help,
            line.routineName,
            line.mainLifts.joinToString(", "),
        )
    }.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

@Composable
private fun ReviewItem.Group.groupHelpText(): String? {
    val lines = buildList {
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
    return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/**
 * A read-only checklist row (state spoken as "Cumplido" / "Por mejorar") with a `?` in the trailing slot when it has
 * [helpText]. Tags: `<tagPrefix><suffix>` and `<tagPrefix><suffix>_help`.
 */
@Composable
internal fun ReviewListRow(item: ReviewItem, tagPrefix: String, modifier: Modifier = Modifier) {
    val stateText = stringResource(
        if (item.met) Res.string.folder_review_result_met else Res.string.folder_review_result_unmet,
    )
    val help = item.helpText()
    val headline = item.headline()
    AppListRow(
        headline = headline,
        supporting = item.supporting(),
        leading = {
            Icon(
                painter = painterResource(if (item.met) Res.drawable.ic_check else Res.drawable.ic_warning),
                contentDescription = null,
                tint = if (item.met) AppTheme.colors.neonLime else AppTheme.colors.error,
                modifier = Modifier.size(Spacing.xl),
            )
        },
        trailing = help?.let {
            {
                HelpButton(
                    contentDescription = stringResource(Res.string.folder_review_result_row_help_cd, headline),
                    title = headline,
                    body = it,
                    testTag = "$tagPrefix${item.tagSuffix}_help",
                )
            }
        },
        modifier = modifier
            .testTag("$tagPrefix${item.tagSuffix}")
            .semantics { stateDescription = stateText },
    )
}

/** The review row of [this] group as the checklist shows it. */
internal fun GroupRow.toReviewItem(): ReviewItem.Group =
    ReviewItem.Group(group, sets, missingSets, isMissing, over, met)

private fun ReviewItem.Group.exampleRes(): StringResource? = when (group) {
    MuscleGroup.Chest -> Res.string.folder_review_result_example_chest
    MuscleGroup.Back -> Res.string.folder_review_result_example_back
    MuscleGroup.Shoulders -> Res.string.folder_review_result_example_shoulders
    MuscleGroup.Quads -> Res.string.folder_review_result_example_quads
    MuscleGroup.Hamstrings -> Res.string.folder_review_result_example_hamstrings
    MuscleGroup.Glutes -> Res.string.folder_review_result_example_glutes
    else -> null
}
