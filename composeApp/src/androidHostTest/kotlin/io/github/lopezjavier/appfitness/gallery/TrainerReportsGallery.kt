package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.feature.trainer.BlockReviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.PastReportScreen
import io.github.lopezjavier.appfitness.feature.trainer.PastReportUiState
import io.github.lopezjavier.appfitness.feature.trainer.ReportsScreen
import io.github.lopezjavier.appfitness.feature.trainer.ReportsUiState
import io.github.lopezjavier.appfitness.feature.trainer.WeekSummaryUiState
import kotlinx.coroutines.flow.flowOf
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
    fun list(state: String, ui: ReportsUiState, online: Boolean = true, wide: Boolean = false) = GalleryEntry(
        TRAINER_AREA,
        REPORTS_SCREEN,
        state,
        covers = setOf("ReportsRoute"),
        wide = wide,
    ) {
        ReportsScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }

    val items = ReportsUiState.Content(
        listOf(
            TrainerFixtures.reportItem(id = "report-2", kind = ReportKind.WeekSummary, week = 2),
            TrainerFixtures.reportItem(id = "report-1", kind = ReportKind.BlockReview, week = null),
        ),
    )
    return listOf(
        list("loading", ReportsUiState.Loading),
        list("empty", ReportsUiState.Empty),
        list("weekly and block reports", items),
        list("failed", ReportsUiState.Error),
        list("offline", ReportsUiState.Offline, online = false),
        list("tablet width", items, wide = true),
    )
}

/** A stored report opened from the list (PastReportRoute): weekly, block and the failures. */
private fun pastReport(): List<GalleryEntry> {
    fun past(state: String, ui: PastReportUiState, online: Boolean = true, wide: Boolean = false) = GalleryEntry(
        TRAINER_AREA,
        PAST_REPORT_SCREEN,
        state,
        covers = setOf("PastReportRoute"),
        wide = wide,
    ) {
        PastReportScreen(state = ui, isOnline = flowOf(online), onEvent = {}, onBack = {})
    }

    val week = PastReportUiState.Week(
        item = TrainerFixtures.reportItem(id = "report-2", kind = ReportKind.WeekSummary, week = 2),
        content = WeekSummaryUiState.Content(
            summary = TrainerFixtures.weekSummary(seen = true),
            exerciseNames = TrainerFixtures.exerciseNames,
        ),
    )
    val block = PastReportUiState.Block(
        item = TrainerFixtures.reportItem(id = "report-1", kind = ReportKind.BlockReview, week = null),
        content = BlockReviewUiState.Content(
            review = TrainerFixtures.blockReview(decision = PhaseDecision.Accepted, seen = true),
            exerciseNames = TrainerFixtures.exerciseNames,
        ),
    )
    return listOf(
        past("loading", PastReportUiState.Loading),
        past("not found", PastReportUiState.NotFound),
        past("failed", PastReportUiState.Error),
        past("offline", PastReportUiState.Offline, online = false),
        past("weekly summary", week),
        past("block review", block),
        past("tablet width, weekly summary", week, wide = true),
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
