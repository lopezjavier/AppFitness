package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlin.math.round

/** Waist-to-hip ratio from the latest waist and hip, rounded to 2 decimals; null when either is missing. */
class WaistHipRatio {
    operator fun invoke(circumferences: List<BodyCircumference>): Double? {
        val waist = latest(circumferences, CircumferenceSite.Waist)
        val hip = latest(circumferences, CircumferenceSite.Hip)
        return if (waist == null || hip == null) null else round(waist.valueCm / hip.valueCm * HUNDREDTHS) / HUNDREDTHS
    }

    private fun latest(list: List<BodyCircumference>, site: CircumferenceSite): BodyCircumference? =
        list.filter { it.site == site }.maxWithOrNull(compareBy({ it.measuredAt }, { it.id }))

    private companion object {
        const val HUNDREDTHS = 100.0
    }
}
