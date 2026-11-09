package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-129: a stored report opens read-only with the live content, its title and its period. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class PastReportScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PastReportEvent>()
    private var backs = 0

    private fun show(state: PastReportUiState) {
        rule.setContent {
            AppTheme { PastReportScreen(state, flowOf(true), { events += it }, { backs++ }) }
        }
        rule.waitForIdle()
    }

    private fun week(paused: Boolean = false): PastReportUiState.Week {
        val summary = weekSummary(seen = true).copy(paused = paused)
        return PastReportUiState.Week(
            reportItem("week-1", paused = paused),
            WeekSummaryUiState.Content(summary, mapOf("id-bench" to "Press banca")),
        )
    }

    private fun block(decision: PhaseDecision?): PastReportUiState.Block {
        val review = blockReview(seen = true, phaseDecision = decision)
        return PastReportUiState.Block(
            reportItem("block-1", ReportKind.BlockReview, week = null, phaseDecision = decision),
            BlockReviewUiState.Content(review, mapOf("id-bench" to "Press banca")),
        )
    }

    @Test
    fun weekReport_showsTheStoredFiguresWithTheLiveTitleAndThePeriod() {
        show(week())

        rule.onNodeWithTag("past_report_title").assertTextEquals("Resumen semanal")
        rule.onNodeWithTag("past_report_subtitle").assertTextContains("3 al 9 nov", substring = true)
        rule.onNodeWithText("Esta semana: 3 de 4 entrenos.").assertIsDisplayed()
    }

    @Test
    fun pausedWeek_showsSemanaEnPausa() {
        show(week(paused = true))

        rule.onNodeWithTag("week_summary_paused").assertTextEquals("Semana en pausa")
    }

    @Test
    fun acceptedBlock_showsTheDecisionAsTextAndNoActions() {
        show(block(PhaseDecision.Accepted))

        rule.onNodeWithTag("past_report_title").assertTextEquals("Revisión del bloque")
        rule.onNodeWithTag("past_report_subtitle").assertTextContains("3 al 9 nov", substring = true)
        rule.onNodeWithTag("past_report_decision").assertTextContains("Fase aceptada", substring = true)
        assertNoActions()
    }

    @Test
    fun keptBlock_showsSeguisteComoEstabasWithoutAPromise() {
        show(block(PhaseDecision.Kept))

        rule.onNodeWithTag("past_report_decision").assertTextEquals("Seguiste como estabas.")
        rule.onAllNodesWithText("volveremos a proponer", substring = true).assertCountEquals(0)
        assertNoActions()
    }

    @Test
    fun undecidedBlock_showsNoDecisionTextAndNoActions() {
        show(block(null))

        rule.onAllNodesWithTag("past_report_decision").assertCountEquals(0)
        rule.onAllNodesWithText("Sin decidir").assertCountEquals(0)
        assertNoActions()
    }

    private fun assertNoActions() {
        listOf("Aceptar", "Seguir como estoy", "Aplicar", "Ahora no", "Crear el siguiente bloque").forEach {
            rule.onAllNodesWithText(it).assertCountEquals(0)
        }
        listOf("block_review_phase_accept", "block_review_calorie_apply", "block_review_next_block").forEach {
            rule.onAllNodesWithTag(it).assertCountEquals(0)
        }
    }

    @Test
    fun back_callsTheBackCallback() {
        show(week())

        rule.onNodeWithTag("past_report_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun notFound_saysItIsNoLongerAvailable() {
        show(PastReportUiState.NotFound)

        rule.onNodeWithTag("past_report_empty_message").assertTextEquals("Ya no está disponible.")
        rule.onNodeWithTag("past_report_title").assertTextEquals("Informes")
    }

    @Test
    fun error_offersRetry() {
        show(PastReportUiState.Error)

        rule.onNodeWithTag("past_report_retry").performClick()

        assertEquals(listOf<PastReportEvent>(PastReportEvent.Retry), events.toList())
    }

    @Test
    fun offline_andLoading_showTheirStates() {
        show(PastReportUiState.Offline)
        rule.onNodeWithTag("past_report_offline").assertIsDisplayed()
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(PastReportUiState.Loading)

        rule.onNodeWithTag("past_report_loading").assertIsDisplayed()
    }

    @Test
    fun recreation_keepsTheOpenReport() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { PastReportScreen(block(PhaseDecision.Accepted), flowOf(true), {}, {}) } }

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("past_report_screen").assertIsDisplayed()
        rule.onNodeWithTag("past_report_decision").assertIsDisplayed()
    }
}
