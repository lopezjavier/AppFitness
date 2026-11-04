package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_all_ok
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_header
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_met
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_n1_floor
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_n2_deficit
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_n2_rate
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_n3_protein
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_n4_fat
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_ok_n1
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_ok_n2
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_ok_n3
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_ok_n4
import io.github.lopezjavier.appfitness.composeapp.resources.goals_check_unmet
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_warning
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.GoalCheck
import io.github.lopezjavier.appfitness.domain.usecase.GoalCheckKey
import io.github.lopezjavier.appfitness.domain.usecase.GoalChecklist
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Tus objetivos": the N1-N4 facts on the goals the user edited (planning/07 section 4). Read-only rows, no score;
 * when everything passes only the one-line confirmation shows. The caller decides when it is shown (edited goals).
 */
@Composable
internal fun GoalsChecklistSection(checklist: GoalChecklist, modifier: Modifier = Modifier) {
    Column(modifier.testTag("trainer_plan_goals_check")) {
        ListSectionHeader(stringResource(Res.string.goals_check_header))
        if (checklist.allPassed) {
            InlineNote(
                text = stringResource(Res.string.goals_check_all_ok),
                testTag = "trainer_plan_goals_check_all_ok",
            )
        } else {
            checklist.checks.forEach { GoalCheckRow(it) }
        }
    }
}

@Composable
private fun GoalCheckRow(check: GoalCheck) {
    val stateText = stringResource(if (check.passed) Res.string.goals_check_met else Res.string.goals_check_unmet)
    AppListRow(
        headline = checkText(check),
        leading = {
            Icon(
                painter = painterResource(if (check.passed) Res.drawable.ic_check else Res.drawable.ic_warning),
                contentDescription = null,
                tint = if (check.passed) AppTheme.colors.neonLime else AppTheme.colors.error,
                modifier = Modifier.size(Spacing.xl),
            )
        },
        modifier = Modifier
            .testTag("trainer_plan_goals_check_${check.key.tagSuffix()}")
            .semantics { stateDescription = stateText },
    )
}

@Composable
private fun checkText(check: GoalCheck): String = if (check.passed) {
    stringResource(
        when (check.key) {
            GoalCheckKey.N1Floor -> Res.string.goals_check_ok_n1
            GoalCheckKey.N2Rate -> Res.string.goals_check_ok_n2
            GoalCheckKey.N3Protein -> Res.string.goals_check_ok_n3
            GoalCheckKey.N4Fat -> Res.string.goals_check_ok_n4
        },
    )
} else {
    failureText(check)
}

@Composable
private fun failureText(check: GoalCheck): String = when (check.key) {
    GoalCheckKey.N1Floor -> stringResource(
        Res.string.goals_check_n1_floor,
        whole(check.kcal),
        whole(check.floorKcal),
    )

    GoalCheckKey.N2Rate -> rateText(check)

    GoalCheckKey.N3Protein -> stringResource(
        Res.string.goals_check_n3_protein,
        whole(check.grams),
        whole(check.minGrams),
    )

    GoalCheckKey.N4Fat -> stringResource(Res.string.goals_check_n4_fat, whole(check.grams), whole(check.minGrams))
}

@Composable
private fun rateText(check: GoalCheck): String {
    val rate = stringResource(
        Res.string.goals_check_n2_rate,
        whole(check.kcal),
        tenths(check.ratePct),
        tenths(check.rangeFromPct),
        tenths(check.rangeToPct),
    )
    return if (check.deficitOverCap) rate + " " + stringResource(Res.string.goals_check_n2_deficit) else rate
}

private fun whole(value: Int?): String = AppNumberFormat.integer(value ?: 0)

private fun tenths(value: Double?): String = AppNumberFormat.decimal(value ?: 0.0, RATE_DECIMALS)

private fun GoalCheckKey.tagSuffix(): String = when (this) {
    GoalCheckKey.N1Floor -> "n1_floor"
    GoalCheckKey.N2Rate -> "n2_rate"
    GoalCheckKey.N3Protein -> "n3_protein"
    GoalCheckKey.N4Fat -> "n4_fat"
}

private const val RATE_DECIMALS = 1
