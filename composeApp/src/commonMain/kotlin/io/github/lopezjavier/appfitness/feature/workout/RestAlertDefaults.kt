package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.domain.port.NotificationPermission
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import io.github.lopezjavier.appfitness.domain.port.RestAlertScheduler
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * Inert defaults for the rest-alert collaborators of [LiveSessionViewModel], for callers that do not exercise the alert
 * outside the app (most view model tests). The app always injects the real ones (see `UiModule`).
 */
internal object NoRestAlertScheduler : RestAlertScheduler {
    override fun schedule(endsAt: Instant) = Unit

    override fun cancel() = Unit
}

internal object NotificationsGranted : NotificationPermission {
    override fun isGranted(): Boolean = true

    override suspend fun request(): Boolean = true
}

/** No discomforts stored and none can be logged: the default of [LiveSessionViewModel] for callers ignoring US-67. */
internal object NoDiscomforts : DiscomfortRepository {
    override suspend fun log(exerciseId: String, note: String?, severe: Boolean): Result<Discomfort> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>> = Result.success(emptyList())
}

internal object ExplanationAlreadyShown : RestAlertPreferences {
    override val sound: Flow<RestSound> = flowOf(RestSound.Default)

    override suspend fun setSound(sound: RestSound) = Unit

    override suspend fun explanationShown(): Boolean = true

    override suspend fun markExplanationShown() = Unit
}
