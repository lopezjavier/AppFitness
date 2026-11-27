package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The display rule of `07` §5 "Circumferences on Peso": every site compares its latest value with the latest one at
 * least [WINDOW_DAYS] days older, and a site with a measurement margin labels a smaller change "dentro del margen de
 * medida". This is the only Kotlin copy of these figures.
 */
object CircumferenceDisplayRule {
    const val WINDOW_DAYS = 28

    private const val WAIST_MARGIN_CM = 3.0
    private const val HIP_MARGIN_CM = 2.0
    private const val ARM_MARGIN_CM = 1.5
    private const val THIGH_MARGIN_CM = 2.7

    /** The site's measurement margin in cm; null for neck, chest and calf, which show the raw change. */
    fun marginCm(site: CircumferenceSite): Double? = when (site) {
        CircumferenceSite.Waist -> WAIST_MARGIN_CM
        CircumferenceSite.Hip -> HIP_MARGIN_CM
        CircumferenceSite.Arm -> ARM_MARGIN_CM
        CircumferenceSite.Thigh -> THIGH_MARGIN_CM
        CircumferenceSite.Neck, CircumferenceSite.Chest, CircumferenceSite.Calf -> null
    }
}

/** One measurement day of a site's history: the latest value of that local day. */
data class CircumferencePoint(val date: LocalDate, val cm: Double)

/** What a site's latest value is compared with. */
sealed interface CircumferenceChange {
    /**
     * The latest value against [refCm] on [refDate], the latest point at least the window older. [deltaCm] = latest −
     * ref, rounded to tenths as shown. [marginCm] is the site's margin, null when the site has none.
     */
    data class Compared(val deltaCm: Double, val refCm: Double, val refDate: LocalDate, val marginCm: Double?) :
        CircumferenceChange {
        /** True when the shown change is below the margin ("dentro del margen de medida"); null without a margin. */
        val withinMargin: Boolean? get() = marginCm?.let { abs(deltaCm) < it }
    }

    /** No point is old enough to compare with (one measurement, or all inside the window): no change figure. */
    data object NoPair : CircumferenceChange
}

/** One site's progress: its latest value and day, its history (oldest first, one point per day) and its change. */
data class CircumferenceSiteSummary(
    val site: CircumferenceSite,
    val latestCm: Double,
    val latestDate: LocalDate,
    val history: List<CircumferencePoint>,
    val change: CircumferenceChange,
)

/**
 * The per-site summaries of [BodyCircumference] rows (`07` §5 "Circumferences on Peso"): only sites with rows, in
 * [CircumferenceSite] order. A day's point is its latest row, ordered by `measuredAt`, then `createdAt`, then `id`
 * (as [WaistHipRatio]); days are local to the given time zone.
 */
class SummarizeCircumferences {
    operator fun invoke(rows: List<BodyCircumference>, timeZone: TimeZone): List<CircumferenceSiteSummary> {
        val ordered = rows.sortedWith(compareBy({ it.measuredAt }, { it.createdAt }, { it.id }))
        return CircumferenceSite.entries.mapNotNull { site ->
            ordered.filter { it.site == site }.takeIf { it.isNotEmpty() }?.let { summarize(site, it, timeZone) }
        }
    }

    /** [rows] oldest first and not empty. */
    private fun summarize(
        site: CircumferenceSite,
        rows: List<BodyCircumference>,
        timeZone: TimeZone,
    ): CircumferenceSiteSummary {
        val history = rows
            .groupBy { it.measuredAt.toLocalDateTime(timeZone).date }
            .map { (date, dayRows) -> CircumferencePoint(date, dayRows.last().valueCm) }
        val latest = history.last()
        val cutoff = latest.date.minus(CircumferenceDisplayRule.WINDOW_DAYS, DateTimeUnit.DAY)
        val change = history.lastOrNull { it.date <= cutoff }?.let { ref ->
            CircumferenceChange.Compared(
                deltaCm = roundToTenths(latest.cm - ref.cm),
                refCm = ref.cm,
                refDate = ref.date,
                marginCm = CircumferenceDisplayRule.marginCm(site),
            )
        } ?: CircumferenceChange.NoPair
        return CircumferenceSiteSummary(site, latest.cm, latest.date, history, change)
    }

    /** Half away from zero, as the shown signed value ("−0,25" shows "−0,3"). */
    private fun roundToTenths(value: Double): Double {
        val tenths = (abs(value) * TENTHS).roundToLong()
        return (if (value < 0) -tenths else tenths) / TENTHS
    }

    private companion object {
        const val TENTHS = 10.0
    }
}
