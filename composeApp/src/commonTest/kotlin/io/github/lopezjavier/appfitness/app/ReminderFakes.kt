package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import io.github.lopezjavier.appfitness.domain.repository.WeeklyCheckInRepository
import io.github.lopezjavier.appfitness.domain.usecase.ComputeReminders
import io.github.lopezjavier.appfitness.domain.usecase.LoadReminders
import io.github.lopezjavier.appfitness.feature.weight.FakeCircumferenceRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

internal class FakeNotificationSettingsRepository(vararg initial: Pair<NotificationKind, Boolean>) :
    NotificationSettingsRepository {
    val values = mutableMapOf(*initial)
    var failReads = false
    var failWrites = false

    /** Only these kinds fail to write (a partial failure). */
    val failingKinds = mutableSetOf<NotificationKind>()

    /** Every successful write, in order. */
    val writes = mutableListOf<Pair<NotificationKind, Boolean>>()

    override suspend fun getAll(): Result<Map<NotificationKind, Boolean>> =
        if (failReads) Result.failure(AppException(AppError.Network)) else Result.success(values.toMap())

    override suspend fun setEnabled(kind: NotificationKind, enabled: Boolean): Result<Unit> {
        if (failWrites || kind in failingKinds) return Result.failure(AppException(AppError.Network))
        values[kind] = enabled
        writes += kind to enabled
        return Result.success(Unit)
    }
}

/** In-memory [WeeklyCheckInRepository] keyed by week start; failures are switchable. */
internal class FakeWeeklyCheckInRepository : WeeklyCheckInRepository {
    val rows = mutableMapOf<LocalDate, CheckIn>()
    val saves = mutableListOf<CheckIn>()
    var failReads = false
    var failWrites = false
    var saveError: AppError = AppError.Network

    override suspend fun get(weekStart: LocalDate): Result<CheckIn?> =
        if (failReads) Result.failure(AppException(AppError.Network)) else Result.success(rows[weekStart])

    override suspend fun save(checkIn: CheckIn): Result<Unit> {
        if (failWrites) return Result.failure(AppException(saveError))
        saves += checkIn
        rows[checkIn.weekStart] = checkIn
        return Result.success(Unit)
    }
}

internal class ReminderClock(var day: LocalDate, private val zone: TimeZone = UTC) : Clock {
    override fun today(): LocalDate = day

    override fun now(): Instant = day.atStartOfDayIn(zone)

    companion object {
        val UTC: TimeZone = TimeZone.UTC
    }
}

/** A coordinator over empty data, for tests that only need the constructor argument. */
internal fun idleReminderCoordinator(): ReminderCoordinator =
    ReminderCoordinator(ReminderEnv(LocalDate(2026, 10, 28)).load, CoroutineScope(SupervisorJob()))

/** The repositories and clock behind a [LoadReminders]; tests mutate them and refresh. */
internal class ReminderEnv(today: LocalDate, val timeZone: TimeZone = TimeZone.UTC) {
    val weight = FakeWeightRepository()
    val circumferences = FakeCircumferenceRepository()
    val settings = FakeNotificationSettingsRepository()
    val profile = FakeProfileRepository(Result.success(ProfileSummary(null, null, onboardingCompleted = true)))
    val checkIns = FakeWeeklyCheckInRepository()
    val clock = ReminderClock(today)
    val load = LoadReminders(weight, circumferences, settings, profile, checkIns, clock, timeZone, ComputeReminders())
}
