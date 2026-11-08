package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlin.time.Instant

internal const val FLAGGED_ID = "ex-bench"
internal const val CANDIDATE_FAVORITE_ID = "ex-dips"
internal const val CANDIDATE_OTHER_ID = "ex-fly"

/** Names for the flagged exercise and its two candidates (custom rows carry their own name). */
internal fun discomfortNames() = ExerciseNameResolver(
    FakeExerciseRepository(
        listOf(
            exerciseRow(FLAGGED_ID, null, name = "Press banca", custom = true),
            exerciseRow(CANDIDATE_FAVORITE_ID, null, name = "Fondos", custom = true),
            exerciseRow(CANDIDATE_OTHER_ID, null, name = "Aperturas", custom = true),
        ),
    ),
    FakeExerciseLibrary(emptyMap()),
)

internal fun followUp(
    offerSwap: Boolean = true,
    showNote: Boolean = false,
    noteReason: DiscomfortNoteReason? = null,
    exerciseId: String = FLAGGED_ID,
) = DiscomfortFollowUp(exerciseId, "d-1", Instant.parse("2026-10-20T10:00:00Z"), offerSwap, showNote, noteReason)

internal fun swapCandidates() = mapOf(
    FLAGGED_ID to listOf(SwapCandidate(CANDIDATE_FAVORITE_ID, true), SwapCandidate(CANDIDATE_OTHER_ID, false)),
)
