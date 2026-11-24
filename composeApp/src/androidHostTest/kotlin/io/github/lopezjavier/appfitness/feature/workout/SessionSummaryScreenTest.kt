package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.feature.trainer.assertTextContainsIgnoringNbsp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-147: "Entreno completado" against the last time. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario and font scale.
class SessionSummaryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<LiveSessionEvent>()

    private fun cmp(position: Int, glyph: ProgressGlyph, previous: TopSet?, current: TopSet?) =
        ExerciseComparison(position, glyph, previous, current)

    private fun summary(
        comparisons: List<ExerciseComparison>,
        cut: Boolean = false,
        records: Map<Int, Set<PersonalRecordType>> = emptyMap(),
        names: List<String> = listOf("Press banca", "Remo con barra", "Sentadilla", "Press militar", "Dominadas"),
    ) = LiveSummary(
        SessionSummary(
            durationSeconds = 2_520,
            completedSets = 18,
            exercises = comparisons.map {
                ExerciseSummary(
                    it.position,
                    "e${it.position}",
                    1_000.0,
                    records[it.position].orEmpty().isNotEmpty(),
                    records[it.position].orEmpty(),
                )
            },
        ),
        exerciseNames = names.withIndex().associate { it.index to it.value },
        comparisons = comparisons,
        cut = cut,
    )

    private val everyState = listOf(
        cmp(0, ProgressGlyph.Up, TopSet(80.0, 8), TopSet(82.5, 6)),
        cmp(1, ProgressGlyph.Same, TopSet(60.0, 10), TopSet(60.0, 10)),
        cmp(2, ProgressGlyph.Down, TopSet(100.0, 5), TopSet(95.0, 5)),
        cmp(3, ProgressGlyph.Uncompared, null, TopSet(40.0, 8)),
        cmp(4, ProgressGlyph.Paused, TopSet(50.0, 8), TopSet(50.0, 7)),
    )

    private fun show(summary: LiveSummary, fontScale: Float = 1f) {
        rule.setContent {
            AppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    LiveSessionScreen(
                        LiveSessionUiState.Finished(summary),
                        0,
                        { events += it },
                        {},
                        onOpenExercise = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun row(position: Int) = rule.onNodeWithTag("session_summary_compare_e$position").performScrollTo()

    @Test
    fun everyExercise_readsItsFiguresAndItsState() {
        show(summary(everyState))

        row(0).assertTextContainsIgnoringNbsp("80 kg × 8 → 82,5 kg × 6").assertTextContainsIgnoringNbsp("Sube")
        row(1).assertTextContainsIgnoringNbsp("60 kg × 10 → 60 kg × 10").assertTextContainsIgnoringNbsp("Igual")
        row(2).assertTextContainsIgnoringNbsp("100 kg × 5 → 95 kg × 5").assertTextContainsIgnoringNbsp("Baja")
        rule.onNodeWithText("Frente a la última vez").assertIsDisplayed()
    }

    @Test
    fun anExerciseWithAnOpenDiscomfort_showsThePausedStateWithoutANote() {
        show(summary(everyState))

        row(4).assertTextContainsIgnoringNbsp("Con molestia")
        rule.onNodeWithTag("session_summary_state_e4", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithText("Con molestia", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun inACut_anUnchangedExerciseReadsMantienes() {
        show(summary(everyState, cut = true))

        row(1).assertTextContainsIgnoringNbsp("Mantienes")
    }

    @Test
    fun anExerciseWithoutAPreviousSession_readsSinCompararWithItsCurrentSetOnly() {
        show(summary(everyState))

        row(3).assertTextContainsIgnoringNbsp("40 kg × 8").assertTextContainsIgnoringNbsp("Sin comparar")
    }

    @Test
    fun recordsAndContext_badgePillsAndOneContextLine_noVolumeBars() {
        show(summary(everyState, records = mapOf(0 to setOf(PersonalRecordType.HeaviestLift))))

        rule.onNodeWithTag("session_summary_records").assertTextContainsIgnoringNbsp("1 récord")
        rule.onNodeWithTag("session_summary_record_0_heaviest_lift").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("session_summary_context_line").assertTextContainsIgnoringNbsp("42 min · 18 series")
        rule.onNodeWithTag("summary_volume_0").assertDoesNotExist()
        rule.onNodeWithTag("summary_duration").assertDoesNotExist()
        rule.onNodeWithTag("summary_sets").assertDoesNotExist()
    }

    @Test
    fun withoutAnyStrengthExercise_theSectionTitleIsHidden() {
        show(summary(emptyList()))

        rule.onNodeWithText("Frente a la última vez").assertDoesNotExist()
        rule.onNodeWithTag("summary_done").assertIsDisplayed()
    }

    @Test
    fun listo_sendsDone() {
        show(summary(everyState))

        rule.onNodeWithTag("summary_done").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.Done), events)
    }

    @Test
    fun font13_keepsEveryRowInsideTheScreenAndListoReachable() = assertFits(1.3f)

    @Test
    fun font20_keepsEveryRowInsideTheScreenAndListoReachable() = assertFits(2f)

    private fun assertFits(fontScale: Float) {
        show(summary(everyState, records = mapOf(0 to setOf(PersonalRecordType.HeaviestLift))), fontScale)

        everyState.forEach { comparison ->
            val bounds = row(comparison.position).getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= 0.dp && bounds.right <= 360.dp)
        }
        row(4).assertTextContainsIgnoringNbsp("Con molestia")
        rule.onNodeWithTag("summary_done").assertIsDisplayed()
    }
}
