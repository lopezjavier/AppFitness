@file:Suppress("MatchingDeclarationName") // The file holds the overlay model and its helpers.

package io.github.lopezjavier.appfitness.designsystem

import kotlin.math.max
import kotlin.math.min

/** A fixed vertical [range] with the optional dashed [reference] line and the [band] already clipped to it. */
internal class OverlaySpec(
    val reference: Double?,
    val band: ClosedFloatingPointRange<Double>?,
    val range: ClosedFloatingPointRange<Double>,
)

/** The overlay of a chart, or null when it has neither a [reference] nor a [band] (or nothing to scale against). */
internal fun overlaySpec(
    reference: Double?,
    band: ClosedFloatingPointRange<Double>?,
    values: List<Double?>,
    dots: List<Double?>?,
): OverlaySpec? {
    if (reference == null && band == null) return null
    return plotRange(reference, values, dots)?.let { range ->
        OverlaySpec(reference, band?.let { clipBand(it, range) }, range)
    }
}

/** The vertical range covering every plotted value and the [reference] (with a little room), null without data. */
internal fun plotRange(
    reference: Double?,
    values: List<Double?>,
    dots: List<Double?>?,
): ClosedFloatingPointRange<Double>? {
    val all = values.filterNotNull() + dots.orEmpty().filterNotNull() + listOfNotNull(reference)
    if (all.isEmpty()) return null
    val low = all.min()
    val high = all.max()
    val pad = max((high - low) * REFERENCE_RANGE_PADDING, MIN_REFERENCE_PADDING)
    return (low - pad)..(high + pad)
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

private const val REFERENCE_RANGE_PADDING = 0.1
private const val MIN_REFERENCE_PADDING = 0.5
