package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** US-28 "Historial" tab: sessions, set lines, record marks, paging states, empty and error, restoration. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseHistoryTabTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 23)
    private val bench = ExerciseDetailUiState.Loaded(
        detail = io.github.lopezjavier.appfitness.domain.model.ExerciseDetail(
            exerciseRow("1", "bench"),
            "Press de banca",
            exerciseInfo("bench", "Press de banca"),
        ),
        tab = ExerciseDetailTab.History,
    )

    private fun row(id: String, name: String = "Empuje", marks: Set<PersonalRecordType> = emptySet()) =
        ExerciseHistoryRow(
            sessionId = id,
            startedAt = Instant.parse("2026-10-22T10:00:00Z"),
            name = name,
            sets = listOf(
                HistorySetUi(1, SetType.WarmUp, "40 kg × 10", null),
                HistorySetUi(2, SetType.Working, "80 kg × 8", 8.5),
            ),
            records = marks,
        )

    private fun show(history: HistoryState, actions: ExerciseDetailActions = ExerciseDetailActions()) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExerciseDetailScreen(bench.copy(history = history), false, {}, {}, today, actions = actions)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun sessions_areListedInTheGivenOrder_withDateAndName() {
        show(HistoryState.Content(listOf(row("a", "Empuje A"), row("b", "Empuje B")), endReached = true))

        rule.onNodeWithTag("exercise_history_session_a").assertTextContains("Empuje A").assertTextContains("Ayer")
        rule.onNodeWithTag("exercise_history_session_b").assertTextContains("Empuje B")
    }

    @Test
    fun setLines_showTypeBadge_values_andRpe() {
        show(HistoryState.Content(listOf(row("a")), endReached = true))

        rule.onNodeWithText("40 kg × 10", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("80 kg × 8", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("RPE 9", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("C", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun beatenRecords_showAsPillsOnTheSession() {
        show(
            HistoryState.Content(
                listOf(row("a", marks = setOf(PersonalRecordType.HeaviestLift, PersonalRecordType.BestSet))),
                endReached = true,
            ),
        )

        rule.onNodeWithTag("exercise_history_record_0_heaviest_lift", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exercise_history_record_0_best_set", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aSessionWithoutMarks_hasNoPills() {
        show(HistoryState.Content(listOf(row("a")), endReached = true))

        rule.onNodeWithTag("exercise_history_records_0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun loadingNextPage_showsTheFooterIndicator() {
        show(HistoryState.Content(listOf(row("a")), loadingMore = true))

        rule.onNodeWithTag("exercise_history_loading_more", useUnmergedTree = true).assertExists()
    }

    @Test
    fun lastRowOnScreen_asksForTheNextPage() {
        var asked = 0
        show(
            HistoryState.Content(listOf(row("a")), endReached = false),
            ExerciseDetailActions(onLoadMoreHistory = { asked++ }),
        )

        assertEquals(true, asked >= 1)
    }

    @Test
    fun theEndOfTheHistory_neverAsksForMore() {
        var asked = 0
        show(
            HistoryState.Content(listOf(row("a")), endReached = true),
            ExerciseDetailActions(onLoadMoreHistory = { asked++ }),
        )

        assertEquals(0, asked)
    }

    @Test
    fun pageError_showsInlineRetry_thatRetriesThePage() {
        var retries = 0
        show(
            HistoryState.Content(listOf(row("a")), pageError = true),
            ExerciseDetailActions(onRetryHistoryPage = { retries++ }),
        )

        rule.onNodeWithTag("exercise_history_page_retry").assertTextEquals("Reintentar").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun noHistory_showsTheEmptyState() {
        show(HistoryState.Empty)

        rule.onNodeWithTag("exercise_history_empty_message").assertTextEquals("Aún no has hecho este ejercicio")
    }

    @Test
    fun firstPageLoading_showsSkeletons() {
        show(HistoryState.Loading)

        rule.onNodeWithTag("exercise_history_loading").assertExists()
    }

    @Test
    fun firstPageFailure_offersRetry() {
        var retries = 0
        show(HistoryState.Error, ExerciseDetailActions(onRetryHistory = { retries++ }))

        rule.onNodeWithTag("exercise_history_error_action").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun recreation_keepsTheTabAndTheScrollOfTheList() {
        val rows = (0 until MANY_ROWS).map { row("s$it", "Sesión $it") }
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    var tab by rememberSaveable { mutableStateOf(ExerciseDetailTab.Summary) }
                    ExerciseDetailScreen(
                        bench.copy(tab = tab, history = HistoryState.Content(rows, endReached = true)),
                        false,
                        {},
                        {},
                        today,
                        actions = ExerciseDetailActions(onSelectTab = { tab = it }),
                    )
                }
            }
        }
        rule.onNodeWithTag("exercise_detail_tab_history").performClick()
        rule.onNodeWithTag("exercise_history_list").performScrollToIndex(SCROLL_TARGET)
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("exercise_detail_tab_history").assertIsSelected()
        rule.onNodeWithTag("exercise_history_session_s$SCROLL_TARGET").assertExists()
        rule.onNodeWithTag("exercise_history_session_s0").assertDoesNotExist()
    }

    private companion object {
        const val MANY_ROWS = 40
        const val SCROLL_TARGET = 25
    }
}
