@file:Suppress("MatchingDeclarationName") // The file holds the overlay model and its helpers.

package io.github.lopezjavier.appfitness.designsystem

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A fixed vertical [range] with the optional dashed [reference] line and the [band] already clipped to it. */
internal class OverlaySpec(
    val reference: Double?,
    val band: ClosedFloatingPointRange<Double>?,
    val range: ClosedFloatingPointRange<Double>,
)

/**
 * The padded domain of a chart plus its optional [reference] and [band]; null when there is nothing to scale
 * against.
 */
internal fun overlaySpec(
    reference: Double?,
    band: ClosedFloatingPointRange<Double>?,
    values: List<Double?>,
    dots: List<Double?>?,
    step: Double = DEFAULT_DOMAIN_STEP,
): OverlaySpec? = plotRange(reference, values, dots, step)?.let { range ->
    OverlaySpec(reference, band?.let { clipBand(it, range) }, range)
}

/**
 * The vertical domain fitted to the data (C-159, R-112): every plotted value and the [reference] plus a margin of 12 %
 * of their spread, never anchored at zero. A flat series is given a minimum spread of 40 % of [step] (2 kg by default),
 * so a steady weight still shows its small moves instead of a flat line. The domain is three equal intervals of a
 * "nice" size (see [niceUnit]) so the four axis labels are round numbers, and it contains all the
 * data. [step] only sets that minimum spread (kilos by default, hours for sleep). Null without data.
 */
internal fun plotRange(
    reference: Double?,
    values: List<Double?>,
    dots: List<Double?>?,
    step: Double = DEFAULT_DOMAIN_STEP,
): ClosedFloatingPointRange<Double>? {
    val all = values.filterNotNull() + dots.orEmpty().filterNotNull() + listOfNotNull(reference)
    if (all.isEmpty()) return null
    val dataLow = all.min()
    val dataHigh = all.max()
    val spread = max(dataHigh - dataLow, step * MIN_SPREAD_OF_STEP)
    val centre = (dataLow + dataHigh) / 2
    val low = min(dataLow, centre - spread / 2) - spread * DOMAIN_RANGE_PADDING
    val high = max(dataHigh, centre + spread / 2) + spread * DOMAIN_RANGE_PADDING
    var unit = niceUnit((high - low) / DOMAIN_INTERVALS)
    while (true) {
        val start = floor(low / unit) * unit
        val end = start + unit * DOMAIN_INTERVALS
        if (end >= high - unit * EPSILON || unit > MAX_UNIT_SEARCH * step) return start..end
        unit = niceUnit(unit * NEXT_UNIT_FACTOR)
    }
}

/** The smallest round size (1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8 times a power of ten) that is at least [raw]. */
private fun niceUnit(raw: Double): Double {
    val magnitude = 10.0.pow(floor(log10(raw)))
    return NICE_MANTISSAS.map { it * magnitude }.first { it >= raw * (1 - EPSILON) }
}

/** [band] limited to [range]; null when they do not overlap. The band never widens the range. */
internal fun clipBand(
    band: ClosedFloatingPointRange<Double>,
    range: ClosedFloatingPointRange<Double>,
): ClosedFloatingPointRange<Double>? {
    val low = max(band.start, range.start)
    val high = min(band.endInclusive, range.endInclusive)
    return if (low < high) low..high else null
}

private const val DOMAIN_RANGE_PADDING = 0.12
private const val DOMAIN_INTERVALS = 3
private val NICE_MANTISSAS = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
private const val MIN_SPREAD_OF_STEP = 0.4
private const val EPSILON = 1e-9
private const val NEXT_UNIT_FACTOR = 1.01
private const val MAX_UNIT_SEARCH = 1000.0

/** Default rounding unit and minimum margin of the line chart domain, in the unit of the values (kg). */
internal const val DEFAULT_DOMAIN_STEP = 5.0
