package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryNutrition
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState
import io.github.lopezjavier.appfitness.gallery.TrainerFixtures
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val NBSP = 160.toChar().toString()
private const val XXHDPI = 3f

/**
 * The Trainer root as approved on 2026-11-15 (T-283 Block A): summary cards without actions (R-103), the details
 * screens that took the actions, offline gating, centred empty state (R-92), facts that never break (R-93) and the
 * pinned footers (R-99).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario of the root.
class TrainerRootCardsTest {
    @get:Rule
    val rule = createComposeRule()

    private val accepted = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))
    private val savedBlock = BlockSummary.Saved(coachBlock(saved = true))
    private val events = mutableListOf<TrainerEvent>()

    private fun pace(
        state: WeightPaceState,
        slope: Double? = -0.30,
        planRate: Double = -0.375,
        early: Boolean = false,
    ) = WeightPace(
        state = state,
        planRateKgWeek = planRate,
        slopeKgWeek = slope,
        slopeLowKgWeek = slope?.minus(0.2),
        slopeHighKgWeek = slope?.plus(0.2),
        weekly = emptyList(),
        daily = emptyList(),
        planLine = null,
        windowStart = LocalDate(2026, 10, 19),
        windowWeeks = 4,
        earlyCutNote = early,
        weeksInPhase = 4,
    )

    private fun planState(pace: WeightPace? = null, summary: WeekSummary? = null) =
        TrainerUiState.Content(
            plan = accepted,
            weightPace = pace,
            latestWeekSummary = summary,
            weeks = listOf(planWeek(week = 1, phase = WeekPhase.Current)),
        )

    private fun blockState(weeks: List<PlanWeek>) =
        TrainerUiState.Content(plan = accepted, block = savedBlock, weeks = weeks)

    private fun show(
        state: TrainerUiState,
        online: Boolean = true,
        actions: TrainerActions = TrainerActions(),
        fontScale: Float? = null,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides if (fontScale == null) density else Density(density.density, fontScale),
            ) {
                AppTheme { TrainerWithBoxes(state, flowOf(online), { events += it }, actions = actions) }
            }
        }
        rule.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun lineCount(interaction: SemanticsNodeInteraction): Int {
        val results = mutableListOf<TextLayoutResult>()
        val action = interaction.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue("node has no text layout", action != null && action(results))
        return results.first().lineCount
    }

    // "Tu plan" card

    @Test
    fun planCard_showsGoalPaceFiguresVerdictAndThreeTiles() {
        show(planState(pace(WeightPaceState.OnPace)))

        node("trainer_box_plan_goal").assertExists()
        node("trainer_box_plan_pace_actual").assertExists()
        node("trainer_box_plan_pace_goal").assertExists()
        node("trainer_box_plan_verdict").assertTextContains("Vas al ritmo previsto.")
        listOf("calories", "cardio", "steps").forEach { node("trainer_box_plan_tile_$it").assertExists() }
    }

    @Test
    fun planCard_isASummary_withNoActionNoPencilAndNoHelp() {
        show(planState(pace(WeightPaceState.OnPace)))

        node("trainer_create_plan_new").assertDoesNotExist()
        node("trainer_box_plan_pace_help").assertDoesNotExist()
        node("trainer_create_plan").assertDoesNotExist()
    }

    @Test
    fun planCard_tappingItsBody_opensTuPlan_once() {
        var opened = 0
        show(planState(pace(WeightPaceState.OnPace)), actions = TrainerActions(onOpenPlan = { opened++ }))

        tap("trainer_box_plan")

        assertEquals(1, opened)
    }

    private fun assertVerdict(case: WeightPace, text: String) {
        show(planState(case))

        node("trainer_box_plan_verdict").assertTextContains(text, substring = true)
    }

    @Test
    fun planCard_verdict_noData() =
        assertVerdict(pace(WeightPaceState.NoData, slope = null), "Aún no hay pesajes suficientes")

    @Test
    fun planCard_verdict_cutSlowerWhileFalling() =
        assertVerdict(pace(WeightPaceState.Slower, slope = -0.10), "Pierdes peso más despacio de lo previsto.")

    @Test
    fun planCard_verdict_cutSlowerButNotFalling_neverSaysPierdes() =
        assertVerdict(pace(WeightPaceState.Slower, slope = 0.05), "Tu peso no baja como estaba previsto.")

    @Test
    fun planCard_verdict_cutFaster() =
        assertVerdict(pace(WeightPaceState.Faster, slope = -0.80), "Pierdes peso más rápido de lo previsto.")

    @Test
    fun planCard_verdict_stable() = assertVerdict(pace(WeightPaceState.Stable, slope = 0.0), "Tu peso se mantiene estable.")

    @Test
    fun planCard_soon_showsOnlyTheProvisionalFigure_noVerdictLine() {
        show(planState(pace(WeightPaceState.Soon, slope = -0.2)))

        node("trainer_box_plan_pace_actual").assertTextContains("Ritmo provisional", substring = true)
        node("trainer_box_plan_verdict").assertDoesNotExist()
    }

    @Test
    fun planCard_noData_hidesTheActualPaceFigure() {
        show(planState(pace(WeightPaceState.NoData, slope = null)))

        node("trainer_box_plan_pace_actual").assertDoesNotExist()
        node("trainer_box_plan_pace_goal").assertExists()
    }

    @Test
    fun planCard_earlyCutNote_showsItsExtraLine() {
        show(planState(pace(WeightPaceState.Faster, slope = -0.8, early = true)))

        node("trainer_box_plan_verdict_early").assertExists()
    }

    @Test
    fun planCard_caloriesTile_saysSinDatos_withoutAGatedSummary() {
        show(planState(pace(WeightPaceState.OnPace)))

        node("trainer_box_plan_tile_calories").assertTextContains("sin datos", substring = true)
        node("trainer_box_plan_tiles_caption").assertDoesNotExist()
    }

    @Test
    fun planCard_caloriesTile_showsTheAverageAndTheCaption_withAGatedSummary() {
        val nutrition = WeekSummaryNutrition(5, true, 1950, 150.0, 2000, 150, 1.8, emptyList())
        show(planState(pace(WeightPaceState.OnPace), TrainerFixtures.weekSummary(nutrition = nutrition)))

        node("trainer_box_plan_tile_calories").assertTextContains("1.950", substring = true)
        node("trainer_box_plan_tiles_caption").assertExists()
    }

    // "Tu bloque" card and its screen

    @Test
    fun blockCard_isASummary_withoutActionsPencilOrDialog() {
        show(blockState(listOf(planWeek(week = 1), planWeek(week = 2, phase = WeekPhase.Upcoming))))

        listOf(
            "trainer_remove_block",
            "trainer_box_block_my_training",
            "trainer_box_block_extend_edit",
            "trainer_remove_block_dialog_confirm",
        ).forEach { node(it).assertDoesNotExist() }
    }

    @Test
    fun blockCard_tappingItsBody_opensTuBloque_once() {
        var opened = 0
        show(
            blockState(listOf(planWeek(week = 1))),
            actions = TrainerActions(onOpenBlockScreen = { opened++ }),
        )

        tap("trainer_box_block")

        assertEquals(1, opened)
    }

    @Test
    fun blockCard_withoutWeeks_showsTheRetryNote_andTappingItRefreshes() {
        show(blockState(emptyList()))

        node("trainer_box_block_weeks_error").assertExists()
        node("trainer_box_block_weeks_error").performClick()

        assertTrue(TrainerEvent.Refresh in events)
    }

    @Test
    fun tuBloque_miEntrenamiento_navigates_andRemoveRequestsTheDialog() {
        var training = 0
        show(
            blockState(listOf(planWeek(week = 1))),
            actions = TrainerActions(onOpenMyTraining = { training++ }),
        )
        rule.openTrainerBlock()

        tap("trainer_box_block_my_training")
        tap("trainer_remove_block")

        assertEquals(1, training)
        assertEquals(listOf<TrainerEvent>(TrainerEvent.RequestRemoveBlock), events)
    }

    @Test
    fun tuBloque_confirmingTheDialog_sendsConfirmRemoveBlock() {
        show(blockState(listOf(planWeek(week = 1))).copy(removeBlockConfirm = true))
        rule.openTrainerBlock()

        tap("trainer_remove_block_dialog_confirm")

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ConfirmRemoveBlock), events)
    }

    @Test
    fun tuBloque_offline_removalIsDisabled_butMiEntrenamientoStillNavigates() {
        var training = 0
        show(
            blockState(listOf(planWeek(week = 1))),
            online = false,
            actions = TrainerActions(onOpenMyTraining = { training++ }),
        )
        rule.openTrainerBlock()

        rule.onNodeWithTag("trainer_remove_block").assertIsNotEnabled()
        tap("trainer_box_block_my_training")
        assertEquals(1, training)
    }

    // Offline gating of the creating actions

    @Test
    fun offline_crearBloqueIsDisabled_withItsHint() {
        show(TrainerUiState.Content(plan = accepted), online = false)

        rule.onNodeWithTag("trainer_create_block").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión", substring = true).assertExists()
    }

    @Test
    fun offline_tuPlanCrearPlanNuevoIsDisabled() {
        show(planState(pace(WeightPaceState.OnPace)), online = false)
        rule.openTrainerPlan()

        rule.onNodeWithTag("trainer_create_plan_new").assertIsNotEnabled()
    }

    @Test
    fun offline_withoutAPlan_theEmptyStateHasNoActionAndShowsTheHint() {
        show(TrainerUiState.Content(), online = false)

        node("trainer_empty_action").assertDoesNotExist()
        rule.onNodeWithText("Sin conexión: no se puede crear el plan").assertExists()
    }

    // R-92: centred empty state

    @Test
    fun emptyRoot_theEmptyStateIsVerticallyCentredInTheFreeSpace() {
        show(TrainerUiState.Content())

        val screen = rule.onNodeWithTag("trainer_screen").fetchSemanticsNode().boundsInRoot
        val empty = node("trainer_empty").fetchSemanticsNode().boundsInRoot
        val ratio = (empty.center.y - screen.top) / screen.height
        assertTrue("centre at $ratio of the screen", ratio in 0.3f..0.7f)
    }

    // R-93: a fact never breaks, at 360 dp and font scale 1.0 and 1.3

    private fun assertFactsStayOnOneLine(fontScale: Float) {
        val nutrition = WeekSummaryNutrition(5, true, 1950, 150.0, 2000, 150, 1.8, emptyList())
        val weeks = listOf(
            planWeek(week = 1, phase = WeekPhase.Past),
            planWeek(week = 2, cardioMinTarget = 150, stepsTarget = 7000, stepsAvg = 6200),
            planWeek(week = 3, phase = WeekPhase.Upcoming),
            planWeek(week = 4, phase = WeekPhase.Upcoming),
        )
        show(
            TrainerUiState.Content(
                plan = accepted,
                block = savedBlock,
                weeks = weeks,
                weightPace = pace(WeightPaceState.OnPace),
                latestWeekSummary = TrainerFixtures.weekSummary(nutrition = nutrition),
            ),
            fontScale = fontScale,
        )

        listOf(
            "Semana${NBSP}2${NBSP}de${NBSP}4",
            "3${NBSP}de${NBSP}4",
            "75${NBSP}%",
            "de${NBSP}2.000${NBSP}kcal",
            "de${NBSP}150${NBSP}min",
        ).forEach { fact ->
            assertEquals(fact, 1, lineCount(rule.onNodeWithText(fact, useUnmergedTree = true)))
        }
    }

    @Test
    fun facts_stayOnOneLine_at360dpAndFontScale1() = assertFactsStayOnOneLine(1.0f)

    @Test
    fun facts_stayOnOneLine_at360dpAndFontScale1_3() = assertFactsStayOnOneLine(1.3f)

    // R-99: a pinned footer owns its padding (the buttons sit one screen margin from each edge, not two)

    @Test
    fun pinnedFooter_doesNotAddTheScreenMarginTwice() {
        rule.setContent {
            AppTheme {
                PlanProposalScreen(
                    PlanProposalUiState.Proposal(coachPlan(), unconfirmedValues = emptyList(), dataChanged = false),
                    flowOf(true),
                    {},
                    {},
                    {},
                    onFix = {},
                )
            }
        }
        rule.waitForIdle()

        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val discard = rule.onNodeWithTag("plan_proposal_discard").fetchSemanticsNode().boundsInRoot
        val accept = rule.onNodeWithTag("plan_proposal_accept").fetchSemanticsNode().boundsInRoot
        val margin = Spacing.screenMargin.value * XXHDPI
        assertEquals(screen.left + margin, discard.left, 2f)
        assertEquals(screen.right - margin, accept.right, 2f)
    }
}
