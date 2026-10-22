package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes

/**
 * Builds one [HealthReading] per weight record (planning/04 §3.7). Every other record is attached to the nearest weight
 * within +-2 minutes (inclusive) that has no value of that type yet; values outside the column ranges become null and
 * a weight outside its range drops the whole row; unpaired fat or composition records are ignored.
 */
object PairHealthReadings {
    private val WINDOW = 2.minutes
    private const val DECIMAL_BASE = 10

    private class Range(val min: Double, val max: Double, val decimals: Int)

    private val WEIGHT = Range(20.0, 400.0, 2)
    private val FAT = Range(2.0, 75.0, 1)
    private val LEAN = Range(5.0, 400.0, 2)
    private val WATER = Range(1.0, 300.0, 2)
    private val BONE = Range(0.1, 20.0, 2)
    private val BMR = Range(300.0, 10000.0, 0)

    operator fun invoke(records: RawHealthRecords): List<HealthReading> {
        val weights = records.weights.mapNotNull { r -> clean(r.value, WEIGHT)?.let { r to it } }
            .sortedBy { it.first.time }
        val fat = attach(weights.map { it.first }, records.fats, FAT)
        val lean = attach(weights.map { it.first }, records.lean, LEAN)
        val water = attach(weights.map { it.first }, records.water, WATER)
        val bone = attach(weights.map { it.first }, records.bone, BONE)
        val bmr = attach(weights.map { it.first }, records.bmr, BMR)
        return weights.map { (record, weightKg) ->
            HealthReading(
                externalId = record.id,
                measuredAt = record.time,
                weightKg = weightKg,
                bodyFatPct = fat[record.id],
                leanMassKg = lean[record.id],
                waterKg = water[record.id],
                boneKg = bone[record.id],
                bmrKcal = bmr[record.id]?.roundToInt(),
            )
        }
    }

    /** Weight record id -> cleaned value; the closest (record, weight) pairs are assigned first. */
    private fun attach(weights: List<HealthRecord>, others: List<HealthRecord>, range: Range): Map<String, Double> {
        val candidates = others.mapNotNull { other -> clean(other.value, range)?.let { other to it } }
        val pairs = candidates.flatMap { (other, value) ->
            weights.mapNotNull { weight ->
                val distance = abs((other.time - weight.time).inWholeMilliseconds)
                if (distance <= WINDOW.inWholeMilliseconds) Triple(distance, weight, other to value) else null
            }
        }.sortedWith(compareBy({ it.first }, { it.second.time }, { it.third.first.time }))
        val result = mutableMapOf<String, Double>()
        val usedOthers = mutableSetOf<String>()
        for ((_, weight, other) in pairs) {
            if (weight.id in result || other.first.id in usedOthers) continue
            result[weight.id] = other.second
            usedOthers += other.first.id
        }
        return result
    }

    /** Rounds to the column scale and returns null when the result is outside the column range. */
    private fun clean(value: Double, range: Range): Double? {
        if (!value.isFinite()) return null
        var factor = 1.0
        repeat(range.decimals) { factor *= DECIMAL_BASE }
        val rounded = (value * factor).roundToLong() / factor
        return rounded.takeIf { it in range.min..range.max }
    }
}
