package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository

/**
 * Reviews a routine folder with [FolderReviewCalculator] from data already on the device: the folder's routines,
 * the catalogue rows and the training intention. No model, no write. A failed read of the routines or the
 * exercises fails the review; a missing intention or a failed preferences read uses the other-intentions weights.
 */
class ReviewFolderUseCase(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val preferences: TrainingPreferencesRepository,
) {
    @Suppress("ReturnCount") // Early exits on a failed read keep the happy path flat.
    suspend operator fun invoke(folderId: String): Result<FolderReviewResult> {
        val loaded = loadRoutines(folderId).getOrElse { return Result.failure(it) }
        val catalogue = exercises.getAll().getOrElse { return Result.failure(it) }.associateBy { it.id }
        val intention = preferences.get().getOrNull()?.intention
        return Result.success(FolderReviewCalculator.compute(loaded.map { toReview(it, catalogue) }, intention))
    }

    @Suppress("ReturnCount")
    private suspend fun loadRoutines(folderId: String): Result<List<Routine>> {
        val summaries = routines.listActive().getOrElse { return Result.failure(it) }
        val loaded = summaries.filter { it.folderId == folderId }.map { summary ->
            routines.get(summary.id).getOrElse { return Result.failure(it) }
        }
        return Result.success(loaded)
    }

    private fun toReview(routine: Routine, catalogue: Map<String, Exercise>) = ReviewRoutine(
        routine.name,
        routine.exercises.sortedBy { it.position }.mapNotNull { planned ->
            val row = catalogue[planned.exerciseId] ?: return@mapNotNull null
            val range = FolderReviewCalculator.resolveRepRange(planned.repRange, planned.plannedSets)
            ReviewExercise(
                id = row.id,
                muscleGroup = row.muscleGroup,
                secondary = row.secondaryMuscles.distinct(),
                countsAsStrength = row.countsForVolume(),
                movementPattern = row.movementPattern,
                workingSets = planned.plannedSets.count { it.type != SetType.WarmUp },
                repMin = range?.first,
                repMax = range?.second,
            )
        },
    )
}
