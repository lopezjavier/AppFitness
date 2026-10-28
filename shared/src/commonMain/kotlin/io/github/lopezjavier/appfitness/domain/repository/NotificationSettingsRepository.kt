package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.NotificationKind

/** The signed-in user's reminder switches. A kind without a row is enabled. */
interface NotificationSettingsRepository {
    /** The stored switches; kinds the user never toggled are absent (treat as enabled). */
    suspend fun getAll(): Result<Map<NotificationKind, Boolean>>

    /** Stores the switch of [kind] (upsert on user and kind). */
    suspend fun setEnabled(kind: NotificationKind, enabled: Boolean): Result<Unit>
}
