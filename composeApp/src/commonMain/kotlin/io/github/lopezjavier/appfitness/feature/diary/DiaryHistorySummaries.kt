package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_complete_days
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_empty
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_month_title
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_no_complete_days
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_week_title
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_month_chart_title
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_week_chart_title
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_weekday_initials
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DiaryHistoryRow
import io.github.lopezjavier.appfitness.designsystem.HistoryTargets
import io.github.lopezjavier.appfitness.designsystem.HistoryTotals
import io.github.lopezjavier.appfitness.designsystem.MiniBarChart
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.feature.workout.sessionDateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
                showSplit = true,
            )
        }

        DiaryHistoryView.Week -> WeekSummary(content, targets, modifier)

        DiaryHistoryView.Month -> MonthSummaryCards(content, targets, modifier)
    }
}

@Composable
private fun DiaryHistoryDay.label(today: LocalDate): String = sessionDateLabel(date, today).text()

@Composable
private fun WeekSummary(content: DiaryHistoryContent, targets: HistoryTargets?, modifier: Modifier = Modifier) {
    val week = content.week
    val completeDays = week?.completeDays ?: 0
    Box(modifier.testTag("diary_history_week_summary")) {
        DiaryHistoryRow(
            title = stringResource(Res.string.diary_history_week_title),
            totals = week?.takeIf {
                it.completeDays > 0
            }?.let { HistoryTotals(it.kcal, it.proteinG, it.carbsG, it.fatG) },
            targets = targets,
            emptyText = emptyText(week?.loggedDays ?: 0),
            subtitle = completeDaysLine(completeDays),
            subtitleTestTag = "diary_history_complete_count",
            chart = {
                val initials = stringResource(Res.string.px_body_weekday_initials).split(",")
                KcalBars(
                    title = stringResource(Res.string.px_body_week_chart_title),
                    values = content.weekKcal,
                    // The 7 counted days before today, then today: drawn but unlit until the day ends.
                    labels = (WEEK_DAYS downTo 0).map {
                        initials[content.today.minus(it, DateTimeUnit.DAY).dayOfWeek.isoDayNumber - 1]
                    },
                    target = targets?.kcal,
                    unlitIndices = setOf(WEEK_DAYS),
                )
            },
        )
    }
}

@Composable
private fun MonthSummaryCards(content: DiaryHistoryContent, targets: HistoryTargets?, modifier: Modifier = Modifier) {
    val summary = content.monthSummary
    val month = content.month
    Column(
        modifier.fillMaxWidth().testTag("diary_history_month_summary"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        DiaryHistoryRow(
            // The card names the month it averages ("octubre 2026 · media diaria", C-157).
            title = stringResource(Res.string.diary_history_month_title, AppDateFormat.month(month.firstDay)),
            totals = summary?.takeIf { it.completeDays > 0 }
                ?.let { HistoryTotals(it.avgKcalPerCompleteDay, it.avgProteinG, it.avgCarbsG, it.avgFatG) },
            targets = targets,
            emptyText = emptyText(summary?.loggedDays ?: 0),
            subtitle = completeDaysLine(summary?.completeDays ?: 0),
            subtitleTestTag = "diary_history_complete_count",
            chart = {
                val last = month.lastDay.dayOfMonth
                KcalBars(
                    title = stringResource(Res.string.px_body_month_chart_title),
                    values = content.monthKcal,
                    labels = content.monthKcal.indices.map { block ->
                        val from = block * BLOCK_DAYS + 1
                        "$from-${minOf(from + BLOCK_DAYS - 1, last)}"
                    },
                    target = targets?.kcal,
                )
            },
        )
    }
}

/** "Sin días completos" when days are logged but none is complete, "Sin registros" when nothing is logged. */
@Composable
private fun emptyText(loggedDays: Int): String = stringResource(
    if (loggedDays > 0) Res.string.diary_history_no_complete_days else Res.string.diary_history_empty,
)

/** "Media de N días completos" under the card title; null when no day is complete. */
@Composable
private fun completeDaysLine(completeDays: Int): String? = if (completeDays >
    0
) {
    pluralStringResource(Res.plurals.diary_history_complete_days, completeDays, completeDays)
} else {
    null
}

/** Calories per day (or block) against the target line; every bar has one colour, [unlitIndices] are dimmed. */
@Composable
private fun KcalBars(
    title: String,
    values: List<Float>,
    labels: List<String>,
    target: Int?,
    unlitIndices: Set<Int> = emptySet(),
) {
    if (values.none { it > 0f }) return
    val description = target?.let { stringResource(Res.string.px_body_chart_cd, AppNumberFormat.integer(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = title.uppercase(),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
        )
        MiniBarChart(
            values = values,
            modifier = Modifier.fillMaxWidth().testTag("diary_history_chart"),
            labels = labels,
            color = AppTheme.colors.neonLime,
            highlightIndex = if (unlitIndices.isEmpty()) values.lastIndex else null,
            target = target?.toFloat(),
            unlitIndices = unlitIndices,
            description = description,
        )
    }
}

private const val WEEK_DAYS = 7
private const val BLOCK_DAYS = 7
