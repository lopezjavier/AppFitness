package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.port.NotificationPermission
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import io.github.lopezjavier.appfitness.domain.port.RestAlertScheduler
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import io.github.lopezjavier.appfitness.domain.repository.ProgressionRepository
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
    override suspend fun log(exerciseId: String, level: DiscomfortLevel, note: String?): Result<Discomfort> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>> = Result.success(emptyList())
}

/** No suggestions: the default of [LiveSessionViewModel] for callers ignoring US-70. */
internal object NoProgression : ProgressionRepository {
    override suspend fun suggestionsFor(sessionId: String): Result<List<ProgressionSuggestion>> =
        Result.success(emptyList())
}

internal object ExplanationAlreadyShown : RestAlertPreferences {
    override suspend fun explanationShown(): Boolean = true

    override suspend fun markExplanationShown() = Unit
}
