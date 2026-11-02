package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** The user's stored intake ("Para conocerte", US-81). Every field is optional and provisional. */
data class CoachIntake(
    val trainingYears: Double? = null,
    val knownMaintenanceKcal: Int? = null,
    val freeText: String? = null,
    /** True while the one-time "your data takes over" card has not been shown yet. */
    val maintenanceHandoverPending: Boolean = false,
)

/**
 * One edited intake value. Each row saves only its own value: the other stored values stay untouched, and a null
 * value clears the field.
 */
sealed interface IntakeUpdate {
    data class TrainingYears(val years: Double?) : IntakeUpdate

    data class KnownMaintenance(val kcal: Int?) : IntakeUpdate

    data class FreeText(val text: String?) : IntakeUpdate
}

/** Where a free-text line comes from. [Unknown] keeps a new server kind from crashing the app. */
enum class FreeTextKind { Intake, CheckIn, Discomfort, Review, Unknown }

enum class FreeTextStatus { Sent, Excluded, OverLimit }

/** One line of the block the coach reads; [sourceId] identifies it for exclusion. */
data class FreeTextLine(
    val kind: FreeTextKind,
    val sourceId: String,
    val date: LocalDate,
    val text: String,
    val status: FreeTextStatus,
)
