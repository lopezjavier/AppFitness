package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_empty
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_month_avg_title
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_month_days
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_month_total
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_unit_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_week_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_week_title
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.DiaryHistoryRow
import io.github.lopezjavier.appfitness.designsystem.HistoryTargets
import io.github.lopezjavier.appfitness.designsystem.HistoryTotals
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.usecase.MonthSummary
import io.github.lopezjavier.appfitness.domain.usecase.PeriodAverage
import io.github.lopezjavier.appfitness.feature.workout.sessionDateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private fun DiaryGoals.toTargets() = HistoryTargets(targetKcal, proteinG, carbsG, fatG)

private fun MealTotals.toHistory() = HistoryTotals(kcal, proteinG, carbsG, fatG)

/** The summary of the panel the user chose: the selected day, the last 7 days or the viewed month. */
@Composable
internal fun DiaryHistorySummary(
    content: DiaryHistoryContent,
    onEvent: (DiaryHistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets = content.goals?.toTargets()
    when (content.view) {
        DiaryHistoryView.Day -> content.selectedDay?.let { day ->
            DiaryHistoryRow(
                title = day.label(content.today),
                totals = day.totals?.toHistory(),
                targets = targets,
                emptyText = stringResource(Res.string.diary_history_empty),
                modifier = modifier,
                onClick = { onEvent(DiaryHistoryEvent.OpenDay) },
            )
        }

        DiaryHistoryView.Week -> WeekSummary(content.week, targets, modifier)

        DiaryHistoryView.Month -> MonthSummaryCards(content.monthSummary, targets, modifier)
    }
}

@Composable
private fun DiaryHistoryDay.label(today: LocalDate): String = sessionDateLabel(date, today).text()

@Composable
private fun WeekSummary(week: PeriodAverage?, targets: HistoryTargets?, modifier: Modifier = Modifier) {
    Box(modifier.testTag("diary_history_week_summary")) {
        DiaryHistoryRow(
            title = stringResource(Res.string.diary_history_week_title),
            subtitle = week?.let { stringResource(Res.string.diary_history_week_subtitle, it.loggedDays) },
            totals = week?.let { HistoryTotals(it.kcal, it.proteinG, it.carbsG, it.fatG) },
            targets = targets,
            emptyText = stringResource(Res.string.diary_history_empty),
        )
    }
}

@Composable
private fun MonthSummaryCards(summary: MonthSummary?, targets: HistoryTargets?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("diary_history_month_summary"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (summary != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                MetricTile(
                    label = stringResource(Res.string.diary_history_month_total),
                    value = AppNumberFormat.integer(summary.totalKcal.roundToInt()),
                    unit = stringResource(Res.string.diary_history_unit_kcal),
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(Res.string.diary_history_month_days),
                    value = summary.loggedDays.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        DiaryHistoryRow(
            title = stringResource(Res.string.diary_history_month_avg_title),
            totals = summary?.let { HistoryTotals(it.avgKcalPerLoggedDay, it.avgProteinG, it.avgCarbsG, it.avgFatG) },
            targets = targets,
            emptyText = stringResource(Res.string.diary_history_empty),
        )
    }
}
