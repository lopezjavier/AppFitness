@file:Suppress("TooManyFunctions") // One card: its week selector, detail and rows are private pieces of it.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.ic_steps_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_timer
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_headline
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_cardio_planned
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_cardio_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_end
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_session_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_start_action
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_starts_chip
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_status_ended
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item_current_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item_done_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_item_planned_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_week_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_weeks_error
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_fact_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_current
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_deload
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_diet_break
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_chip_paused
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_planned
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_done
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_missed
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_pending
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_session_planned
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_title
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_week_title_of
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppColors
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CircleIconButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.InlineNoteTone
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.NonBreaking
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgressDefaults
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgressPicker
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatRing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.model.nextPendingSession
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100.0
private const val STACK_FONT_SCALE = 1.2f
private const val CARDIO_STAGGER = 3
private const val STEPS_STAGGER = 4

/** What the "Tu bloque" card of the root needs: the retry event of its weeks note (the card opens nothing). */
internal class BlockCardActions(val onEvent: (TrainerEvent) -> Unit)

/**
 * "Tu bloque" card on the Trainer root (C-124, R-103, R-105, R-106). Title row: "Tu bloque", the block's state chip
 * ("Semana 2 de 4",
 * "Bloque terminado") and the ⋮ [menu]. Body: the block chips ("Empieza el…", "Semana en pausa"), the week tabs ("S1"…,
 * done filled with a check, current outlined, the chosen one underlined) and,
 * under them, the chosen week as the "Semanas del bloque" card showed it: its chips, dates, "3 de 4 entrenos" with the
 * percent
 * chip and one continuous 6 dp bar, one status card per session, and the Cardio and Pasos al día bars. The current week
 * is chosen by default (the last one once the block ended, the first before it starts); the choice is local and
 * survives a recreation. [weeks] is empty when their read failed: the card then shows a retry note. [initialWeek]
 * preselects a week (the gallery). [today] places the cardio pace tick of the current week (05 §6.0f).
 */
@Composable
@Suppress("LongParameterList") // The block, its weeks and pause, the diet break, the menu and the gallery hook.
internal fun BlockSummaryBox(
    status: BlockStatus,
    weeks: List<PlanWeek>,
    actions: BlockCardActions,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    pause: BlockPause? = null,
    dietBreakWeek: Int? = null,
    menu: (@Composable () -> Unit)? = null,
    initialWeek: Int? = null,
    online: Boolean = true,
    starting: Boolean = false,
) {
    if (status !is BlockStatus.Saved) return
    val start = SessionStart(online, starting) { actions.onEvent(TrainerEvent.StartRoutine(it)) }
    SummaryCard(
        title = stringResource(Res.string.trainer_block_title),
        lines = emptyList(),
        onClick = null,
        testTag = "trainer_box_block",
        modifier = modifier,
        glow = AppTheme.colors.neonLime,
        status = if (weeks.isEmpty() || !status.reviewDue) null else ({ MainChip(status) }),
        titleAction = menu,
        content = { BlockBody(status, weeks, today, pause, dietBreakWeek, initialWeek, start) },
        footer = if (weeks.isEmpty()) ({ BlockWeeksError { actions.onEvent(TrainerEvent.Refresh) } }) else null,
    )
}

/**
 * The state chip: only "Bloque terminado" (Neutral). The week count ("Semana 2 de 4") is no longer a chip: the week
 * buttons show every week and outline the current one.
 */
@Composable
private fun MainChip(status: BlockStatus.Saved) {
    if (status.reviewDue) {
        StatusChip(
            stringResource(Res.string.trainer_block_status_ended),
            StatusTone.Neutral,
            Modifier.testTag("trainer_box_block_chip"),
        )
    }
}

/** The week shown by default: the current one, the last once every week is past, otherwise the first. */
private fun defaultWeek(weeks: List<PlanWeek>): Int? = weeks.firstOrNull { it.phase == WeekPhase.Current }?.week
    ?: weeks.takeIf { list -> list.all { it.phase == WeekPhase.Past } }?.maxOfOrNull { it.week }
    ?: weeks.minOfOrNull { it.week }

@Composable
@Suppress("LongParameterList") // The block, its weeks, the pause, the diet break and the gallery hook.
private fun BlockBody(
    status: BlockStatus.Saved,
    weeks: List<PlanWeek>,
    today: LocalDate?,
    pause: BlockPause?,
    dietBreakWeek: Int?,
    initialWeek: Int?,
    start: SessionStart,
) {
    // The weeks could not be read: the footer shows the retry note instead.
    if (weeks.isEmpty()) return
    val sorted = weeks.sortedBy { it.week }
    var picked by rememberSaveable { mutableStateOf(initialWeek) }
    // Once the user picks a week, the bars of the next weeks appear filled: the fill plays once per screen (R-97).
    var interacted by rememberSaveable { mutableStateOf(false) }
    val selected = sorted.firstOrNull { it.week == picked } ?: sorted.firstOrNull { it.week == defaultWeek(sorted) }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        BlockChips(status, sorted.firstOrNull())
        WeekPicker(status, sorted, selected?.week ?: 1) { week ->
            if (week != selected?.week) interacted = true
            picked = week
        }
        selected?.let { week ->
            val animate = LocalAnimationsEnabled.current && !interacted
            CompositionLocalProvider(LocalAnimationsEnabled provides animate) {
                WeekDetail(
                    week,
                    WeekDetailContext(
                        totalWeeks = sorted.size,
                        paused = status.paused && !status.reviewDue,
                        dietBreak = week.week == dietBreakWeek,
                        today = today,
                        pause = pause,
                        start = start,
                    ),
                )
            }
        }
    }
}

/** The block-level chips: the start date before the block starts and "Semana en pausa" while paused. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BlockChips(status: BlockStatus.Saved, first: PlanWeek?) {
    val starts = first?.takeIf { status.week == null && !status.reviewDue && it.phase == WeekPhase.Upcoming }
    val paused = status.paused && !status.reviewDue
    if (starts == null && !paused) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        starts?.let { week ->
            StatusChip(
                stringResource(
                    Res.string.trainer_block_starts_chip,
                    AppDateFormat.weekdayDayMonth(week.startDate.dayOfWeek, week.startDate.day, week.startDate.month),
                ),
                StatusTone.Neutral,
                Modifier.testTag("trainer_box_block_starts"),
            )
        }
        if (paused) {
            StatusChip(
                stringResource(Res.string.block_week_paused),
                StatusTone.Neutral,
                Modifier.testTag("trainer_box_block_paused"),
            )
        }
    }
}

/** "El bloque termina el …": only once a week was paused and the end moved (US-106). */
@Composable
private fun BlockEndLine(pause: BlockPause?) {
    val end = pause?.blockEnd ?: return
    if (pause.pausedWeeks <= 0) return
    Text(
        text = stringResource(Res.string.trainer_block_end, AppDateFormat.shortDate(end)),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.testTag("trainer_box_block_end"),
    )
}

/**
 * The block's weeks as squared tabs that are the progress line itself (R-106): "S1"… inside each (spoken "Semana 1,
 * hecha"), done weeks filled cyan with a check, the current one outlined, the chosen one underlined and bold; up to
 * six share the width, longer blocks scroll sideways.
 */
@Composable
private fun WeekPicker(status: BlockStatus.Saved, weeks: List<PlanWeek>, selected: Int, onSelect: (Int) -> Unit) {
    val current = weeks.firstOrNull { it.phase == WeekPhase.Current }?.week
    val itemCd = weeks.associate { week ->
        week.week to stringResource(
            when (week.phase) {
                WeekPhase.Past -> Res.string.trainer_block_week_item_done_cd
                WeekPhase.Current -> Res.string.trainer_block_week_item_current_cd
                WeekPhase.Upcoming -> Res.string.trainer_block_week_item_planned_cd
            },
            week.week,
        )
    }
    val labels = weeks.associate { it.week to stringResource(Res.string.trainer_block_week_item, it.week) }
    SegmentedProgressPicker(
        total = weeks.size,
        done = weeks.count { it.phase == WeekPhase.Past },
        selected = weeks.indexOfFirst { it.week == selected } + 1,
        onSelect = { step -> weeks.getOrNull(step - 1)?.let { onSelect(it.week) } },
        label = { step -> weeks.getOrNull(step - 1)?.let { labels[it.week] }.orEmpty() },
        description = { step -> weeks.getOrNull(step - 1)?.let { itemCd[it.week] }.orEmpty() },
        stateDescription = status.weekLine(),
        current = current?.let { week -> weeks.indexOfFirst { it.week == week } + 1 },
        testTag = "trainer_box_block_weeks",
    )
}

/** What the chosen week's detail needs besides the week: the block's size, pause state, diet break and today. */
private class WeekDetailContext(
    val totalWeeks: Int,
    val paused: Boolean,
    val dietBreak: Boolean,
    val today: LocalDate?,
    val pause: BlockPause?,
    val start: SessionStart,
)

/** What the play button of a session card needs: the connection, a start in flight and the event to send (T-300). */
private class SessionStart(val online: Boolean, val starting: Boolean, val onStart: (routineId: String) -> Unit)

/**
 * The chosen week, as the "Semanas del bloque" card showed it (US-73, US-75): "Semana 2" with its chips, the dates,
 * the moved end date under the dates, the sessions headline with the percent chip and its bar (a planned week reads "4
 * entrenos planificados"), one
 * status card per session and the Cardio and Pasos al día bars (a planned week shows only its cardio target).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod") // One week detail layout: chips, sessions and bars.
private fun WeekDetail(week: PlanWeek, context: WeekDetailContext) {
    val upcoming = week.phase == WeekPhase.Upcoming
    val today = context.today
    val title = if (context.totalWeeks > SegmentedProgressDefaults.MaxTabs) {
        stringResource(Res.string.trainer_week_title_of, week.week, context.totalWeeks)
    } else {
        stringResource(Res.string.trainer_week_title, week.week)
    }
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xs).testTag("trainer_box_block_week_${week.week}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.align(Alignment.CenterVertically).testTag("trainer_box_block_week_title"),
            )
            if (week.phase == WeekPhase.Current) {
                // A paused week is "En pausa", never "Actual": the week is not running (R-106).
                StatusChip(
                    stringResource(
                        if (context.paused) {
                            Res.string.trainer_week_chip_paused
                        } else {
                            Res.string.trainer_week_chip_current
                        },
                    ),
                    if (context.paused) StatusTone.Neutral else StatusTone.Info,
                    Modifier.testTag("trainer_box_block_week_current"),
                )
            }
            if (context.dietBreak) {
                StatusChip(
                    stringResource(Res.string.trainer_week_chip_diet_break),
                    StatusTone.Info,
                    Modifier.testTag("trainer_box_block_week_diet_break"),
                )
            }
            if (week.deload) {
                StatusChip(
                    stringResource(Res.string.trainer_week_chip_deload),
                    StatusTone.Info,
                    Modifier.testTag("trainer_box_block_week_deload"),
                )
            }
        }
        Text(
            text = AppDateFormat.dayRange(week.startDate, week.endDate),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("trainer_box_block_week_dates"),
        )
        BlockEndLine(context.pause)
        if (upcoming) {
            Text(
                text = pluralStringResource(Res.plurals.trainer_week_planned, week.planned, week.planned),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("trainer_box_block_planned"),
            )
        } else if (week.planned > 0) {
            SessionsHeadline(week)
        }
        if (week.sessions.isNotEmpty()) SessionCards(week, context.start)
        ActivityBars(week, today)
    }
}

/**
 * "3 de 4" (`headlineMetric`) with "entrenos" and the percent chip at the end: Info while not all are done, Good (with
 * its check glyph) once every planned session is done; sessions never read "Superado" (07 §3 (c)).
 */
@Composable
private fun SessionsHeadline(week: PlanWeek) {
    val percent = (week.completed * PERCENT / week.planned).roundToInt()
    val done = week.completed >= week.planned
    val progressCd = stringResource(Res.string.trainer_week_progress_cd, week.completed, week.planned)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        StatRing(
            fraction = week.completed.toFloat() / week.planned,
            color = if (done) AppTheme.colors.electricGreen else AppTheme.colors.athleticCyan,
            modifier = Modifier
                .semantics { contentDescription = progressCd }
                .testTag("trainer_box_block_sessions_progress"),
        ) {
            if (done) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = AppTheme.colors.electricGreen,
                    modifier = Modifier.size(Spacing.xl),
                )
            } else {
                Text(
                    text = NonBreaking.join(percent.toString(), "%"),
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.textPrimary,
                )
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(Res.string.report_sessions_headline, week.completed, week.planned),
                style = AppTheme.typography.headlineMetric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("trainer_box_block_headline"),
            )
            Text(
                text = stringResource(Res.string.trainer_block_week_unit),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        StatusChip(
            text = NonBreaking.join(percent.toString(), "%"),
            tone = if (week.completed >= week.planned) StatusTone.Good else StatusTone.Info,
            modifier = Modifier.testTag("trainer_box_block_percent"),
        )
    }
}

/**
 * The week's sessions as small cards in two columns (one column from font scale 1.2). The cards are read-only; only the
 * next pending session of the current week, when it has a routine, carries a play button (T-300).
 */
@Composable
private fun SessionCards(week: PlanWeek, start: SessionStart) {
    val columns = if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) 1 else 2
    val startable = week.nextPendingSession()?.takeIf { it.routineId != null }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        week.sessions.chunked(columns).forEach { rowSessions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowSessions.forEach { session ->
                    val play = if (session == startable) start else null
                    SessionCard(week, session, play, Modifier.weight(1f))
                }
                repeat(columns - rowSessions.size) { Row(Modifier.weight(1f)) {} }
            }
        }
        if (startable != null && !start.online) {
            OfflineHint(stringResource(Res.string.trainer_block_start_action))
        }
    }
}

/** The lime-tinted surface of a done session, the plain tile of any other. */
private fun Modifier.sessionSurface(done: Boolean, colors: AppColors): Modifier = this
    .clip(AppShapes.md)
    .background(if (done) AppPaint.tintSurface(colors.electricGreen) else AppPaint.tileSurface(colors))
    .border(
        AppBorder.thin,
        if (done) AppPaint.accentBorder(colors.electricGreen) else AppPaint.tileBorder(),
        AppShapes.md,
    )

/**
 * One session: its name and its state in words, "Hecho" with a lime check, "Pendiente" (this week), "No hecho" (a
 * past week; neutral, never an error) or "Planificado" (an upcoming week, `textTertiary`).
 */
@Composable
private fun SessionCard(week: PlanWeek, session: PlanWeekSession, start: SessionStart?, modifier: Modifier = Modifier) {
    val state = stringResource(
        when {
            session.done -> Res.string.trainer_week_session_done
            week.phase == WeekPhase.Past -> Res.string.trainer_week_session_missed
            week.phase == WeekPhase.Current -> Res.string.trainer_week_session_pending
            else -> Res.string.trainer_week_session_planned
        },
    )
    val tag = "trainer_box_block_session_${week.week}_${session.sessionIndex}"
    val routineId = session.routineId
    // The play button is a sibling of the merged text group: a merged parent would hide it from its own tag.
    Row(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .sessionSurface(session.done, AppTheme.colors)
            .padding(
                start = Spacing.md,
                end = if (start != null) Spacing.xs else Spacing.md,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionCardText(week, session, state, Modifier.weight(1f).testTag(tag))
        if (start != null && routineId != null) {
            CircleIconButton(
                contentDescription = stringResource(Res.string.routines_start_cd, session.name),
                icon = Res.drawable.ic_play_arrow,
                testTag = "${tag}_start",
                onClick = { start.onStart(routineId) },
                enabled = start.online && !start.starting,
                accent = true,
            )
        }
    }
}

/** The name on top and the state below, spoken as one node ("Pierna B: Pendiente"). */
@Composable
private fun SessionCardText(week: PlanWeek, session: PlanWeekSession, state: String, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.trainer_block_session_cd, session.name, state)
    // Name on top, state below: "Planificado" next to a name would squeeze it at 360 dp.
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = session.name,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (session.done) {
                IconBadge(
                    Res.drawable.ic_check,
                    AppTheme.colors.electricGreen,
                    size = Spacing.lg + Spacing.xs,
                    iconSize = Spacing.md,
                    shape = AppShapes.full,
                )
            }
            Text(
                text = state,
                style = AppTheme.typography.bodySmall,
                color = when {
                    session.done -> AppTheme.colors.electricGreen
                    week.phase == WeekPhase.Upcoming -> AppTheme.colors.textTertiary
                    else -> AppTheme.colors.textSecondary
                },
            )
        }
    }
}

/**
 * "Cardio" and "Pasos al día" as [GoalBarRow]s (05 §6.0g): label, "81 de 150 min" and an 8 dp bar, always
 * `athleticCyan`. Cardio keeps the "due by now" tick while the current week is open. A planned week shows only its
 * cardio target ("Objetivo: 150 min", no bar) and no steps. A row without a target is omitted; steps without data read
 * muted with no bar.
 */
@Composable
private fun ActivityBars(week: PlanWeek, today: LocalDate?) {
    val cardioTarget = week.cardioMinTarget?.takeIf { it > 0 }
    val stepsTarget = week.stepsTarget?.takeIf { it > 0 }?.takeIf { week.phase != WeekPhase.Upcoming }
    if (cardioTarget == null && stepsTarget == null) return
    // More air above and between the bars than in the sessions grid: the two goals read as their own group.
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        cardioTarget?.let { target ->
            val planned = week.phase == WeekPhase.Upcoming
            GoalBarRow(
                label = stringResource(Res.string.trainer_plan_fact_cardio),
                value = if (planned) {
                    stringResource(Res.string.trainer_block_cardio_planned, AppNumberFormat.integer(target))
                } else {
                    stringResource(
                        Res.string.trainer_block_cardio_value,
                        AppNumberFormat.integer(week.cardioMinDone),
                        AppNumberFormat.integer(target),
                    )
                },
                ratio = if (planned) null else week.cardioMinDone.toFloat() / target,
                pace = dueShare(elapsedShare(week, today), week.planned),
                stagger = CARDIO_STAGGER,
                tag = "trainer_box_block_cardio",
                icon = Res.drawable.ic_timer,
            )
        }
        stepsTarget?.let { target ->
            val avg = week.stepsAvg
            GoalBarRow(
                label = stringResource(Res.string.trainer_block_steps_label),
                value = if (avg == null) {
                    stringResource(Res.string.trainer_block_steps_none)
                } else {
                    stringResource(
                        Res.string.trainer_block_steps_value,
                        AppNumberFormat.integer(avg),
                        AppNumberFormat.integer(target),
                    )
                },
                ratio = avg?.let { it.toFloat() / target },
                // An average has no pace to keep: no marker while open.
                pace = null,
                stagger = STEPS_STAGGER,
                tag = "trainer_box_block_steps",
                icon = Res.drawable.ic_steps_outlined,
            )
        }
    }
}

/**
 * What the "Tu bloque" ⋮ opens and the view model keeps open: the last-day picker of the current week (while
 * [TrainerUiState.Content.extendWeekPickerOpen]) and the "Eliminar bloque" confirmation (R-08).
 */
@Composable
internal fun BlockMenuTargets(state: TrainerUiState.Content, online: Boolean, onEvent: (TrainerEvent) -> Unit) {
    val extension = state.weekExtension
    if (state.extendWeekPickerOpen && extension != null) {
        ExtendWeekPicker(
            extension,
            ExtendWeekUi(
                extension = extension,
                pickerOpen = true,
                busy = state.extendingWeek,
                online = online,
                onDismiss = { onEvent(TrainerEvent.DismissExtendWeekPicker) },
                onConfirm = { onEvent(TrainerEvent.ExtendWeek(it)) },
            ),
        )
    }
    if (state.removeBlockConfirm) RemoveBlockDialog(onEvent)
}

/** The "weeks could not be read" note of the card, tapping it retries (C-138). */
@Composable
private fun BlockWeeksError(onRetry: () -> Unit) {
    InlineNote(
        text = stringResource(Res.string.trainer_block_weeks_error),
        testTag = "trainer_box_block_weeks_error",
        onClick = onRetry,
        onClickLabel = stringResource(Res.string.action_retry),
        tone = InlineNoteTone.Error,
    )
}
