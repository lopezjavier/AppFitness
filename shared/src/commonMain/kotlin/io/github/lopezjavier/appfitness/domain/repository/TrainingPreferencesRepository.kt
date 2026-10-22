package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences

/** The signed-in user's `training_preferences` row (written through `save_preferences`). */
interface TrainingPreferencesRepository {
    /** The saved preferences; a missing row yields the defaults. */
    suspend fun get(): Result<TrainingPreferences>
}

/** Whether warm-up sets count; a failed read falls back to the default (off) and never blocks the caller. */
suspend fun TrainingPreferencesRepository.warmUpIncludedOrDefault(): Boolean =
    get().getOrNull()?.warmUpIncluded ?: false
