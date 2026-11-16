@file:Suppress("MatchingDeclarationName") // The file holds the overlay model and its helpers.

package io.github.lopezjavier.appfitness.designsystem

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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
 * The padded vertical domain (C-159, R-112): every plotted value and the [reference] plus a margin of
 * max(10 % of the range, [step]), rounded outwards to a multiple of [step] (5 kg by default). Never anchored at zero.
 * Null without data.
 */
internal fun plotRange(
    reference: Double?,
    values: List<Double?>,
    dots: List<Double?>?,
    step: Double = DEFAULT_DOMAIN_STEP,
): ClosedFloatingPointRange<Double>? {
    val all = values.filterNotNull() + dots.orEmpty().filterNotNull() + listOfNotNull(reference)
    if (all.isEmpty()) return null
    val low = all.min()
    val high = all.max()
    val pad = max((high - low) * DOMAIN_RANGE_PADDING, step)
    return (floor((low - pad) / step) * step)..(ceil((high + pad) / step) * step)
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

private const val DOMAIN_RANGE_PADDING = 0.1

/** Default rounding unit and minimum margin of the line chart domain, in the unit of the values (kg). */
internal const val DEFAULT_DOMAIN_STEP = 5.0
