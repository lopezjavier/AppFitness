package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.ReportTeaser
import io.github.lopezjavier.appfitness.domain.model.groupReportsByBlock
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NBSP = " "
private const val MINUS = "−"

/**
 * US-129 and US-144: "Informes" lists the stored reports grouped by block, with a teaser per row, and opens one on
 * tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class ReportsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ReportsEvent>()
    private val opened = mutableListOf<String>()

    private fun show(state: ReportsUiState, fontScale: Float? = null) {
        rule.setContent {
            val density = LocalDensity.current
            val provided = if (fontScale == null) density else Density(density.density, fontScale)
            CompositionLocalProvider(LocalDensity provides provided) {
                AppTheme { ReportsScreen(state, flowOf(true), { events += it }, { opened += it }, {}) }
            }
        }
        rule.waitForIdle()
    }

    private fun content(vararg items: ReportItem) = ReportsUiState.Content(groupReportsByBlock(items.toList()))

    /** A chip with exactly [text] inside the teaser of report [id]. */
    private fun chip(id: String, text: String) {
        rule.onNode(
            hasText(text) and hasAnyAncestor(hasTestTag("reports_teaser_$id")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    private fun reveal(tag: String) {
        rule.onNodeWithTag("reports_list").performScrollToNode(hasTestTag(tag))
    }

    private val week = reportItem("week-3", week = 3, teaser = ReportTeaser(3, null, -0.3))
    private val block = reportItem(
        "block-1",
        ReportKind.BlockReview,
        week = null,
        teaser = ReportTeaser(12, 15, -1.8),
    )
    private val paused = reportItem("paused-1", paused = true)

    @Test
    fun teaserPerRow_weekShowsWeekNumberAndItsFigures() {
        show(content(week))

        rule.onNodeWithTag("reports_row_week-3").assertTextContains("Semana 3", substring = true)
        chip("week-3", "3${NBSP}suben")
        chip("week-3", "${MINUS}0,3${NBSP}kg")
        rule.onNodeWithTag("reports_row_week-3").assertTextContains("3 al 9 nov", substring = true)
    }

    @Test
    fun teaserPerRow_blockShowsReviewWithUpsOfTotal() {
        show(content(block))

        rule.onNodeWithTag("reports_row_block-1").assertTextContains("Revisión del bloque", substring = true)
        chip("block-1", "12${NBSP}de${NBSP}15 suben")
        chip("block-1", "${MINUS}1,8${NBSP}kg")
    }

    @Test
    fun teaserPerRow_pausedWeekReadsPausedAndHasNoTeaser() {
        show(content(paused))

        rule.onNodeWithTag("reports_row_paused-1").assertTextContains("Semana en pausa", substring = true)
        rule.onNodeWithTag("reports_row_paused-1").assertTextContains("3 al 9 nov", substring = true)
        rule.onAllNodesWithTag("reports_teaser_paused-1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun teaserPerRow_pausedWeekIgnoresATeaserIfOneArrives() {
        show(content(paused.copy(teaser = ReportTeaser(2, null, null))))

        rule.onAllNodesWithTag("reports_teaser_paused-1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun teaserPerRow_weekWithoutNumberFallsBackToWeeklySummary() {
        show(content(reportItem("old-1", week = null)))

        rule.onNodeWithTag("reports_row_old-1").assertTextContains("Resumen semanal", substring = true)
    }

    @Test
    fun onlyGatedFigures_missingWeightLeavesOnlyTheStrengthChip() {
        show(content(reportItem("w-1", week = 4, teaser = ReportTeaser(1, null, null))))

        chip("w-1", "1${NBSP}sube")
        rule.onNodeWithTag("reports_teaser_w-1", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag("reports_row_w-1").assertCountEquals(1)
        assertEquals(
            0,
            rule.onAllNodesWithText("kg", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun onlyGatedFigures_weekOneShowsWeightOnly() {
        show(content(reportItem("w-1", week = 1, teaser = ReportTeaser(null, null, -0.3))))

        chip("w-1", "${MINUS}0,3${NBSP}kg")
        rule.onAllNodesWithTag("reports_row_w-1").assertCountEquals(1)
    }

    @Test
    fun onlyGatedFigures_itemWithoutTeaserHasNoChipsButKeepsItsDates() {
        show(content(reportItem("w-9", week = 2, teaser = null)))

        rule.onAllNodesWithTag("reports_teaser_w-9", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("reports_row_w-9").assertTextContains("3 al 9 nov", substring = true)
    }

    @Test
    fun teaser_zeroUpsReadsNoneOrZeroOfTotal() {
        show(
            content(
                reportItem("w-0", week = 2, teaser = ReportTeaser(0, null, 0.0)),
                reportItem("b-0", ReportKind.BlockReview, week = null, teaser = ReportTeaser(0, 15, null)),
            ),
        )

        chip("w-0", "Ninguno sube")
        chip("w-0", "Peso igual")
        chip("b-0", "0${NBSP}de${NBSP}15 suben")
    }

    @Test
    fun groupedByBlock_headingsAndReviewFirstNewestBlockFirst() {
        show(
            content(
                reportItem("w-2-1", week = 1, planId = "plan-2"),
                reportItem("w-1-4", week = 4, planId = "plan-1"),
                reportItem("b-1", ReportKind.BlockReview, week = null, planId = "plan-1"),
            ),
        )

        rule.onNodeWithTag("reports_group_plan-2").assertTextContains("Bloque 2", substring = true)
        rule.onNodeWithTag("reports_group_plan-1").assertTextContains("Bloque 1", substring = true)
        fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        assertTrue(top("reports_group_plan-2") < top("reports_group_plan-1"))
        assertTrue(top("reports_group_plan-1") < top("reports_row_b-1"))
        assertTrue(top("reports_row_b-1") < top("reports_row_w-1-4"))
    }

    @Test
    fun tappingAWeekRow_opensThatReport() {
        show(content(week, block))

        rule.onNodeWithTag("reports_row_week-3").performClick()

        assertEquals(listOf("week-3"), opened)
    }

    @Test
    fun tappingABlockRow_opensThatReport() {
        show(content(week, block))

        rule.onNodeWithTag("reports_row_block-1").performClick()

        assertEquals(listOf("block-1"), opened)
    }

    @Test
    fun title_isInformes() {
        show(content(week))

        rule.onNodeWithTag("reports_title").assertTextEquals("Informes")
    }

    @Test
    fun empty_showsTheHintWithoutPromisingHistory() {
        show(ReportsUiState.Empty)

        rule.onNodeWithTag("reports_empty").assertIsDisplayed()
        rule.onNodeWithTag("reports_empty_message")
            .assertTextEquals("Tus resúmenes semanales y revisiones de bloque aparecerán aquí.")
    }

    @Test
    fun error_offersRetry() {
        show(ReportsUiState.Error)

        rule.onNodeWithTag("reports_retry").performClick()

        assertEquals(listOf<ReportsEvent>(ReportsEvent.Retry), events.toList())
    }

    @Test
    fun offline_showsTheOfflineState() {
        show(ReportsUiState.Offline)

        rule.onNodeWithTag("reports_offline").assertIsDisplayed()
    }

    @Test
    fun loading_showsRowSkeletons() {
        show(ReportsUiState.Loading)

        rule.onNodeWithTag("reports_loading").assertIsDisplayed()
    }

    @Test
    fun fontScale130_teaserChipsStayWholeAndRowsClickable() {
        assertLargeFont(1.3f)
    }

    @Test
    fun fontScale200_teaserChipsWrapAndRowsStayClickable() {
        assertLargeFont(2.0f)
    }

    private fun assertLargeFont(scale: Float) {
        show(content(week, block, paused), fontScale = scale)

        listOf("week-3", "block-1").forEach { id ->
            reveal("reports_row_$id")
            rule.onNodeWithTag("reports_teaser_$id", useUnmergedTree = true).assertIsDisplayed()
        }
        chip("week-3", "3${NBSP}suben")
        chip("block-1", "12${NBSP}de${NBSP}15 suben")
        chip("block-1", "${MINUS}1,8${NBSP}kg")
        rule.onNodeWithTag("reports_row_block-1").performClick()
        assertEquals(listOf("block-1"), opened)
    }
}
