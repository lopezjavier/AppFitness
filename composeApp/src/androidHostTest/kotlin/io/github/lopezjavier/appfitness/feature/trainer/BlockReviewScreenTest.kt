package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockWaist
import io.github.lopezjavier.appfitness.domain.model.BlockWeight
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-101 and US-103: the end-of-block review screen, one test per Gherkin scenario plus the standard states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class BlockReviewScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockReviewEvent>()
    private val started = mutableListOf<Triple<BeforeStartMode, String?, String?>>()

    private fun show(state: BlockReviewUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                BlockReviewScreen(
                    state = state,
                    isOnline = flowOf(online),
                    onEvent = { events += it },
                    onBack = {},
                    onOpenBeforeStart = { mode, plan, replacing -> started += Triple(mode, plan, replacing) },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun content(
        review: BlockReview = blockReview(),
        deciding: PhaseDecision? = null,
        decisionFailed: PhaseDecision? = null,
        nextPlanId: String? = null,
        calorieFailed: CalorieChoice? = null,
        withCalorie: Boolean = false,
    ) = BlockReviewUiState.Content(
        review = review,
        exerciseNames = mapOf("id-bench" to "Press banca"),
        calorieProposal = if (withCalorie) calorieProposal() else null,
        deciding = deciding,
        decisionFailed = decisionFailed,
        calorieFailed = calorieFailed,
        nextPlanId = nextPlanId,
    )

    private fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun finishedBlock_showsEverySectionInOrder() {
        show(
            content(
                blockReview(
                    weight = BlockWeight(80.0, 79.0, -0.3, null, null, -0.4),
                    outcomes = listOf(BlockOutcome(OutcomeKind.Weight, "weight", 78.0, "kg", null, 79.0, false)),
                    waist = BlockWaist(-2.0, LocalDate(2026, 10, 24), LocalDate(2026, 11, 21), false),
                ),
            ),
        )

        rule.onNodeWithTag("block_review_title").assertTextEquals("Revisión del bloque")
        rule.onNodeWithText("Has hecho 14 de 16 entrenos (88 %).").assertIsDisplayed()
        val order = listOf("sessions", "lifts", "groups", "weight", "outcomes", "waist")
            .map { top("block_review_section_$it") }
        assertEquals(order.sorted(), order)
        rule.onNodeWithTag("block_review_section_sessions").assertIsDisplayed()
    }

    @Test
    fun mainLift_showsItsTrendLine() {
        show(content())

        rule.onNodeWithText("100 → 105 kg", substring = true).assertIsDisplayed()
        rule.onAllNodesWithTag("block_review_lift_id-bench_stall").assertCountEquals(0)
    }

    @Test
    fun stalledLift_showsTheChipAndItsDiagnosisLine() {
        show(
            content(
                blockReview(
                    lifts = listOf(blockLift(stalled = true)),
                    stalls = listOf(StallDiagnosis("id-bench", StallCause.NoClearCause(proteinGateMissing = false))),
                ),
            ),
        )

        rule.onNodeWithTag("block_review_lift_id-bench_stall").assertTextContains("Estancado")
        rule.onNodeWithTag("block_review_stall_id-bench_cause", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("block_review_stall_id-bench_action", useUnmergedTree = true).assertExists()
    }

    @Test
    fun outcomes_areShownAsTextWithoutChangingAnything() {
        show(
            content(
                blockReview(
                    outcomes = listOf(
                        BlockOutcome(OutcomeKind.Weight, "weight", 78.0, "kg", null, 77.5, true),
                        BlockOutcome(OutcomeKind.E1rm, "id-bench", 120.0, "kg", null, 105.0, false),
                    ),
                ),
            ),
        )

        rule.onNodeWithTag("block_review_outcome_0_chip").assertTextContains("Conseguido")
        rule.onNodeWithTag("block_review_outcome_1_chip").assertTextContains("No conseguido")
        assertTrue(events.isEmpty())
    }

    @Test
    fun waistLine_isShownWithItsDate() {
        show(content(blockReview(waist = BlockWaist(-2.0, LocalDate(2026, 10, 24), LocalDate(2026, 11, 21), false))))

        rule.onNodeWithTag("block_review_waist_line").assertTextEquals("Cintura: −2,0 cm desde el 24 oct")
    }

    @Test
    fun waistLine_withinTheMeasurementMarginSaysSo() {
        show(content(blockReview(waist = BlockWaist(-0.4, LocalDate(2026, 10, 24), LocalDate(2026, 11, 21), true))))

        rule.onNodeWithTag("block_review_waist_line")
            .assertTextContains("dentro del margen de medida", substring = true)
    }

    @Test
    fun withoutWaistData_noWaistSection() {
        show(content(blockReview(waist = null)))

        rule.onAllNodesWithTag("block_review_section_waist").assertCountEquals(0)
        rule.onAllNodesWithTag("block_review_waist_line").assertCountEquals(0)
    }

    @Test
    fun help_opensFromTheLiftsHeader() {
        show(content())

        rule.onNodeWithTag("block_review_help_lifts").performClick()

        rule.onNodeWithTag("block_review_help_lifts_sheet_body").assertIsDisplayed()
    }

    @Test
    fun cutOfThreeBlocks_proposesMaintenanceWithTheGherkinCopy() {
        show(content())

        rule.onNodeWithTag("block_review_phase").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Llevas 12 semanas de definición. Te proponemos 4 semanas comiendo lo que gastas antes de seguir.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Mantenimiento").assertIsDisplayed()
        rule.onNodeWithTag("block_review_phase_help").assertIsDisplayed()
    }

    @Test
    fun floorLimitedAndBackToGoal_eachShowAProposalCard() {
        val floor = PhaseProposal(
            Phase.Maintenance,
            PhaseChangeReason.FloorLimited,
            mapOf("slope_kg_week" to -0.1, "target_rate_kg_week" to -0.5),
        )
        show(content(blockReview(phaseProposal = floor)))
        rule.onNodeWithTag("block_review_phase").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("block_review_phase_accept").assertIsEnabled()
    }

    @Test
    fun backToGoal_showsTheGoalPhase() {
        val back = PhaseProposal(Phase.Cut, PhaseChangeReason.BackToGoal, emptyMap())
        show(content(blockReview(phaseProposal = back)))

        rule.onNodeWithText("Definición").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAProposal_noPhaseCard() {
        show(content(blockReview(phaseProposal = null)))

        rule.onAllNodesWithTag("block_review_phase").assertCountEquals(0)
        rule.onAllNodesWithTag("block_review_phase_status").assertCountEquals(0)
    }

    @Test
    fun aceptar_sendsTheAcceptedDecision() {
        show(content())

        rule.onNodeWithTag("block_review_phase_accept").performScrollTo().performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted)), events)
    }

    @Test
    fun seguirComoEstoy_sendsTheKeptDecision() {
        show(content())

        rule.onNodeWithTag("block_review_phase_discard").performScrollTo().performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.DecidePhase(PhaseDecision.Kept)), events)
    }

    @Test
    fun acceptedDecision_replacesTheCardWithAStatusLine() {
        show(content(blockReview(phaseDecision = PhaseDecision.Accepted)))

        rule.onAllNodesWithTag("block_review_phase").assertCountEquals(0)
        rule.onNodeWithTag("block_review_phase_status").performScrollTo()
        rule.onNodeWithText("Fase aceptada: Mantenimiento.").assertIsDisplayed()
    }

    @Test
    fun keptDecision_saysTheCoachWillProposeItAgain() {
        show(content(blockReview(phaseDecision = PhaseDecision.Kept)))

        rule.onNodeWithTag("block_review_phase_status").performScrollTo()
        rule.onNodeWithText("Sigues como estás. Te lo volveremos a proponer en la próxima revisión.")
            .assertIsDisplayed()
    }

    @Test
    fun failedDecision_offersReintentarThatRepeatsTheChoice() {
        show(content(decisionFailed = PhaseDecision.Accepted))

        rule.onNodeWithText("No se pudo guardar tu decisión. Inténtalo de nuevo.").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("block_review_phase_retry").performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.RetryDecision), events)
    }

    @Test
    fun offline_disablesAceptarAndTheFooter() {
        show(content(), online = false)

        rule.onNodeWithTag("block_review_phase_accept").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("block_review_next_block").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el bloque", substring = true).assertIsDisplayed()
    }

    @Test
    fun footer_isEnabledWhileTheProposalIsUndecided_andOpensTheBlockStart() {
        show(content())

        rule.onNodeWithTag("block_review_next_block").assertTextEquals("Crear el siguiente bloque").assertIsEnabled()
        assertTrue("nothing starts on its own", started.isEmpty())
        rule.onNodeWithTag("block_review_next_block").performClick()

        assertEquals(listOf(Triple(BeforeStartMode.Block, "plan-1", null)), started)
    }

    @Test
    fun footer_usesTheAcceptedPlanWhenThereIsOne() {
        show(content(blockReview(phaseDecision = PhaseDecision.Accepted), nextPlanId = "plan-2"))

        rule.onNodeWithTag("block_review_next_block").performClick()

        assertEquals(listOf(Triple(BeforeStartMode.Block, "plan-2", null)), started)
    }

    @Test
    fun pendingCalorieProposal_isDecidableHere() {
        show(content(withCalorie = true))

        rule.onNodeWithTag("block_review_calorie").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("block_review_calorie_accept").performScrollTo().performClick()
        rule.onNodeWithTag("block_review_calorie_discard").performScrollTo().performClick()

        assertEquals(listOf(BlockReviewEvent.ApplyCalorie, BlockReviewEvent.NotNowCalorie), events)
    }

    @Test
    fun failedCalorieDecision_offersReintentar() {
        show(content(withCalorie = true, calorieFailed = CalorieChoice.NotNow))

        rule.onNodeWithTag("block_review_calorie_retry").performScrollTo().performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.NotNowCalorie), events)
    }

    @Test
    fun withoutACalorieProposal_noCalorieCard() {
        show(content())

        rule.onAllNodesWithTag("block_review_calorie").assertCountEquals(0)
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(BlockReviewUiState.Loading)

        rule.onNodeWithTag("block_review_loading").assertIsDisplayed()
        rule.onAllNodesWithTag("block_review_next_block").assertCountEquals(0)
    }

    @Test
    fun noReview_showsTheEmptyMessage() {
        show(BlockReviewUiState.Empty)

        rule.onNodeWithText("Aún no hay revisión del bloque.").assertIsDisplayed()
    }

    @Test
    fun error_offersReintentar() {
        show(BlockReviewUiState.Error)

        rule.onNodeWithTag("block_review_retry").performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.Retry), events)
    }

    @Test
    fun offlineLoad_showsTheOfflineState() {
        show(BlockReviewUiState.Offline, online = false)

        rule.onNodeWithTag("block_review_offline").assertIsDisplayed()
    }
}
