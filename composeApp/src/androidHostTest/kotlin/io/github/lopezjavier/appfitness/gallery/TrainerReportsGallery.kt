package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReportGroup
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.ReportTeaser
import io.github.lopezjavier.appfitness.feature.trainer.BlockReportUi
import io.github.lopezjavier.appfitness.feature.trainer.PastReportScreen
import io.github.lopezjavier.appfitness.feature.trainer.PastReportUiState
import io.github.lopezjavier.appfitness.feature.trainer.ReportsScreen
import io.github.lopezjavier.appfitness.feature.trainer.ReportsUiState
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val REPORTS_SCREEN = "Informes"
private const val PAST_REPORT_SCREEN = "Informe pasado"

/** The stored reports list (ReportsRoute): loading, empty, the list of weekly and block reports and the failures. */
private fun reports(): List<GalleryEntry> {
    fun list(state: String, ui: ReportsUiState, online: Boolean = true, wide: Boolean = false, tall: Boolean = false) =
        GalleryEntry(
            TRAINER_AREA,
            REPORTS_SCREEN,
            state,
            covers = setOf("ReportsRoute"),
            wide = wide,
            tall = tall,
        ) {
            ReportsScreen(
                state = ui,
                isOnline = flowOf(online),
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    fun item(id: String, plan: String, kind: ReportKind, week: Int?, start: LocalDate, days: Int = 6): ReportItem =
        TrainerFixtures.reportItem(id = id, kind = kind, week = week).copy(
            planId = plan,
            periodStart = start,
            periodEnd = start.plus(days, DateTimeUnit.DAY),
        )

    fun week(id: String, plan: String, week: Int, start: LocalDate, teaser: ReportTeaser?) =
        item(id, plan, ReportKind.WeekSummary, week, start).copy(teaser = teaser)

    // Block 2 (open): two weeks, no review yet. Block 1: review first, then its weeks, one paused, one v1.
    val open = ReportGroup(
        "plan-2",
        2,
        listOf(
            week("w-2-3", "plan-2", 3, LocalDate(2026, 11, 17), ReportTeaser(3, null, -0.3)),
            week("w-2-2", "plan-2", 2, LocalDate(2026, 11, 10), ReportTeaser(0, null, 0.0)),
        ),
    )
    val closed = ReportGroup(
        "plan-1",
        1,
        listOf(
            item("b-1", "plan-1", ReportKind.BlockReview, null, LocalDate(2026, 10, 13), days = 27)
                .copy(teaser = ReportTeaser(12, 15, -1.8)),
            week("w-1-4", "plan-1", 4, LocalDate(2026, 11, 3), ReportTeaser(1, null, null)),
            item("w-1-3", "plan-1", ReportKind.WeekSummary, null, LocalDate(2026, 10, 27)).copy(paused = true),
            week("w-1-2", "plan-1", 2, LocalDate(2026, 10, 20), null),
        ),
    )
    val mixed = ReportsUiState.Content(listOf(open, closed))
    val onlyWeeks = ReportsUiState.Content(listOf(open))
    return listOf(
        list("loading", ReportsUiState.Loading),
        list("empty", ReportsUiState.Empty),
        list("blocks and weeks, grouped", mixed, tall = true),
        list("only weeks", onlyWeeks),
        list("failed", ReportsUiState.Error),
        list("offline", ReportsUiState.Offline, online = false),
        list("tablet width", mixed, wide = true),
    )
}

/** A stored report from the list (PastReportRoute): block and failures; weekly ones: TrainerWeekReportGallery. */
private fun pastReport(): List<GalleryEntry> {
    fun past(
        state: String,
        ui: PastReportUiState,
        online: Boolean = true,
        wide: Boolean = false,
        tall: Boolean = false,
    ) = GalleryEntry(
        TRAINER_AREA,
        PAST_REPORT_SCREEN,
        state,
        covers = setOf("PastReportRoute"),
        wide = wide,
        tall = tall,
    ) {
        PastReportScreen(state = ui, isOnline = flowOf(online), onEvent = {}, onBack = {})
    }

    fun block(report: BlockReportUi?, decision: PhaseDecision? = PhaseDecision.Accepted) = PastReportUiState.Block(
        item = TrainerFixtures.reportItem(id = "report-1", kind = ReportKind.BlockReview, week = null)
            .copy(periodStart = LocalDate(2026, 10, 13), periodEnd = LocalDate(2026, 11, 9)),
        content = BlockReportFixtures.content(report, decision = decision, seen = true),
    )
    return listOf(
        past("loading", PastReportUiState.Loading),
        past("not found", PastReportUiState.NotFound),
        past("failed", PastReportUiState.Error),
        past("offline", PastReportUiState.Offline, online = false),
        past("block review, decision as text", block(BlockReportFixtures.stored), tall = true),
        past("block review, phase kept", block(BlockReportFixtures.storedKept, PhaseDecision.Kept), tall = true),
        past("block review, v1 without progress", block(BlockReportFixtures.v1), tall = true),
        past("block review, tablet width", block(BlockReportFixtures.stored), wide = true),
    )
}

internal fun trainerReportEntries(): List<GalleryEntry> = reports() + pastReport()

/** Renders the Trainer reports entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class TrainerReportsGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTrainerReports() = rule.renderGallery(trainerReportEntries().filter { !it.wide && !it.tall })
}
