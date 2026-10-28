package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository

/** Used when Supabase is not configured: nothing can be read or stored. */
class UnconfiguredNotificationSettingsRepository : NotificationSettingsRepository {
    override suspend fun getAll(): Result<Map<NotificationKind, Boolean>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun setEnabled(kind: NotificationKind, enabled: Boolean): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
