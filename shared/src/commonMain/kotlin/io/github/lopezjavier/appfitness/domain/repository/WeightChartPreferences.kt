package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.coroutines.flow.Flow

/** The weight chart filter chosen by the user, kept on the device. */
interface WeightChartPreferences {
    /** The saved range; [WeightChartRange.Default] when nothing (or something unknown) is saved. */
    val range: Flow<WeightChartRange>

    suspend fun setRange(range: WeightChartRange)
}
