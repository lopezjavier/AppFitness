package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.app.FakeCoachSummaryRepository
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.MarkWeekSummarySeen
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-98: the weekly summary screen, one test per Gherkin scenario plus the standard states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class WeekSummaryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<WeekSummaryEvent>()

    private fun show(state: WeekSummaryUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { WeekSummaryScreen(state, flowOf(online), { events += it }, {}) }
        }
        rule.waitForIdle()
    }

    private fun content(summary: WeekSummary = weekSummary()) =
        WeekSummaryUiState.Content(summary, mapOf("id-bench" to "Press banca"))

    @Test
    fun finishedWeek_showsEverySectionWithItsFigures() {
        show(content(weekSummary(calorieProposal = weekProposal(ProposalStatus.Applied))))

        rule.onNodeWithTag("week_summary_title").assertTextEquals("Resumen semanal")
        rule.onNodeWithTag("week_summary_subtitle").assertTextContains("Semana 2", substring = true)
        rule.onNodeWithText("Esta semana: 3 de 4 entrenos.").assertIsDisplayed()
        rule.onNodeWithText("Peso medio 80,2 kg (−0,3 kg/semana; objetivo −0,4 kg/semana).")
            .assertIsDisplayed()
        rule.onNodeWithText("Registros: 5 de 7 diarios completos · 3 pesajes.").assertIsDisplayed()
        rule.onNodeWithText("Propuesta de calorías: +100 kcal · Aplicada").assertIsDisplayed()
    }

    @Test
    fun indirectSetsCountHalf_andGroupsWithZeroAreOmitted() {
        show(content())

        rule.onNodeWithText("Series por grupo muscular").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_groups_line")
            .assertTextEquals("Pecho 10 · Espalda 12 · Tríceps 2,5")
    }

    @Test
    fun missingRecords_replaceTheCalorieLineButKeepWeightAndRecords() {
        show(content(weekSummary(missingRecords = true, calorieProposal = weekProposal())))

        rule.onNodeWithText("Esta semana faltan registros: no cambiamos tus calorías.").assertIsDisplayed()
        rule.onAllNodesWithTag("week_summary_section_weight").assertCountEquals(1)
        rule.onAllNodesWithTag("week_summary_section_records").assertCountEquals(1)
        rule.onNodeWithText("Propuesta de calorías: +100 kcal · Aplicada").assertDoesNotExist()
    }

    @Test
    fun deloadChangeRow_isShownReadOnlyAndNotRepeatedAsALine() {
        val deload = coachChange("d1", CoachChangeKind.DeloadScheduled, exerciseId = "")
        show(content(weekSummary(deload = true, changes = listOf(deload))))

        rule.onNodeWithTag("week_summary_change_row_d1").assertIsDisplayed()
        rule.onAllNodesWithTag("week_summary_section_deload").assertCountEquals(0)
        rule.onAllNodesWithTag("trainer_deload_cancel_d1").assertCountEquals(0)
    }

    @Test
    fun deloadWithoutARow_showsTheSeparateLine() {
        show(content(weekSummary(deload = true)))

        rule.onNodeWithTag("week_summary_section_deload").assertIsDisplayed()
    }

    @Test
    fun coachChangeRow_hasNoActions() {
        val change = coachChange("c1", exerciseId = "id-bench")
        show(content(weekSummary(changes = listOf(change))))

        rule.onNodeWithTag("week_summary_change_row_c1").assertIsDisplayed()
        rule.onAllNodesWithTag("trainer_coach_change_undo_c1").assertCountEquals(0)
    }

    @Test
    fun halfSetsAreSpokenAsOneSentence() {
        show(content(weekSummary(groups = listOf(GroupSets(MuscleGroup.Triceps, 2.5)))))

        rule.onNode(hasContentDescription("Tríceps, 2,5 series"))
            .assertIsDisplayed()
    }

    @Test
    fun noSummary_showsTheEmptyStateWithoutAction() {
        show(WeekSummaryUiState.Empty)

        rule.onNodeWithText("Aún no hay resumen semanal.").assertIsDisplayed()
        rule.onAllNodesWithTag("week_summary_retry").assertCountEquals(0)
    }

    @Test
    fun failedLoad_showsTheErrorWithRetry() {
        show(WeekSummaryUiState.Error)

        rule.onNodeWithText("No se pudo cargar el resumen semanal. Inténtalo de nuevo.").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_retry").performClick()

        assertEquals(listOf<WeekSummaryEvent>(WeekSummaryEvent.Retry), events)
    }

    @Test
    fun offline_showsTheOfflineState() {
        show(WeekSummaryUiState.Offline, online = false)

        rule.onNodeWithTag("week_summary_offline").assertIsDisplayed()
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(WeekSummaryUiState.Loading)

        rule.onNodeWithTag("week_summary_loading").assertIsDisplayed()
    }

    /** "No model": opening the summary reads and marks through the summary repository only. */
    @Test
    fun openingTheSummary_onlyTouchesTheSummaryRepository_andClearsTheNotice() {
        val env = ReminderEnv(LocalDate(2026, 11, 10))
        val summaries: FakeCoachSummaryRepository = env.summaries
        summaries.latest = Result.success(weekSummary(seen = false))
        val coordinator = ReminderCoordinator(env.load, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { coordinator.refresh() }
        assertEquals(listOf(ReminderKind.WeekSummary), coordinator.reminders.value.map { it.kind })
        val model = WeekSummaryViewModel(
            summaries,
            MarkWeekSummarySeen(summaries),
            coordinator,
            ExerciseNameResolver(FakeExerciseRepository(), FakeExerciseLibrary(emptyMap())),
            env.clock,
        )

        rule.setContent {
            AppTheme {
                WeekSummaryRoot(onBack = {}, viewModel = model, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitUntil(5_000) { summaries.seen.isNotEmpty() }
        rule.waitForIdle()

        rule.onNodeWithText("Esta semana: 3 de 4 entrenos.").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("plan-1" to 2), summaries.seen)
        rule.waitUntil(5_000) { coordinator.reminders.value.isEmpty() }
        assertTrue(coordinator.reminders.value.isEmpty())
    }
}
