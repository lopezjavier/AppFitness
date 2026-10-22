package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthRecord
import io.github.lopezjavier.appfitness.domain.model.RawHealthRecords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val T0 = Instant.parse("2026-10-20T07:31:00Z")

private fun rec(id: String, at: Instant, value: Double) = HealthRecord(id, at, value)

class PairHealthReadingsTest {
    @Test
    fun weightAndFatOfTheSameInstant_becomeOneReading() {
        val result = PairHealthReadings(
            RawHealthRecords(weights = listOf(rec("w1", T0, 73.9)), fats = listOf(rec("f1", T0, 15.1))),
        )

        assertEquals(listOf(HealthReading("w1", T0, 73.9, bodyFatPct = 15.1)), result)
    }

    @Test
    fun fatTwoMinutesAwayOnEitherSide_isPaired_butNotAtTwoMinutesOneSecond() {
        val weights = listOf(rec("w1", T0, 70.0))

        val after = PairHealthReadings(RawHealthRecords(weights, fats = listOf(rec("f", T0 + 120.seconds, 15.0))))
        val before = PairHealthReadings(RawHealthRecords(weights, fats = listOf(rec("f", T0 - 120.seconds, 16.0))))
        val tooLate = PairHealthReadings(RawHealthRecords(weights, fats = listOf(rec("f", T0 + 121.seconds, 15.0))))
        val tooEarly = PairHealthReadings(RawHealthRecords(weights, fats = listOf(rec("f", T0 - 121.seconds, 15.0))))

        assertEquals(15.0, after.single().bodyFatPct)
        assertEquals(16.0, before.single().bodyFatPct)
        assertEquals(null, tooLate.single().bodyFatPct)
        assertEquals(null, tooEarly.single().bodyFatPct)
    }

    @Test
    fun aSixMetricWeighIn_isOneRowWithAllSixValues() {
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("w1", T0, 73.9)),
                fats = listOf(rec("f1", T0, 15.1)),
                lean = listOf(rec("l1", T0, 62.4)),
                water = listOf(rec("a1", T0, 45.0)),
                bone = listOf(rec("b1", T0, 3.1)),
                bmr = listOf(rec("m1", T0, 1720.0)),
            ),
        )

        assertEquals(listOf(HealthReading("w1", T0, 73.9, 15.1, 62.4, 45.0, 3.1, 1720)), result)
    }

    @Test
    fun weightOnly_givesAReadingWithoutOptionalValues() {
        val result = PairHealthReadings(RawHealthRecords(weights = listOf(rec("w1", T0, 80.0))))

        assertEquals(listOf(HealthReading("w1", T0, 80.0)), result)
    }

    @Test
    fun fatWithoutWeight_isIgnored() {
        val result = PairHealthReadings(RawHealthRecords(fats = listOf(rec("f1", T0, 15.0))))

        assertTrue(result.isEmpty())
    }

    @Test
    fun outOfRangeValues_areDroppedToNull_andAnOutOfRangeWeightDropsTheRow() {
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("ok", T0, 70.0), rec("bad", T0 + 600.seconds, 19.9)),
                fats = listOf(rec("f", T0, 1.9)),
                lean = listOf(rec("l", T0, 4.9)),
                water = listOf(rec("a", T0, 300.1)),
                bone = listOf(rec("b", T0, 20.1)),
                bmr = listOf(rec("m", T0, 299.0)),
            ),
        )

        assertEquals(listOf(HealthReading("ok", T0, 70.0)), result)
    }

    @Test
    fun valuesAtTheTableBounds_areKept() {
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("w", T0, 20.0)),
                fats = listOf(rec("f", T0, 75.0)),
                lean = listOf(rec("l", T0, 5.0)),
                water = listOf(rec("a", T0, 1.0)),
                bone = listOf(rec("b", T0, 0.1)),
                bmr = listOf(rec("m", T0, 10000.0)),
            ),
        )

        assertEquals(HealthReading("w", T0, 20.0, 75.0, 5.0, 1.0, 0.1, 10000), result.single())
    }

    @Test
    fun valuesAreRoundedToTheColumnScale() {
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("w", T0, 73.9449)),
                fats = listOf(rec("f", T0, 15.06)),
                lean = listOf(rec("l", T0, 62.4049)),
                bmr = listOf(rec("m", T0, 1720.6)),
            ),
        )

        assertEquals(HealthReading("w", T0, 73.94, 15.1, 62.4, null, null, 1721), result.single())
    }

    @Test
    fun twoWeighInsFiveMinutesApart_eachTakeTheirOwnFat() {
        val later = T0 + 300.seconds
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("w1", T0, 70.0), rec("w2", later, 71.0)),
                fats = listOf(rec("f2", later, 20.0), rec("f1", T0, 10.0)),
            ),
        )

        assertEquals(listOf("w1" to 10.0, "w2" to 20.0), result.map { it.externalId to it.bodyFatPct })
    }

    @Test
    fun aFatBetweenTwoWeights_goesToTheNearestOne() {
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("w1", T0, 70.0), rec("w2", T0 + 180.seconds, 71.0)),
                fats = listOf(rec("f", T0 + 100.seconds, 12.0)),
            ),
        )

        assertEquals(listOf(null, 12.0), result.map { it.bodyFatPct })
    }

    @Test
    fun aSecondFatForTheSameWeight_isIgnored() {
        val result = PairHealthReadings(
            RawHealthRecords(
                weights = listOf(rec("w1", T0, 70.0)),
                fats = listOf(rec("f1", T0, 12.0), rec("f2", T0 + 30.seconds, 13.0)),
            ),
        )

        assertEquals(12.0, result.single().bodyFatPct)
    }

    @Test
    fun externalIdIsTheWeightRecordId_andRowsAreOrderedByTime() {
        val result = PairHealthReadings(
            RawHealthRecords(weights = listOf(rec("late", T0 + 3600.seconds, 70.0), rec("early", T0, 71.0))),
        )

        assertEquals(listOf("early", "late"), result.map { it.externalId })
    }
}
