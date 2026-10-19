package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint

/** Exercises whose current best 1RM beats every previous session (the first eligible session is a record). */
object DetectPersonalRecords {
    /**
     * [currentBest] maps exercise id to the 1RM of the current session; [history] holds the exercise's points, which
     * may include the current session ([currentSessionId]) and is then ignored.
     */
    operator fun invoke(
        currentBest: Map<String, Double?>,
        history: Map<String, List<ExerciseHistoryPoint>>,
        currentSessionId: String? = null,
    ): Set<String> = currentBest.mapNotNull { (exerciseId, best) ->
        if (best == null) return@mapNotNull null
        val previous = history[exerciseId].orEmpty()
            .filter { it.sessionId != currentSessionId }
            .mapNotNull { it.bestOneRepMax }
            .maxOrNull()
        exerciseId.takeIf { previous == null || best > previous }
    }.toSet()
}
