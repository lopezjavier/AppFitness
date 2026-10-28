package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ReminderSnapshot
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.WeeklyCheckInRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Reads the last weigh-in (any source), the last circumference, the onboarding weight day, the check-in weekday and
 * the stored check-in of the open window, plus the silenced kinds, and derives the pending reminders and the check-in
 * window. Days are local to [timeZone]. Any failed read fails the whole call, so a failure is never shown as
 * "no reminders".
 */
@Suppress("LongParameterList") // One port per read.
class LoadReminders(
    private val measurements: BodyMeasurementRepository,
    private val circumferences: CircumferenceRepository,
    private val settings: NotificationSettingsRepository,
    private val profile: ProfileRepository,
    private val checkIns: WeeklyCheckInRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val compute: ComputeReminders,
    private val resolveCheckInDay: ResolveCheckInDay = ResolveCheckInDay(),
) {
    suspend operator fun invoke(): Result<ReminderSnapshot> = runCatching {
        val today = clock.today()
        val latestWeight = measurements.getLatestWeight().getOrThrow()
        val onboarding = measurements.getOnboardingWeight().getOrThrow()
        val circumference = circumferences.getAll().getOrThrow()
        val stored = settings.getAll().getOrThrow()
        val weekday = profile.getCheckInWeekday().getOrThrow()
        val silenced = stored.filterValues { enabled -> !enabled }.keys.toSet<NotificationKind>()
        val firstDay = onboarding?.measuredAt?.toLocalDateTime(timeZone)?.date
        val window = resolveCheckInDay(today, weekday, firstDay)
            ?.takeIf { NotificationKind.WeeklyCheckIn !in silenced }
            ?.let { day -> day.copy(answer = checkIns.get(day.weekStart).getOrThrow()) }
        val reminders = compute(
            today = today,
            lastWeightDay = latestWeight?.measuredAt?.toLocalDateTime(timeZone)?.date,
            // Newest first by contract; max() keeps this correct if a repository does not order.
            lastCircumferenceDay = circumference.maxOfOrNull { it.measuredAt }?.toLocalDateTime(timeZone)?.date,
            firstDay = firstDay,
            silenced = silenced,
            checkIn = window,
        )
        ReminderSnapshot(reminders, window)
    }
}
