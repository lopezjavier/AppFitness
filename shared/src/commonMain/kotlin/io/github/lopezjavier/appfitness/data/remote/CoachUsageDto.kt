package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A `coach_usage` row. */
@Serializable
internal data class CoachUsageDto(
    val month: LocalDate,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cost_eur") val costEur: Double = 0.0,
) {
    fun toDomain() = CoachUsage(month, inputTokens, outputTokens, costEur)
}
