package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BlockEndNotice
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Reads the last weigh-in and the earliest weigh-in (both of any source), the last circumference and the silenced
 * kinds, and derives the pending reminders; the earliest weigh-in anchors the circumference reminder while no
 * circumference was ever taken. It adds the unseen weekly summary notice when a [summaries] repository is given and
 * the block-end notice when [blockReview] is given and the open review is not yet seen (04 section 6.9). Days are
 * local to [timeZone]. Any failed read fails the whole call, so a failure is never shown as "no reminders"; the
 * exception is the block-end read, derived and also shown by the Trainer: a failure hides only that item.
 */
@Suppress("LongParameterList") // One port per read.
class LoadReminders(
    private val measurements: BodyMeasurementRepository,
    private val circumferences: CircumferenceRepository,
    private val settings: NotificationSettingsRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val compute: ComputeReminders,
    private val summaries: CoachSummaryRepository? = null,
    private val blockReview: LoadBlockReviewUseCase? = null,
) {
    suspend operator fun invoke(): Result<List<Reminder>> = runCatching {
        val today = clock.today()
        val latestWeight = measurements.getLatestWeight().getOrThrow()
        val earliestWeight = measurements.getEarliestWeight().getOrThrow()
        val circumference = circumferences.getAll().getOrThrow()
        val stored = settings.getAll().getOrThrow()
        val silenced = stored.filterValues { enabled -> !enabled }.keys.toSet<NotificationKind>()
        val notice = summaries?.takeIf { NotificationKind.WeekSummary !in silenced }
            ?.getLatest(today)?.getOrThrow()?.toNotice()
        val blockEnd = blockReview?.takeIf { NotificationKind.BlockEnd !in silenced }
            ?.invoke(today)?.getOrNull()?.takeIf { !it.seen }?.let { BlockEndNotice(it.endedOn) }
        compute(
            today = today,
            lastWeightDay = latestWeight?.measuredAt?.toLocalDateTime(timeZone)?.date,
            // Newest first by contract; max() keeps this correct if a repository does not order.
            lastCircumferenceDay = circumference.maxOfOrNull { it.measuredAt }?.toLocalDateTime(timeZone)?.date,
            firstWeightDay = earliestWeight?.measuredAt?.toLocalDateTime(timeZone)?.date,
            silenced = silenced,
            weekSummary = notice,
            blockEnd = blockEnd,
        )
    }
}
