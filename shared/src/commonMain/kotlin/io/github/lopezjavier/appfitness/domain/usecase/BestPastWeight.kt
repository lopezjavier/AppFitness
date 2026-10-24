package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType

/** The heaviest weight ever lifted in an exercise (its "weight record"), or null when it has no history (US-33). */
object BestPastWeight {
    operator fun invoke(records: List<PersonalRecord>): Double? =
        records.filter { it.type == PersonalRecordType.HeaviestLift }.maxOfOrNull { it.value }
}
