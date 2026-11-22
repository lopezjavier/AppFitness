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
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val names = mapOf("id-bench" to "Press banca")
        val report = weekReportOf(summary, names, live = false)
        return PastReportUiState.Week(
            reportItem("week-1", paused = paused),
            WeekSummaryUiState.Content(summary, names),
            report,
        )
    }

    private fun block(
        decision: PhaseDecision?,
        review: BlockReview = v2Review(review = blockReview(seen = true)),
    ): PastReportUiState.Block {
        val content = reviewContent(review.copy(phaseDecision = decision), live = false)
        return PastReportUiState.Block(
            reportItem("block-1", ReportKind.BlockReview, week = null, phaseDecision = decision),
            content,
        )
    }

    @Test
    fun weekReport_showsTheStoredFiguresWithTheLiveTitleAndThePeriod() {
        show(week())

        rule.onNodeWithTag("past_report_title").assertTextEquals("Resumen semanal")
        rule.onNodeWithTag("past_report_subtitle").assertTextContains("3 al 9 nov", substring = true)
        rule.onNodeWithTag("week_summary_coach_card").assertIsDisplayed()
    }

    @Test
    fun pausedWeek_showsSemanaEnPausa() {
        show(week(paused = true))

        rule.onNodeWithTag("week_summary_paused").assertTextContains("Semana en pausa", substring = true)
    }

    @Test
    fun acceptedBlock_showsTheDecisionAsTextInTheCoachCardAndNoActions() {
        show(block(PhaseDecision.Accepted))

        rule.onNodeWithTag("past_report_title").assertTextEquals("Revisión del bloque")
        rule.onNodeWithTag("past_report_subtitle").assertTextContains("3 al 9 nov", substring = true)
        rule.onNodeWithTag("block_review_coach_action")
            .assertTextContains("Fase aceptada: Mantenimiento", substring = true)
        assertNoActions()
    }

    @Test
    fun keptBlock_showsSeguisteComoEstabasWithoutAPromise() {
        show(block(PhaseDecision.Kept))

        rule.onNodeWithTag("block_review_coach_action").assertTextEquals("Seguiste como estabas.")
        rule.onAllNodesWithText("volveremos a proponer", substring = true).assertCountEquals(0)
        assertNoActions()
    }

    @Test
    fun decidedBlock_showsTheDecisionOnce_andTheStoredProposalWithItsReason() {
        show(block(PhaseDecision.Accepted))

        rule.onAllNodesWithText("Fase aceptada", substring = true).assertCountEquals(1)
        rule.onNodeWithTag("block_review_phase_status").assertIsDisplayed()
        rule.onNodeWithText("Mantenimiento").assertIsDisplayed()
    }

    @Test
    fun undecidedBlock_showsNoDecisionTextNoPendingActionAndNoActions() {
        show(block(null))

        rule.onNodeWithTag("block_review_coach_action")
            .assertTextContains("Crea el siguiente bloque", substring = true)
        rule.onAllNodesWithText("Sin decidir").assertCountEquals(0)
        rule.onAllNodesWithText("pendiente", substring = true).assertCountEquals(0)
        assertNoActions()
    }

    @Test
    fun storedBlock_showsTheProgressBuiltAtTheBlockEnd() {
        show(block(PhaseDecision.Accepted))

        rule.onNodeWithTag("block_review_coach_sentence", useUnmergedTree = true)
            .assertTextContainsIgnoringNbsp("En este bloque: 2 ejercicios suben")
        rule.onNodeWithTag("block_review_strength_group_chest").assertTextContainsIgnoringNbsp("2 de 3 suben")
        rule.onNodeWithTag("block_review_lift_id-bench").assertIsDisplayed()
        rule.onNodeWithTag("block_review_section_weight").assertIsDisplayed()
        listOf("Entrenos", "Series por grupo", "Récord").forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun storedBlock_recordsShowThreeBadgesAndTheTotal() {
        val records = listOf("id-bench", "id-fly", "id-row").map(::recordBadge)
        show(block(null, v2Review(blockProgress(records = records, recordsTotal = 5))))

        rule.onNodeWithTag("block_review_record_3").assertIsDisplayed()
        rule.onNodeWithTag("block_review_records_total").assertTextContainsIgnoringNbsp("5 récords en el bloque")
    }

    @Test
    fun storedBlock_weightBelowTheGate_saysWeighInsAreMissing() {
        show(block(null, v2Review(blockProgress(weight = blockWeight(daysLast = 1, changeKg = null)))))

        rule.onNodeWithTag("block_review_weight_gate")
            .assertTextContainsIgnoringNbsp("Faltan pesajes para ver el cambio del bloque")
    }

    @Test
    fun storedBlock_withNoWeighIn_hasNoWeightCardAndSaysTheyWereMissing() {
        val none = blockProgress(missing = listOf(MissingKind.WeighIns), weight = null)
        show(block(null, v2Review(none, blockReview(phaseProposal = null))))

        rule.onAllNodesWithTag("block_review_section_weight").assertCountEquals(0)
        rule.onNodeWithTag("block_review_coach_action")
            .assertTextContains("Faltaron pesajes en el bloque", substring = true)
    }

    @Test
    fun storedBlock_objectivesShowTheChipOnlyWhenTrue() {
        val outcomes = listOf(weightOutcome(deadlinePassed, false), benchOutcome(deadlineAhead, false))
        show(block(null, v2Review(review = blockReview(outcomes = outcomes))))

        rule.onNode(hasTextIgnoringNbsp("Peso: objetivo 78 kg · ahora 79,8 kg")).assertExists()
        rule.onNodeWithTag("block_review_outcome_chip_0").assertTextContains("No conseguido")
        rule.onAllNodesWithTag("block_review_outcome_chip_1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun storedBlock_followsTheOrderOfTheGoal() {
        val strength = blockProgress().copy(intention = TrainingIntention.Strength)
        show(block(null, v2Review(strength)))

        val strengthTop = rule.onNodeWithTag("block_review_section_strength").fetchSemanticsNode().boundsInRoot.top
        val weightTop = rule.onNodeWithTag("block_review_section_weight").fetchSemanticsNode().boundsInRoot.top
        assertTrue(strengthTop < weightTop)
    }

    @Test
    fun storedV1Block_opensWithoutErrorShowingTheCoachActionAndTheProposal() {
        show(block(PhaseDecision.Accepted, blockReview(seen = true)))

        rule.onNodeWithTag("block_review_coach_action").assertTextContains("Fase aceptada", substring = true)
        rule.onAllNodesWithTag("block_review_coach_sentence", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("block_review_section_strength").assertCountEquals(0)
        rule.onNodeWithTag("block_review_phase_status").assertIsDisplayed()
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
        rule.onNodeWithTag("block_review_coach_action").assertIsDisplayed()
    }

    @Test
    fun recreation_keepsTheGroupSheetOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { PastReportScreen(block(PhaseDecision.Accepted), flowOf(true), {}, {}) } }
        rule.onNodeWithTag("block_review_strength_group_chest").performClick()
        rule.onNodeWithTag("block_review_strength_sheet_exercise_id-bench").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_strength_sheet_exercise_id-bench").assertIsDisplayed()
    }
}
