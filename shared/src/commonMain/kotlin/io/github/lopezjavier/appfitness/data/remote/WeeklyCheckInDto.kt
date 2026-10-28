package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.weekly_check_ins` (user_id defaults to `auth.uid()` on insert, so it is never sent). */
@Serializable
internal data class WeeklyCheckInDto(
    @SerialName("week_start") val weekStart: LocalDate,
    val energy: Int,
    val notes: String? = null,
) {
    fun toDomain() = CheckIn(weekStart, energy, notes.orEmpty())

    companion object {
        const val COLUMNS = "week_start,energy,notes"

        /** Notes are always sent (empty when none) so an edit can clear the previous text on upsert. */
        fun of(checkIn: CheckIn) = WeeklyCheckInDto(checkIn.weekStart, checkIn.energy, checkIn.notes)
    }
}

/** Row of `select=check_in_weekday` on `profiles` and body of the `PATCH` that sets it (1 Monday .. 7 Sunday). */
@Serializable
internal data class CheckInWeekdayDto(@SerialName("check_in_weekday") val weekday: Int) {
    companion object {
        const val COLUMNS = "check_in_weekday"
    }
}

/** Row of `weekly_records`. */
@Serializable
internal data class WeekRecordRowDto(
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("record_type") val recordType: String,
    val value: Double,
    @SerialName("session_date") val sessionDate: LocalDate? = null,
) {
    /** Null for a record type this app does not know or a row without a session date. */
    fun toDomain(): WeekRecord? {
        val type = PersonalRecordType.fromKey(recordType) ?: return null
        return sessionDate?.let { WeekRecord(exerciseId, type, value, it) }
    }
}
