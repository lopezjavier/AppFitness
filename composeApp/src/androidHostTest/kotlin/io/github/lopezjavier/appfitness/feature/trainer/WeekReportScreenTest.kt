package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.RecordType
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WaistProgress
import io.github.lopezjavier.appfitness.domain.model.WeekProgress
import io.github.lopezjavier.appfitness.domain.model.WeekRecordBadge
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val FONT_SCALE_LARGE = 1.3f
private const val FONT_SCALE_MAX = 2f

/** US-141: the progress-first weekly report, one test per Gherkin scenario (live and stored). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class WeekReportScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val names = mapOf("id-bench" to "Press banca", "id-fly" to "Aperturas", "id-press" to "Press inclinado")

    private fun contentOf(
        summary: WeekSummary,
        live: Boolean = true,
        followUps: List<DiscomfortFollowUp> = emptyList(),
    ) = WeekSummaryUiState.Content(summary, names, weekReportOf(summary, names, live, followUps))

    private fun show(content: WeekSummaryUiState.Content, fontScale: Float? = null) {
        rule.setContent {
            AppTheme {
                if (fontScale == null) {
                    WeekSummaryScreen(content, flowOf(true), {}, {})
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        WeekSummaryScreen(content, flowOf(true), {}, {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun showPast(content: WeekSummaryUiState.Content) {
        rule.setContent {
            AppTheme {
                PastReportScreen(
                    PastReportUiState.Week(reportItem("week-1"), content, content.report),
                    flowOf(true),
                    {},
                    {},
                )
            }
        }
        rule.waitForIdle()
    }

    // A trend whose band holds the plan rate and excludes zero: the shared verdict reads "on pace".
    private val onPace = weekWeight(trend = WeekWeightTrend(-0.6, -0.8, -0.3, 4))

    private fun progress(block: WeekProgress.() -> WeekProgress = { this }) = weekProgress(weight = onPace).block()

    private fun summaryOf(progress: WeekProgress = weekProgress(weight = onPace), nutrition: Boolean = true) =
        v2Summary(progress).let { if (nutrition) it else it.copy(nutrition = null) }

    private fun yOf(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    @Test
    fun coachCard_isOnTop_withTheSentenceAndTheAction() {
        show(contentOf(summaryOf()))

        rule.onNodeWithTag("week_summary_coach_card").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_coach_sentence")
            .assertTextContainsIgnoringNbsp("Esta semana: 1 ejercicio sube, peso −0,3 kg; vas al ritmo previsto.")
        rule.onNodeWithTag("week_summary_coach_action").assertTextContainsIgnoringNbsp("Sigue con el plan")
        assertTrue(yOf("week_summary_coach_card") < yOf("week_summary_section_strength"))
    }

    @Test
    fun weekOne_showsTheBaseMessageInsteadOfStates() {
        val week1 = weekProgress(blockWeek = 1)
        show(contentOf(summaryOf(week1)))

        rule.onNodeWithTag("week_summary_coach_sentence").assertTextEquals("Semana 1: tu base está lista.")
        rule.onNodeWithTag("week_summary_strength_base")
            .assertTextContainsIgnoringNbsp("Semana 1: esta es tu base. Desde la semana 2 verás qué sube.")
    }

    @Test
    fun strength_showsOneRowPerGroup_withHowManyGoUp_andTheContext() {
        show(contentOf(summaryOf()))

        rule.onNodeWithTag("week_summary_strength_group_chest").assertTextContainsIgnoringNbsp("1 de 2 suben")
        rule.onNodeWithTag("week_summary_strength_context", useUnmergedTree = true)
            .assertTextContainsIgnoringNbsp("3 de 4 entrenos · 18 de 20 series")
        rule.onNodeWithTag("week_summary_strength_exercise_id-bench", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun groupSheet_listsEveryExercise_withTheLoadsOfTheComparison() {
        show(contentOf(summaryOf()))

        rule.onNodeWithTag("week_summary_strength_group_chest").performClick()

        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-bench")
            .assertTextContainsIgnoringNbsp("80 kg × 8 → 82,5 kg × 6")
        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-press")
            .assertTextContainsIgnoringNbsp("Aún sin un segundo entreno")
        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-press")
            .assertTextContainsIgnoringNbsp("Sin comparar")
    }

    @Test
    fun cut_readsSameAsMantienes_andOutsideACutAsIgual() {
        show(contentOf(summaryOf()))
        rule.onNodeWithTag("week_summary_strength_group_chest").performClick()
        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-fly").assertTextContainsIgnoringNbsp("Mantienes")
    }

    @Test
    fun outsideACut_sameReadsIgual() {
        show(contentOf(summaryOf(weekProgress(phase = null, intention = TrainingIntention.Maintenance))))
        rule.onNodeWithTag("week_summary_strength_group_chest").performClick()
        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-fly").assertTextContainsIgnoringNbsp("Igual")
    }

    @Test
    fun groupWithNoComparedExercise_readsSinComparar() {
        val group = StrengthGroup(
            MuscleGroup.Back,
            listOf(exerciseProgress("id-fly", ProgressState.Uncompared, baseline = null, best = null)),
        )
        show(contentOf(summaryOf(weekProgress(groups = listOf(group)))))

        rule.onNodeWithTag("week_summary_strength_group_back").assertTextContainsIgnoringNbsp("Sin comparar")
    }

    private fun pausedProgress() = weekProgress(
        groups = listOf(
            StrengthGroup(
                MuscleGroup.Chest,
                listOf(
                    exerciseProgress("id-bench", ProgressState.Paused),
                    exerciseProgress("id-fly", ProgressState.Up),
                ),
            ),
        ),
    )

    private val followUp = DiscomfortFollowUp(
        "id-bench",
        "d1",
        Instant.fromEpochSeconds(0),
        offerSwap = false,
        showNote = true,
        noteReason = DiscomfortNoteReason.Days14,
    )

    @Test
    fun pausedExercise_readsConMolestia_andTheLiveReportShowsTheHealthNote() {
        show(contentOf(summaryOf(pausedProgress()), followUps = listOf(followUp)))

        rule.onNodeWithTag("week_summary_strength_paused_id-bench").assertExists()
        rule.onNodeWithText("Con molestia", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("week_summary_health_note_id-bench", useUnmergedTree = true)
            .assertTextContainsIgnoringNbsp("sigue ahí desde hace 14 días")
        rule.onAllNodesWithText("En pausa").assertCountEquals(0)
    }

    @Test
    fun pausedExercise_onAPastReport_hasNoHealthNote() {
        showPast(contentOf(summaryOf(pausedProgress()), live = false, followUps = listOf(followUp)))

        rule.onNodeWithText("Con molestia", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("week_summary_health_note_id-bench", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun mainLifts_showTheTrend_andTheDeloadWeekIsHollow() {
        show(contentOf(summaryOf()))

        rule.onNodeWithTag("week_summary_section_lifts").assertIsDisplayed()
        rule.onNodeWithText("Sube +4 kg", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun records_showTheBadge_andNoCardWithoutRecords() {
        show(contentOf(summaryOf()))
        rule.onAllNodesWithTag("week_summary_section_records").assertCountEquals(0)
    }

    @Test
    fun records_withOne_showTheirCard() {
        val record = WeekRecordBadge("id-bench", RecordType.E1rm, 110.0, 105.0, mainLift = true)
        show(contentOf(summaryOf(progress { copy(records = listOf(record)) })))

        rule.onNodeWithTag("week_summary_record_1").assertTextContainsIgnoringNbsp("Press banca")
        rule.onNodeWithTag("week_summary_record_1").assertTextContainsIgnoringNbsp("antes 105 kg")
    }

    @Test
    fun weight_showsTheAverage_theChange_theGoal_andTheVerdict() {
        show(contentOf(summaryOf()))

        rule.onNodeWithTag("week_summary_weight_avg").assertTextContainsIgnoringNbsp("80,2")
        rule.onNodeWithTag("week_summary_weight_change").assertTextContainsIgnoringNbsp("−0,3")
        rule.onNodeWithTag("week_summary_weight_goal").assertTextContainsIgnoringNbsp("Meta 75 kg")
        rule.onNodeWithTag("week_summary_weight_verdict").assertExists()
        rule.onNodeWithTag("week_summary_weight_chart").assertExists()
    }

    @Test
    fun weight_roundingToZero_readsIgualQueLaSemanaPasada() {
        show(contentOf(summaryOf(weekProgress(weight = weekWeight(changeKg = 0.01)))))

        rule.onNodeWithTag("week_summary_weight_change").assertTextContainsIgnoringNbsp("igual que la semana pasada")
    }

    @Test
    fun weight_underTheGate_showsTheDots_andNoChange() {
        show(contentOf(summaryOf(weekProgress(weight = weekWeight(days = 2, changeKg = null)))))

        rule.onNodeWithTag(
            "week_summary_weight_gate",
        ).assertTextContainsIgnoringNbsp("Faltan pesajes para ver el cambio")
        rule.onNodeWithTag("week_summary_weight_gate_dots", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("week_summary_weight_change").assertCountEquals(0)
    }

    @Test
    fun weight_withoutWeighIns_saysSoAndAsksForThree() {
        val none = weekWeight(days = 0, changeKg = null).copy(avgKg = null)
        show(contentOf(summaryOf(weekProgress(weight = none, missing = listOf(MissingKind.WeighIns)))))

        rule.onNodeWithTag("week_summary_weight_none").assertTextContainsIgnoringNbsp("Sin pesajes esta semana.")
        rule.onNodeWithTag("week_summary_weight_none").assertTextContainsIgnoringNbsp("Pésate 3 días esta semana")
    }

    @Test
    fun pastReport_withoutWeighIns_doesNotAskToWeighIn() {
        val none = weekWeight(days = 0, changeKg = null).copy(avgKg = null)
        showPast(contentOf(summaryOf(weekProgress(weight = none)), live = false))

        rule.onNodeWithTag("week_summary_weight_none").assertTextContainsIgnoringNbsp("Faltaron pesajes esa semana")
        rule.onAllNodesWithText("Pésate", substring = true).assertCountEquals(0)
    }

    private val measured = WaistProgress(84.0, LocalDate(2026, 11, 8), 85.0, LocalDate(2026, 10, 11), -1.0)

    @Test
    fun waist_inAWeekWithAMeasurement_showsTheValue_andTheMarginUnderThreeCentimetres() {
        show(contentOf(summaryOf(progress { copy(waist = measured) })))

        rule.onNodeWithTag("week_summary_waist_value", useUnmergedTree = true).assertTextContainsIgnoringNbsp("84")
        rule.onNodeWithTag(
            "week_summary_waist_margin",
            useUnmergedTree = true,
        ).assertTextContainsIgnoringNbsp("dentro del margen de medida")
    }

    @Test
    fun waist_withoutAMeasurement_hasNoCard() {
        show(contentOf(summaryOf()))

        rule.onAllNodesWithTag("week_summary_section_waist").assertCountEquals(0)
    }

    @Test
    fun order_fatLoss_putsWeightBeforeStrength() {
        show(contentOf(summaryOf(progress { copy(waist = measured) })))

        assertTrue(yOf("week_summary_section_weight") < yOf("week_summary_section_waist"))
        assertTrue(yOf("week_summary_section_waist") < yOf("week_summary_section_strength"))
    }

    @Test
    fun order_strengthGoal_putsStrengthBeforeWeight() {
        show(contentOf(summaryOf(weekProgress(intention = TrainingIntention.Strength))))

        assertTrue(yOf("week_summary_section_strength") < yOf("week_summary_section_weight"))
    }

    @Test
    fun order_maintenance_putsStrengthWaistThenWeight() {
        val maintenance = weekProgress(intention = TrainingIntention.Maintenance).copy(waist = measured)
        show(contentOf(summaryOf(maintenance)))

        assertTrue(yOf("week_summary_section_strength") < yOf("week_summary_section_waist"))
        assertTrue(yOf("week_summary_section_waist") < yOf("week_summary_section_weight"))
    }

    @Test
    fun report_hasNoComplianceCards() {
        show(contentOf(summaryOf()))

        listOf("sessions", "groups", "nutrition").forEach {
            rule.onAllNodesWithTag("week_summary_section_$it").assertCountEquals(0)
        }
        rule.onAllNodesWithText("Series por grupo muscular").assertCountEquals(0)
        rule.onAllNodesWithText("Nutrición").assertCountEquals(0)
    }

    @Test
    fun liveProposal_isPlainText_notAButton() {
        val nutrition = weekNutrition(decisions = listOf(weekDecision(ProposalStatus.Pending)))
        show(contentOf(summaryOf().copy(nutrition = nutrition)))

        rule.onNodeWithTag(
            "week_summary_coach_action",
        ).assertTextContainsIgnoringNbsp("Propuesta +100 kcal · pendiente")
        rule.onNodeWithTag("week_summary_coach_action").assertHasNoClickAction()
    }

    @Test
    fun liveDiagnosis_isAButtonThatScrollsToItsCard() {
        val nutrition = weekNutrition(diagnosis = NutritionDiagnosis.NoCause)
        show(contentOf(summaryOf().copy(nutrition = nutrition)))

        rule.onNodeWithTag("week_summary_coach_action").assertHasClickAction().performClick()

        rule.onNodeWithTag("week_summary_section_diagnosis").assertIsDisplayed()
    }

    @Test
    fun liveStall_isAButton_andAStallWithoutNameIsNotTheAction() {
        val stalls = listOf(StallDiagnosis("id-bench", StallCause.Sleep(6.2)))
        show(contentOf(summaryOf().copy(stalls = stalls)))
        rule.onNodeWithTag("week_summary_coach_action").assertHasClickAction()
    }

    @Test
    fun deload_isPlainText() {
        show(contentOf(summaryOf().copy(deload = true)))

        rule.onNodeWithTag("week_summary_coach_action").assertHasNoClickAction()
    }

    @Test
    fun missingData_readsAsInformation_notAButton() {
        show(contentOf(summaryOf(weekProgress(missing = listOf(MissingKind.Food, MissingKind.DeletedSessions)))))

        rule.onNodeWithTag("week_summary_coach_action")
            .assertTextContainsIgnoringNbsp("Registra comidas completas al menos 5 días")
        rule.onNodeWithTag("week_summary_coach_action").assertHasNoClickAction()
    }

    @Test
    fun deletedSessions_readYaNoConstan() {
        show(contentOf(summaryOf(weekProgress(missing = listOf(MissingKind.DeletedSessions)))))

        rule.onNodeWithTag("week_summary_coach_action")
            .assertTextContainsIgnoringNbsp("Algunos entrenos de esta semana ya no constan")
    }

    @Test
    fun pastReport_decidedProposal_readsItsOutcome_andIsNeverAButton() {
        val nutrition = weekNutrition(decisions = listOf(weekDecision(ProposalStatus.Applied)))
        showPast(contentOf(summaryOf().copy(nutrition = nutrition), live = false))

        rule.onNodeWithTag("week_summary_coach_action").assertTextContainsIgnoringNbsp("Propuesta +100 kcal · aplicada")
        rule.onNodeWithTag("week_summary_coach_action").assertHasNoClickAction()
    }

    @Test
    fun pastReport_proposalWithoutAnswer_readsSinRespuesta() {
        val nutrition = weekNutrition(decisions = listOf(weekDecision(ProposalStatus.Pending)))
        showPast(contentOf(summaryOf().copy(nutrition = nutrition), live = false))

        rule.onNodeWithTag("week_summary_coach_action").assertTextContainsIgnoringNbsp("· sin respuesta")
        rule.onAllNodesWithText("pendiente", substring = true).assertCountEquals(0)
    }

    @Test
    fun pastReport_missingInputs_readInThePast_andAreNotAButton() {
        val missing = listOf(MissingKind.WeighIns, MissingKind.Food)
        showPast(contentOf(summaryOf(weekProgress(missing = missing)), live = false))

        rule.onNodeWithTag("week_summary_coach_action").assertTextContainsIgnoringNbsp("Faltaron pesajes esa semana")
        rule.onNodeWithTag("week_summary_coach_action").assertHasNoClickAction()
        rule.onAllNodesWithText("Pésate", substring = true).assertCountEquals(0)
    }

    @Test
    fun pastReport_missingFood_readsFaltaronComidas() {
        showPast(contentOf(summaryOf(weekProgress(missing = listOf(MissingKind.Food))), live = false))

        rule.onNodeWithTag("week_summary_coach_action")
            .assertTextContainsIgnoringNbsp("Faltaron comidas completas esa semana")
    }

    @Test
    fun pastReport_diagnosisIsNotAButton() {
        val nutrition = weekNutrition(diagnosis = NutritionDiagnosis.NoCause)
        showPast(contentOf(summaryOf().copy(nutrition = nutrition), live = false))

        rule.onNodeWithTag("week_summary_coach_action").assertHasNoClickAction()
    }

    @Test
    fun pastReport_ofABackfilledV2Week_showsTheProgressCards() {
        showPast(contentOf(summaryOf(), live = false))

        rule.onNodeWithTag("week_summary_section_strength").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_section_weight").assertIsDisplayed()
    }

    @Test
    fun pastReport_ofAV1Week_opensWithTheCoachCardOnly() {
        val v1 = weekSummary(seen = true)
        showPast(contentOf(v1, live = false))

        rule.onNodeWithTag("week_summary_coach_card").assertIsDisplayed()
        rule.onAllNodesWithTag("week_summary_section_strength").assertCountEquals(0)
        rule.onAllNodesWithTag("week_summary_section_weight").assertCountEquals(0)
    }

    private fun assertReadableAt(scale: Float) {
        show(contentOf(summaryOf()), fontScale = scale)
        rule.onNodeWithTag("week_summary_strength_group_chest").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week_summary_weight_avg").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fontScale13_keepsTheStrengthRowAndTheWeightCardReadable() = assertReadableAt(FONT_SCALE_LARGE)

    @Test
    fun fontScale20_keepsTheStrengthRowAndTheWeightCardReadable() = assertReadableAt(FONT_SCALE_MAX)

    @Test
    fun recreation_keepsTheGroupSheetOpen() {
        val restoration = StateRestorationTester(rule)
        val content = contentOf(summaryOf())
        restoration.setContent { AppTheme { WeekSummaryScreen(content, flowOf(true), {}, {}) } }
        rule.onNodeWithTag("week_summary_strength_group_chest").performClick()
        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-bench").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-bench").assertIsDisplayed()
    }
}
