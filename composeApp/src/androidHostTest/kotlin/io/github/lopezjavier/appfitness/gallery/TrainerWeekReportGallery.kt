package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.feature.trainer.PastReportScreen
import io.github.lopezjavier.appfitness.feature.trainer.PastReportUiState
import io.github.lopezjavier.appfitness.feature.trainer.WeekReportUi
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryScreen
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryUiState
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

private const val SCREEN = "Resumen semanal, progreso"
private const val PAST_SCREEN = "Informe pasado, progreso"

/** The progress-first weekly report (T-315) on the live screen: every state of every card, each goal order. */
private fun live(
    state: String,
    content: WeekSummaryUiState.Content,
    wide: Boolean = false,
    fontScale: Float = 1f,
    covers: Set<String> = emptySet(),
    interact: (ComposeContentTestRule.() -> Unit)? = null,
) = GalleryEntry(
    TRAINER_AREA,
    SCREEN,
    state,
    covers = setOf("WeekSummaryRoute") + covers,
    wide = wide,
    fontScale = fontScale,
    interact = interact,
) {
    WeekSummaryScreen(state = content, isOnline = flowOf(true), onEvent = {}, onBack = {})
}

private fun live(state: String, report: WeekReportUi, wide: Boolean = false, fontScale: Float = 1f) =
    live(state, WeekReportFixtures.content(report), wide, fontScale)

private fun past(state: String, report: WeekReportUi, wide: Boolean = false) = GalleryEntry(
    TRAINER_AREA,
    PAST_SCREEN,
    state,
    covers = setOf("PastReportRoute"),
    wide = wide,
) {
    val content = WeekReportFixtures.content(report)
    PastReportScreen(
        state = PastReportUiState.Week(
            TrainerFixtures.reportItem(id = "report-3", kind = ReportKind.WeekSummary, week = 4),
            content,
            content.report,
        ),
        isOnline = flowOf(true),
        onEvent = {},
        onBack = {},
    )
}

private fun weekReports(): List<GalleryEntry> {
    val f = WeekReportFixtures
    val pausedWeek = WeekSummaryUiState.Content(
        WeekSummary.pausedWeek("plan-1", LocalDate(2026, 11, 3), LocalDate(2026, 11, 9)),
        f.names,
    )
    return listOf(
        live("all states, pending proposal (muscle order)", f.report()),
        live("week 1, base", f.week1),
        live("empty strength", f.emptyStrength),
        live("no records, on plan", f.noRecords),
        live("weight, under the gate", f.gate),
        live("weight, no weigh-ins", f.noWeighIns),
        live("weight, same as last week", f.sameAsLast),
        live("waist, inside the margin", f.waistInMargin),
        live("waist, beyond the margin", f.waistBeyond),
        live("order, fat loss (weight first, Mantienes)", f.cutHold),
        live("order, strength", f.report(TrainingIntention.Strength)),
        live("order, maintenance (strength, waist, weight)", f.withWaist(f.report(TrainingIntention.Maintenance))),
        live(
            "order, recomposition (strength, waist, weight)",
            f.withWaist(f.report(TrainingIntention.Recomposition)),
        ),
        live("kept cards: diagnosis, stall, deload", f.keptCards),
        live("paused week", pausedWeek),
        live(
            "group sheet open",
            f.content(f.report()),
            covers = setOf("GroupSheet"),
            interact = { onNodeWithTag("week_summary_strength_group_chest").performClick() },
        ),
        live("large font (1.3)", f.report(), fontScale = 1.3f),
        live("large font (2.0)", f.report(), fontScale = 2.0f),
        live("tablet width", f.report(), wide = true),
    )
}

private fun pastWeekReports(): List<GalleryEntry> {
    val f = WeekReportFixtures
    return listOf(
        past("v2, decided proposal as text, no health note", f.decidedPast),
        past("v1, no progress", f.v1Past),
        past("missing inputs say what is missing", f.missingDeleted),
        past("tablet width", f.decidedPast, wide = true),
    )
}

internal fun trainerWeekReportEntries(): List<GalleryEntry> = weekReports() + pastWeekReports()
