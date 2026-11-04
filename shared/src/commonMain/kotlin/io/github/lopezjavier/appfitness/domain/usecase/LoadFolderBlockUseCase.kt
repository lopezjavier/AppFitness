package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockDraft
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockExercise
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockRoutine
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository

/** A folder read for "Desde una carpeta": the editable copy plus what saving needs. */
data class FolderBlockSource(
    val draft: FolderBlockDraft,
    val folderName: String,
    /** Names of every folder, so the saved name can be made unique. */
    val existingFolderNames: Set<String>,
    val sessionMinutes: Int,
)

/**
 * Reads a folder and the user's training preferences into a [FolderBlockSource]; read only, nothing is written.
 * Failures: the repository's own exception for a failed read, [IllegalStateException] for missing days or minutes.
 */
class LoadFolderBlockUseCase(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val preferences: TrainingPreferencesRepository,
) {
    @Suppress("ReturnCount") // One early exit per failed read.
    suspend operator fun invoke(folderId: String): Result<FolderBlockSource> {
        val prefs = preferences.get().getOrElse { return Result.failure(it) }
        val days = prefs.daysPerWeek
        val minutes = prefs.sessionMinutes
        if (days == null || minutes == null) {
            return Result.failure(IllegalStateException("Training days or minutes missing"))
        }
        val loaded = loadFolderRoutines(routines, folderId).getOrElse { return Result.failure(it) }
        val catalogue = exercises.getAll().getOrElse { return Result.failure(it) }.associateBy { it.id }
        val folders = routines.listFolders().getOrElse { return Result.failure(it) }
        val draft = FolderBlockDraft(
            routines = loaded.map { toBlockRoutine(it, catalogue) },
            intention = prefs.intention,
            daysPerWeek = days,
            equipment = prefs.equipment,
        )
        return Result.success(
            FolderBlockSource(
                draft = draft,
                folderName = folders.firstOrNull { it.id == folderId }?.name.orEmpty(),
                existingFolderNames = folders.map { it.name }.toSet(),
                sessionMinutes = minutes,
            ),
        )
    }

    private fun toBlockRoutine(routine: Routine, catalogue: Map<String, Exercise>) = FolderBlockRoutine(
        routine.name,
        routine.exercises.sortedBy { it.position }.mapNotNull { planned ->
            val row = catalogue[planned.exerciseId] ?: return@mapNotNull null
            val range = FolderReviewCalculator.resolveRepRange(planned.repRange, planned.plannedSets)
            val working = planned.plannedSets.filter { it.type != SetType.WarmUp }
            FolderBlockExercise(
                id = row.id,
                muscleGroup = row.muscleGroup,
                secondary = row.secondaryMuscles.distinct(),
                countsAsStrength = row.countsForVolume(),
                movementPattern = row.movementPattern,
                equipment = row.equipment,
                sets = working.size,
                repMin = range?.first,
                repMax = range?.second,
                rpe = planned.targetRpe,
                restSeconds = planned.restSeconds,
                plannedWeightKg = working.firstOrNull()?.weightKg,
            )
        },
    )
}
