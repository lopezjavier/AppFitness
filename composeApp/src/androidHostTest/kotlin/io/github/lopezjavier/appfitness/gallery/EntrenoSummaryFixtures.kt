package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.feature.workout.ExerciseComparison
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionUiState
import io.github.lopezjavier.appfitness.feature.workout.LiveSummary
import io.github.lopezjavier.appfitness.feature.workout.TopSet

/** Fixtures of the "Entreno completado" gallery entries (US-147). */
private const val SUMMARY_DURATION_SECONDS = 3_300L

private val summaryNames = listOf("Press de banca", "Remo con barra", "Sentadilla", "Press militar", "Dominadas")

internal fun cmp(position: Int, glyph: ProgressGlyph, previous: TopSet?, current: TopSet?) =
    ExerciseComparison(position, glyph, previous, current)

/** One exercise of every state: Sube, Igual, Baja, Sin comparar and Con molestia in one mixed list. */
internal val allStates = listOf(
    cmp(0, ProgressGlyph.Up, TopSet(80.0, 8), TopSet(82.5, 6)),
    cmp(1, ProgressGlyph.Same, TopSet(60.0, 10), TopSet(60.0, 10)),
    cmp(2, ProgressGlyph.Down, TopSet(100.0, 5), TopSet(95.0, 5)),
    cmp(3, ProgressGlyph.Uncompared, null, TopSet(40.0, 8)),
    cmp(4, ProgressGlyph.Paused, TopSet(50.0, 8), TopSet(50.0, 7)),
)

/** The summary of a finished workout; a record on the first exercise when [withRecords]. */
internal fun compareState(
    withRecords: Boolean = false,
    cut: Boolean = false,
    comparisons: List<ExerciseComparison>,
): LiveSessionUiState.Finished {
    val exercises = comparisons.map {
        val records = if (withRecords && it.position == 0) setOf(PersonalRecordType.HeaviestLift) else emptySet()
        ExerciseSummary(it.position, "ex${it.position}", 1_500.0, records.isNotEmpty(), records)
    }
    return LiveSessionUiState.Finished(
        LiveSummary(
            SessionSummary(SUMMARY_DURATION_SECONDS, completedSets = 14, exercises = exercises),
            exerciseNames = summaryNames.withIndex().associate { it.index to it.value },
            comparisons = comparisons,
            cut = cut,
        ),
    )
}

/** The two-exercise summary the older entries and the update-routine sheet sit on. */
internal fun summaryState(withRecords: Boolean) = compareState(
    withRecords = withRecords,
    comparisons = allStates.take(2),
)

/** Scrolls the summary so the row of the exercise with [exerciseId] is on screen. */
internal fun scrollSummaryTo(exerciseId: String): ComposeContentTestRule.() -> Unit = {
    onNodeWithTag("session_summary_compare_$exerciseId").performScrollTo()
}
