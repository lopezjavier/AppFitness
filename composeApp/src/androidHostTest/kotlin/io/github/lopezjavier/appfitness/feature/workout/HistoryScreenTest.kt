package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun row(id: String, label: DateLabel, name: String = "Torso", sets: Int = 3) = HistoryRow(
    id = id,
    label = label,
    name = name,
    durationSeconds = 3_725,
    volumeKg = 2_120.0,
    completedSets = sets,
)

/** US-14 history list: rows, empty and error states, pagination and opening a session. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class HistoryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<HistoryEvent>()

    private fun show(state: HistoryUiState) {
        rule.setContent { AppTheme { HistoryScreen(state, { events += it }) } }
        rule.waitForIdle()
    }

    private fun inRow(id: String, text: String) = hasText(text) and hasAnyAncestor(hasTestTag("history_row_$id"))

    @Test
    fun rows_showDateNameDurationVolumeAndSets_inOrder() {
        show(
            HistoryUiState.Content(
                listOf(
                    row("a", DateLabel.Today),
                    row("b", DateLabel.Yesterday, name = "Pierna", sets = 1),
                    row("c", DateLabel.Other(DayOfWeek.MONDAY, 24, Month.OCTOBER), name = "Espalda"),
                ),
            ),
        )

        rule.onNodeWithTag("history_screen").assertExists()
        rule.onNode(inRow("a", "Hoy"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("b", "Ayer"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("c", "lun, 24 oct"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("b", "Pierna"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("a", "1:02:05"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("a", "2.120 kg"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("a", "3 series"), useUnmergedTree = true).assertExists()
        rule.onNode(inRow("b", "1 serie"), useUnmergedTree = true).assertExists()
        val tops = listOf("a", "b", "c").map {
            rule.onNodeWithTag("history_row_$it").fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun row_isAtLeast48dpTall_andDescribedForAccessibility() {
        show(HistoryUiState.Content(listOf(row("a", DateLabel.Today))))

        rule.onNodeWithTag("history_row_a").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("history_row_a").assertContentDescriptionContains("Hoy, Torso", substring = true)
    }

    @Test
    fun tappingARow_emitsOpenSession() {
        show(
            HistoryUiState.Content(listOf(row("a", DateLabel.Today), row("b", DateLabel.Yesterday)), endReached = true),
        )

        rule.onNodeWithTag("history_row_b").performClick()

        assertEquals(listOf<HistoryEvent>(HistoryEvent.OpenSession("b")), events)
    }

    @Test
    fun loading_showsSkeletonRows() {
        show(HistoryUiState.Loading)

        rule.onNodeWithTag("history_loading").assertExists()
    }

    @Test
    fun empty_showsTheEmptyState() {
        show(HistoryUiState.Empty)

        rule.onNodeWithTag("history_empty").assertExists()
        rule.onNodeWithText("Aún no has completado ningún entreno").assertExists()
    }

    @Test
    fun error_showsMessage_andRetryEmitsRetry() {
        show(HistoryUiState.Error)

        rule.onNodeWithTag("history_error").assertExists()
        rule.onNodeWithText("No se pudo cargar el historial").assertExists()
        rule.onNodeWithTag("history_error_action").performClick()

        assertEquals(listOf<HistoryEvent>(HistoryEvent.Retry), events)
    }

    @Test
    fun loadingMore_showsTheIndicatorAtTheEnd() {
        show(HistoryUiState.Content(listOf(row("a", DateLabel.Today)), loadingMore = true))

        rule.onNodeWithTag("history_loading_more").assertIsDisplayed()
    }

    @Test
    fun pageError_showsMessage_andRetryEmitsRetryLoadMore() {
        show(HistoryUiState.Content(listOf(row("a", DateLabel.Today)), pageError = true))

        rule.onNodeWithText("No se pudieron cargar más entrenos").assertExists()
        rule.onNodeWithTag("history_load_more_retry").performClick()

        assertEquals(listOf<HistoryEvent>(HistoryEvent.RetryLoadMore), events)
    }

    @Test
    fun scrollingToTheLastRow_emitsLoadMore() {
        val rows = (1..20).map { row("s$it", DateLabel.Today) }
        show(HistoryUiState.Content(rows))

        rule.onNodeWithTag("history_list").performScrollToNode(hasTestTag("history_row_s20"))
        rule.waitForIdle()

        assertTrue(events.contains(HistoryEvent.LoadMore))
    }

    @Test
    fun endReached_neverShowsFooterOrRequestsMore() {
        val rows = (1..3).map { row("s$it", DateLabel.Today) }
        show(HistoryUiState.Content(rows, endReached = true))

        rule.onNodeWithTag("history_loading_more").assertDoesNotExist()
        rule.onNodeWithTag("history_load_more_retry").assertDoesNotExist()
    }
}
