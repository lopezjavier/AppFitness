package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-129: "Informes" lists the stored reports as served and opens one on tap. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ReportsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ReportsEvent>()
    private val opened = mutableListOf<String>()

    private fun show(state: ReportsUiState) {
        rule.setContent {
            AppTheme { ReportsScreen(state, flowOf(true), { events += it }, { opened += it }, {}) }
        }
        rule.waitForIdle()
    }

    private val week = reportItem("week-1")
    private val block = reportItem("block-1", ReportKind.BlockReview, week = null)
    private val paused = reportItem("paused-1", paused = true)

    @Test
    fun rows_showKindAndPeriodInServedOrder_withoutAnyReminderRow() {
        show(ReportsUiState.Content(listOf(block, week, paused)))

        rule.onNodeWithTag("reports_title").assertTextEquals("Informes")
        rule.onNodeWithTag("reports_row_block-1").assertTextContains("Revisión del bloque", substring = true)
        rule.onNodeWithTag("reports_row_block-1").assertTextContains("3 al 9 nov", substring = true)
        rule.onNodeWithTag("reports_row_week-1").assertTextContains("Resumen semanal", substring = true)
        rule.onNodeWithTag("reports_row_paused-1").assertTextContains("3 al 9 nov · Semana en pausa", substring = true)
        rule.onAllNodesWithTag("notifications_reminder_weight").assertCountEquals(0)
    }

    @Test
    fun tappingARow_opensThatReport() {
        show(ReportsUiState.Content(listOf(week, block)))

        rule.onNodeWithTag("reports_row_block-1").performClick()

        assertEquals(listOf("block-1"), opened)
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
    fun recreation_keepsTheListAndItsScrollPosition() {
        val items: List<ReportItem> = List(30) { reportItem("r-$it") }
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { ReportsScreen(ReportsUiState.Content(items), flowOf(true), {}, {}, {}) }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("reports_row_r-25"))
        rule.onNodeWithTag("reports_row_r-25").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("reports_row_r-25").assertIsDisplayed()
        rule.onAllNodesWithTag("reports_row_r-0").assertCountEquals(0)
    }
}
