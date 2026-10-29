package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences

/** The signed-in user's `training_preferences` row (written through `save_preferences`). */
interface TrainingPreferencesRepository {
    /** The saved preferences; a missing row yields the defaults. */
    suspend fun get(): Result<TrainingPreferences>

    /** Writes only the "Mi entrenamiento" columns (nulls clear the optional ones); the rest of the row is kept. */
    suspend fun saveMyTraining(training: MyTraining): Result<Unit>
}

/** Whether warm-up sets count; a failed read falls back to the default (off) and never blocks the caller. */
suspend fun TrainingPreferencesRepository.warmUpIncludedOrDefault(): Boolean =
    get().getOrNull()?.warmUpIncluded ?: false

/** Whether a finished routine session updates its routine without asking; a failed read falls back to off (ask). */
suspend fun TrainingPreferencesRepository.autoUpdateRoutineOrDefault(): Boolean =
    get().getOrNull()?.autoUpdateRoutine ?: false
