package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BestPastWeightTest {
    private fun record(type: PersonalRecordType, value: Double) = PersonalRecord(type, value, null, null)

    @Test
    fun heaviestLiftRecordWins() {
        val records = listOf(
            record(PersonalRecordType.HeaviestLift, 100.0),
            record(PersonalRecordType.HeaviestLift, 110.0),
            record(PersonalRecordType.HeaviestLift, 90.0),
        )

        assertEquals(110.0, BestPastWeight(records))
    }

    @Test
    fun emptyListHasNoWeight() = assertNull(BestPastWeight(emptyList()))

    @Test
    fun ignoresNonWeightFamilies() {
        val records = listOf(
            record(PersonalRecordType.MaxVolume, 5000.0),
            record(PersonalRecordType.TotalReps, 300.0),
            record(PersonalRecordType.Best1Rm, 140.0),
            record(PersonalRecordType.HeaviestLift, 100.0),
        )

        assertEquals(100.0, BestPastWeight(records))
        assertNull(BestPastWeight(records.filter { it.type != PersonalRecordType.HeaviestLift }))
    }
}
