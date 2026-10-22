package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.coroutines.flow.Flow

/** The weight chart filter and metric chosen by the user, kept on the device. */
interface WeightChartPreferences {
    /** The saved range; [WeightChartRange.Default] when nothing (or something unknown) is saved. */
    val range: Flow<WeightChartRange>

    suspend fun setRange(range: WeightChartRange)

    /** The saved metric; [BodyMetric.Weight] when nothing (or something unknown) is saved. */
    val metric: Flow<BodyMetric>

    suspend fun setMetric(metric: BodyMetric)
}
