package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.LiftSeries
import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.LiftWeek
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val NBSP = 160.toChar().toString()
private const val FONT_SCALE_LARGE = 1.3f
private const val FONT_SCALE_MAX = 2f

/** US-143 on the live review (one test per Gherkin scenario) plus the decisions, footer and shell states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class BlockReviewScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockReviewEvent>()
    private val started = mutableListOf<Triple<BeforeStartMode, String?, String?>>()

    private fun show(state: BlockReviewUiState, online: Boolean = true, fontScale: Float? = null) {
        rule.setContent {
            AppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: 1f)) {
                    BlockReviewScreen(
                        state = state,
                        isOnline = flowOf(online),
                        onEvent = { events += it },
                        onBack = {},
                        onOpenBeforeStart = { mode, plan, replacing -> started += Triple(mode, plan, replacing) },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    private fun progress(intention: TrainingIntention = TrainingIntention.FatLoss) =
        blockProgress().copy(intention = intention)

    // Scenario: Coach card on top

    @Test
    fun coachCard_isFirstWithTheBlockSentenceAndThePendingPhaseAction() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_coach_sentence", useUnmergedTree = true)
            .assertTextContainsIgnoringNbsp("En este bloque: 2 ejercicios suben, Remo +8 kg (+10 %), peso −1,8 kg.")
        rule.onNodeWithTag("block_review_coach_action")
            .assertTextContainsIgnoringNbsp("Propuesta de fase: Mantenimiento · pendiente")
        val coachTop = top("block_review_coach_card")
        listOf("strength", "weight").forEach { assertTrue(coachTop < top("block_review_section_$it")) }
    }

    @Test
    fun coachAction_withoutAPhaseProposalButACalorieOne_isTheCalorieProposal() {
        show(reviewContent(v2Review(review = blockReview(phaseProposal = null)), withCalorie = true))
        rule.onNodeWithTag("block_review_coach_action").assertTextContainsIgnoringNbsp("kcal")
    }

    @Test
    fun coachAction_withAStall_pointsAtTheStallCard() {
        val stalled = blockReview(
            phaseProposal = null,
            stalls = listOf(StallDiagnosis("id-bench", StallCause.NoClearCause(proteinGateMissing = false))),
        )
        show(reviewContent(v2Review(review = stalled)))

        rule.onNodeWithTag("block_review_coach_action").assertTextContainsIgnoringNbsp("¿Por qué no progreso?")
        rule.onNodeWithTag("block_review_section_stalls").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun coachAction_withMissingWeighIns_asksForThreeDaysPerWeek() {
        val gap = blockProgress(missing = listOf(MissingKind.WeighIns), weight = null)
        show(reviewContent(v2Review(gap, blockReview(phaseProposal = null))))

        rule.onNodeWithTag("block_review_coach_action").assertTextContainsIgnoringNbsp("Pésate 3 días por semana")
    }

    @Test
    fun coachAction_withNothingPending_isCreateNextAsPlainText() {
        show(reviewContent(v2Review(review = blockReview(phaseProposal = null))))

        rule.onNodeWithTag("block_review_coach_action").assertTextContainsIgnoringNbsp("Crea el siguiente bloque")
    }

    // Scenario: Strength per muscle group over the block

    @Test
    fun strength_showsTheRingTheGroupRowAndTheContextLine() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_strength_summary").assertTextContainsIgnoringNbsp("2 de 3")
        rule.onNodeWithTag("block_review_strength_group_chest").assertTextContainsIgnoringNbsp("2 de 3 suben")
        rule.onNodeWithTag("block_review_strength_context", useUnmergedTree = true)
            .assertTextContainsIgnoringNbsp("14 de 16 entrenos · 170 de 180 series")
        rule.onNodeWithTag("block_review_strength_exercise_id-bench", useUnmergedTree = true).assertIsDisplayed()
        listOf("Entrenos", "Series por grupo", "Récord").forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun strength_hidesAnExerciseWithoutAName() {
        show(reviewContent())

        rule.onAllNodesWithTag("block_review_strength_exercise_id-ghost", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun groupSheet_opensFromTheGroupRowAndListsTheLoads() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_strength_group_chest").performClick()

        rule.onNodeWithTag("block_review_strength_sheet_exercise_id-bench").assertIsDisplayed()
    }

    // Scenario: Main lifts and records of the block

    @Test
    fun mainLifts_readSubeFromFourPercentAndTheMarginBelow() {
        val lifts = listOf(
            LiftSeries(
                "id-bench",
                listOf(LiftWeek(1, 100.0, false), LiftWeek(4, 105.0, false)),
                LiftStatus.Up,
                5.0,
                5.0,
            ),
            LiftSeries(
                "id-row",
                listOf(LiftWeek(1, 100.0, false), LiftWeek(4, 102.0, false)),
                LiftStatus.Margin,
                2.0,
                2.0,
            ),
        )
        show(reviewContent(v2Review(blockProgress(lifts = lifts))))

        rule.onNode(hasTextIgnoringNbsp("Sube +5 kg (+5 %)"), useUnmergedTree = true).assertExists()
        rule.onNode(hasTextIgnoringNbsp("dentro del margen de un día a otro"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun records_showThreeBadgesAndTheTotalWhenThereAreMore() {
        val records = listOf("id-bench", "id-fly", "id-row").map(::recordBadge)
        show(reviewContent(v2Review(blockProgress(records = records, recordsTotal = 7))))

        listOf(1, 2, 3).forEach { rule.onNodeWithTag("block_review_record_$it").assertIsDisplayed() }
        rule.onAllNodesWithTag("block_review_record_4").assertCountEquals(0)
        rule.onNodeWithTag("block_review_records_total").assertTextContainsIgnoringNbsp("7 récords en el bloque")
    }

    @Test
    fun records_withNoneHaveNoCard() {
        show(reviewContent())

        rule.onAllNodesWithTag("block_review_section_records").assertCountEquals(0)
    }

    // Scenario: Weight over the block

    @Test
    fun weight_withTheGatePassed_showsChangeChartGoalAndVerdict() {
        // A verdict needs enough weekly averages behind the trend; the short fixture trend reads "Soon" (no line).
        val longTrend = WeekWeightTrend(slope = -0.5, low = -0.7, high = -0.4, weeks = 8)
        show(reviewContent(v2Review(blockProgress(weight = blockWeight(trend = longTrend)))))

        rule.onNodeWithTag("block_review_weight_change").assertTextContainsIgnoringNbsp("1,8")
        rule.onNodeWithTag("block_review_weight_chart").assertExists()
        rule.onNodeWithTag("block_review_weight_goal").assertTextContainsIgnoringNbsp("Meta 76 kg · 15 feb")
        rule.onNodeWithTag("block_review_weight_verdict").assertExists()
    }

    @Test
    fun weight_belowTheGate_saysWeighInsAreMissing() {
        show(reviewContent(v2Review(blockProgress(weight = blockWeight(daysFirst = 2, changeKg = null)))))

        rule.onNodeWithTag("block_review_weight_gate")
            .assertTextContainsIgnoringNbsp("Faltan pesajes para ver el cambio del bloque")
        rule.onAllNodesWithTag("block_review_weight_change").assertCountEquals(0)
    }

    @Test
    fun weight_withNoWeighIn_hasNoCardAndTheCoachAsks() {
        val none = blockProgress(missing = listOf(MissingKind.WeighIns), weight = null)
        show(reviewContent(v2Review(none, blockReview(phaseProposal = null))))

        rule.onAllNodesWithTag("block_review_section_weight").assertCountEquals(0)
        rule.onNodeWithTag("block_review_coach_action").assertTextContainsIgnoringNbsp("Pésate 3 días por semana")
    }

    // Scenario: Objectives show the gap, the chip only when true

    @Test
    fun objectives_readTargetAndNow_withTheChipOnlyWhenTrue() {
        val outcomes = listOf(
            benchOutcome(deadlinePassed, reached = true),
            weightOutcome(deadlinePassed, reached = false),
            benchOutcome(deadlineAhead, reached = false).copy(key = "id-row"),
        )
        show(reviewContent(v2Review(review = blockReview(outcomes = outcomes))))

        rule.onNode(hasTextIgnoringNbsp("Press banca: objetivo 90 kg · ahora 84 kg")).assertExists()
        rule.onNode(hasTextIgnoringNbsp("Remo: objetivo 90 kg · ahora 84 kg")).assertExists()
        rule.onNode(hasTextIgnoringNbsp("Peso: objetivo 78 kg · ahora 79,8 kg")).assertExists()
        rule.onNodeWithTag("block_review_outcome_chip_0").assertTextContains("Conseguido")
        rule.onNodeWithTag("block_review_outcome_chip_1").assertTextContains("No conseguido")
        rule.onAllNodesWithTag("block_review_outcome_chip_2", useUnmergedTree = true).assertCountEquals(0)
        assertTrue(events.isEmpty())
    }

    @Test
    fun objectives_aWeightObjectiveUnderTheGate_neverSaysNoConseguido() {
        show(reviewContent(v2Review(review = blockReview(outcomes = listOf(weightOutcome(deadlinePassed, null))))))

        rule.onAllNodesWithTag("block_review_outcome_chip_0", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun objectives_theDeadlineDateNeverSplits() {
        show(reviewContent(v2Review(review = blockReview(outcomes = listOf(weightOutcome(deadlinePassed, false))))))

        rule.onNode(hasTextIgnoringNbsp("Fecha límite: 15 nov")).assertExists()
        rule.onNode(hasText("Fecha límite: 15${NBSP}nov")).assertExists()
    }

    // Scenario: Order by goal

    @Test
    fun order_fatLossPutsWeightFirst() {
        show(
            reviewContent(
                v2Review(progress(TrainingIntention.FatLoss), blockReview(outcomes = listOf(benchOutcome(null, null)))),
            ),
        )

        assertTrue(top("block_review_section_weight") < top("block_review_section_strength"))
        assertTrue(top("block_review_section_strength") < top("block_review_section_outcomes"))
        assertTrue(top("block_review_section_outcomes") < top("block_review_phase"))
    }

    @Test
    fun order_strengthPutsStrengthFirst_andStallThenPhaseThenCalorieComeLast() {
        val review = blockReview(
            stalls = listOf(StallDiagnosis("id-bench", StallCause.NoClearCause(proteinGateMissing = false))),
            outcomes = listOf(benchOutcome(null, null)),
        )
        show(reviewContent(v2Review(progress(TrainingIntention.Strength), review), withCalorie = true))

        val order = listOf("strength", "weight", "outcomes", "stalls").map { top("block_review_section_$it") } +
            listOf(top("block_review_phase"), top("block_review_calorie"))
        assertEquals(order.sorted(), order)
    }

    // A v1 review (stored before schema v2)

    @Test
    fun v1Review_opensWithTheCoachActionObjectivesStallsAndProposals_noError() {
        val review = blockReview(
            outcomes = listOf(benchOutcome(deadlinePassed, false)),
            stalls = listOf(StallDiagnosis("id-bench", StallCause.NoClearCause(proteinGateMissing = false))),
        )
        show(reviewContent(review))

        rule.onNodeWithTag("block_review_coach_action").assertIsDisplayed()
        rule.onAllNodesWithTag("block_review_coach_sentence", useUnmergedTree = true).assertCountEquals(0)
        listOf("strength", "lifts", "weight", "records").forEach {
            rule.onAllNodesWithTag("block_review_section_$it").assertCountEquals(0)
        }
        listOf("outcomes", "stalls").forEach { rule.onNodeWithTag("block_review_section_$it").assertIsDisplayed() }
        rule.onNodeWithTag("block_review_phase").performScrollTo().assertIsDisplayed()
    }

    // Decisions on the phase and calorie proposals

    @Test
    fun theReview_hasNoDietBreakSwitch() {
        show(reviewContent())

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertDoesNotExist()
        rule.onNodeWithTag("plan_proposal_diet_break_switch").assertDoesNotExist()
    }

    @Test
    fun cutOfThreeBlocks_proposesMaintenanceWithTheGherkinCopy() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_phase").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Llevas 12${NBSP}semanas de definición. " +
                "Te proponemos 4${NBSP}semanas comiendo lo que gastas antes de seguir.",
        ).assertIsDisplayed()
        rule.onNodeWithTag("block_review_phase_help").assertIsDisplayed()
    }

    @Test
    fun floorLimited_showsAProposalCard() {
        val floor = PhaseProposal(
            Phase.Maintenance,
            PhaseChangeReason.FloorLimited,
            mapOf("slope_kg_week" to -0.1, "target_rate_kg_week" to -0.5),
        )
        show(reviewContent(v2Review(review = blockReview(phaseProposal = floor))))

        rule.onNodeWithTag("block_review_phase").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("block_review_phase_accept").assertIsEnabled()
    }

    @Test
    fun backToGoal_showsTheGoalPhase() {
        val back = PhaseProposal(Phase.Cut, PhaseChangeReason.BackToGoal, emptyMap())
        show(reviewContent(v2Review(review = blockReview(phaseProposal = back))))

        rule.onNodeWithText("Definición").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAProposal_noPhaseCard() {
        show(reviewContent(v2Review(review = blockReview(phaseProposal = null))))

        rule.onAllNodesWithTag("block_review_phase").assertCountEquals(0)
        rule.onAllNodesWithTag("block_review_phase_status").assertCountEquals(0)
    }

    @Test
    fun aceptar_sendsTheAcceptedDecision() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_phase_accept").performScrollTo().performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted)), events)
    }

    @Test
    fun seguirComoEstoy_sendsTheKeptDecision() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_phase_discard").performScrollTo().performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.DecidePhase(PhaseDecision.Kept)), events)
    }

    @Test
    fun acceptedDecision_replacesTheCardWithAStatusLine() {
        show(reviewContent(v2Review(review = blockReview(phaseDecision = PhaseDecision.Accepted))))

        rule.onAllNodesWithTag("block_review_phase").assertCountEquals(0)
        rule.onNodeWithTag("block_review_phase_status").performScrollTo()
        rule.onNodeWithText("Fase aceptada: Mantenimiento.").assertIsDisplayed()
    }

    @Test
    fun keptDecision_saysTheCoachWillProposeItAgain() {
        show(reviewContent(v2Review(review = blockReview(phaseDecision = PhaseDecision.Kept))))

        rule.onNodeWithTag("block_review_phase_status").performScrollTo()
        rule.onNodeWithText("Sigues como estás. Te lo volveremos a proponer en la próxima revisión.")
            .assertIsDisplayed()
    }

    @Test
    fun failedDecision_offersReintentarThatRepeatsTheChoice() {
        show(reviewContent(decisionFailed = PhaseDecision.Accepted))

        rule.onNodeWithText("No se pudo guardar tu decisión. Inténtalo de nuevo.").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("block_review_phase_retry").performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.RetryDecision), events)
    }

    @Test
    fun offline_disablesAceptarAndTheFooter() {
        show(reviewContent(), online = false)

        rule.onNodeWithTag("block_review_phase_accept").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("block_review_next_block").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el bloque", substring = true).assertIsDisplayed()
    }

    @Test
    fun footer_isEnabledWhileTheProposalIsUndecided_andOpensTheBlockStart() {
        show(reviewContent())

        rule.onNodeWithTag("block_review_next_block").assertTextEquals("Crear el siguiente bloque").assertIsEnabled()
        assertTrue("nothing starts on its own", started.isEmpty())
        rule.onNodeWithTag("block_review_next_block").performClick()

        assertEquals(listOf(Triple(BeforeStartMode.Block, "plan-1", "plan-1")), started)
    }

    @Test
    fun footer_usesTheAcceptedPlanWhenThereIsOne() {
        show(
            reviewContent(
                v2Review(review = blockReview(phaseDecision = PhaseDecision.Accepted)),
                nextPlanId = "plan-2",
            ),
        )

        rule.onNodeWithTag("block_review_next_block").performClick()

        assertEquals(listOf(Triple(BeforeStartMode.Block, "plan-2", "plan-1")), started)
    }

    @Test
    fun pendingCalorieProposal_isDecidableHere() {
        show(reviewContent(withCalorie = true))

        rule.onNodeWithTag("block_review_calorie").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("block_review_calorie_accept").performScrollTo().performClick()
        rule.onNodeWithTag("block_review_calorie_discard").performScrollTo().performClick()

        assertEquals(listOf(BlockReviewEvent.ApplyCalorie, BlockReviewEvent.NotNowCalorie), events)
    }

    @Test
    fun failedCalorieDecision_offersReintentar() {
        show(reviewContent(withCalorie = true, calorieFailed = CalorieChoice.NotNow))

        rule.onNodeWithTag("block_review_calorie_retry").performScrollTo().performClick()

        assertEquals(listOf<BlockReviewEvent>(BlockReviewEvent.NotNowCalorie), events)
    }

    @Test
    fun withoutACalorieProposal_noCalorieCard() {
        show(reviewContent())

        rule.onAllNodesWithTag("block_review_calorie").assertCountEquals(0)
    }

    // Large fonts

    private fun assertReadableAt(scale: Float) {
        show(reviewContent(), fontScale = scale)
        rule.onNodeWithTag("block_review_strength_group_chest").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("block_review_weight_avg", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fontScale13_keepsTheStrengthRowAndTheWeightCardReadable() = assertReadableAt(FONT_SCALE_LARGE)

    @Test
    fun fontScale20_keepsTheStrengthRowAndTheWeightCardReadable() = assertReadableAt(FONT_SCALE_MAX)

    // Shell states

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
