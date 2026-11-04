package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-83 "Similares" tab: sorted rows with percentage, opening one, and the empty, offline and error states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ExerciseSimilarTabTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 23)
    private val bench = ExerciseDetailUiState.Loaded(
        detail = ExerciseDetail(exerciseRow("1", "bench"), "Press de banca", exerciseInfo("bench", "Press de banca")),
        tab = ExerciseDetailTab.Similar,
    )

    private fun similar(id: String, key: String, name: String, percentSimilarity: Double) = SimilarExerciseRow(
        CatalogExercise(exerciseRow(id, key, equipment = Equipment.BandLong), name, hasMedia = false),
        percentSimilarity,
    )

    private fun show(state: SimilarState, actions: ExerciseDetailActions = ExerciseDetailActions()) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    ExerciseDetailScreen(bench.copy(similar = state), false, {}, {}, today, actions = actions)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun similarTab_isTheSecondTab_andShowsItselfSelected() {
        show(SimilarState.Empty)

        rule.onNodeWithTag("exercise_detail_tab_similar").assertIsSelected()
    }

    @Test
    fun rows_showNameEquipmentAndPercentage_inTheGivenOrder() {
        show(
            SimilarState.Content(
                listOf(similar("2", "band-long-bench", "Press con banda", 1.0), similar("3", "fly", "Aperturas", 0.87)),
            ),
        )

        rule.onNodeWithTag("exercise_detail_similar_row_band-long-bench").assertExists()
        rule.onNodeWithTag("exercise_detail_similar_percent_band-long-bench", useUnmergedTree = true)
            .assertContentDescriptionEquals("100 por ciento de similitud")
        rule.onNodeWithTag("exercise_detail_similar_percent_fly", useUnmergedTree = true)
            .assertContentDescriptionEquals("87 por ciento de similitud")
    }

    @Test
    fun percentage_isACompactRing_withTheNumberInside_andTheFractionAsProgress() {
        show(SimilarState.Content(listOf(similar("3", "fly", "Aperturas", 0.87))))

        rule.onNodeWithTag("exercise_detail_similar_percent_fly", useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.87f, 0f..1f)),
        )
        rule.onNodeWithText("87", useUnmergedTree = true).assertExists()
    }

    @Test
    fun favoriteStar_isInlineAfterTheName_andTheRingIsTheOnlyTrailing() {
        val starred = similar("3", "fly", "Aperturas", 0.87).copy(isFavorite = true)
        show(SimilarState.Content(listOf(starred)))

        val name = rule.onNodeWithText("Aperturas", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val star = rule.onNodeWithTag("exercise_similar_favorite_fly", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val ring = rule.onNodeWithTag("exercise_detail_similar_percent_fly", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(star.left > name.left)
        assertTrue(star.left >= name.right)
        assertTrue(star.top < name.bottom && star.bottom > name.top)
        assertTrue(star.right <= ring.left)
        rule.onNodeWithTag("exercise_detail_similar_percent_fly", useUnmergedTree = true)
            .assertContentDescriptionEquals("87 por ciento de similitud")
    }

    @Test
    fun tappingARow_opensThatExercise() {
        val opened = mutableListOf<String>()
        show(
            SimilarState.Content(listOf(similar("3", "fly", "Aperturas", 0.87))),
            ExerciseDetailActions(onOpenExercise = { opened += it }),
        )

        rule.onNodeWithTag("exercise_detail_similar_row_fly").performClick()

        assertEquals(listOf("3"), opened)
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(SimilarState.Loading)

        rule.onNodeWithTag("exercise_detail_similar_loading").assertExists()
    }

    @Test
    fun empty_showsTheEmptyState() {
        show(SimilarState.Empty)

        rule.onNodeWithTag("exercise_detail_similar_empty").assertExists()
    }

    @Test
    fun offline_showsTheOfflineState() {
        show(SimilarState.Offline)

        rule.onNodeWithTag("exercise_detail_similar_offline").assertExists()
    }

    @Test
    fun error_offersRetry() {
        var retries = 0
        show(SimilarState.Error, ExerciseDetailActions(onRetrySimilar = { retries++ }))

        rule.onNodeWithTag("exercise_detail_similar_retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun tappingTheTab_asksForIt() {
        val asked = mutableListOf<ExerciseDetailTab>()
        show(SimilarState.Empty, ExerciseDetailActions(onSelectTab = { asked += it }))

        rule.onNodeWithTag("exercise_detail_tab_history").performClick()

        assertEquals(listOf(ExerciseDetailTab.History), asked)
    }
}
