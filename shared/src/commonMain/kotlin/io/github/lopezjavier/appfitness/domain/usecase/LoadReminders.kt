package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Reads the last weigh-in (any source), the last circumference, the onboarding weight day and the silenced kinds,
 * and derives the pending reminders. Days are local to [timeZone]. Any failed read fails the whole call, so a
 * failure is never shown as "no reminders".
 */
class LoadReminders(
    private val measurements: BodyMeasurementRepository,
    private val circumferences: CircumferenceRepository,
    private val settings: NotificationSettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val compute: ComputeReminders,
) {
    suspend operator fun invoke(): Result<List<Reminder>> = runCatching {
        val latestWeight = measurements.getLatestWeight().getOrThrow()
        val onboarding = measurements.getOnboardingWeight().getOrThrow()
        val circumference = circumferences.getAll().getOrThrow()
        val stored = settings.getAll().getOrThrow()
        val silenced = stored.filterValues { enabled -> !enabled }.keys.toSet<NotificationKind>()
        compute(
            today = clock.today(),
            lastWeightDay = latestWeight?.measuredAt?.toLocalDateTime(timeZone)?.date,
            // Newest first by contract; max() keeps this correct if a repository does not order.
            lastCircumferenceDay = circumference.maxOfOrNull { it.measuredAt }?.toLocalDateTime(timeZone)?.date,
            firstDay = onboarding?.measuredAt?.toLocalDateTime(timeZone)?.date,
            silenced = silenced,
        )
    }
}
