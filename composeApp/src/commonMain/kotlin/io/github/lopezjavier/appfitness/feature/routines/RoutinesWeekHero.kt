package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_stat_days
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_stat_streak
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_stat_streak_unit
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_stat_workouts
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_week_cd
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_week_empty
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_week_title
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_1
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_2
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_3
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_4
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_5
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_6
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_7
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HeroStat
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WeekStrip
import io.github.lopezjavier.appfitness.designsystem.WeekStripDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val WeekdayLabels: List<StringResource> = listOf(
    Res.string.weekday_short_1,
    Res.string.weekday_short_2,
    Res.string.weekday_short_3,
    Res.string.weekday_short_4,
    Res.string.weekday_short_5,
    Res.string.weekday_short_6,
    Res.string.weekday_short_7,
)

/**
 * The "Esta semana" hero of the Entreno tab: the seven days of the current week (trained ones lit, today outlined)
 * and three figures (workouts, trained days, weekly streak) read from the completed sessions. Hidden while
 * [RoutinesUiState.Content.today] is unknown.
 */
@Composable
internal fun RoutinesWeekHero(state: RoutinesUiState.Content, modifier: Modifier = Modifier) {
    val today = state.today ?: return
    val start = today.weekStart()
    val labels = WeekdayLabels.map { stringResource(it) }
    val days = labels.mapIndexed { index, label ->
        val date = start.plus(index, DateTimeUnit.DAY)
        WeekStripDay(label = label, done = date in state.trainedDays, today = date == today)
    }
    val trained = state.trainedDays.size
    AppCard(modifier.fillMaxWidth().testTag("routines_week_hero"), glow = AppTheme.colors.athleticCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(Res.string.px_routines_week_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            WeekStrip(days, description = stringResource(Res.string.px_routines_week_cd, trained))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HeroStat(
                    icon = Res.drawable.ic_fitness_center,
                    value = AppNumberFormat.integer(state.weekWorkouts),
                    label = stringResource(Res.string.px_routines_stat_workouts),
                    accent = AppTheme.colors.neonLime,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    icon = Res.drawable.ic_calendar,
                    value = AppNumberFormat.integer(trained),
                    label = stringResource(Res.string.px_routines_stat_days),
                    accent = AppTheme.colors.athleticCyan,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    icon = Res.drawable.ic_flame_outlined,
                    value = AppNumberFormat.integer(state.streakWeeks),
                    label = stringResource(Res.string.px_routines_stat_streak),
                    accent = AppTheme.colors.energyAmber,
                    modifier = Modifier.weight(1f),
                    unit = stringResource(Res.string.px_routines_stat_streak_unit),
                )
            }
            if (state.weekWorkouts == 0) {
                Text(
                    text = stringResource(Res.string.px_routines_week_empty),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}
