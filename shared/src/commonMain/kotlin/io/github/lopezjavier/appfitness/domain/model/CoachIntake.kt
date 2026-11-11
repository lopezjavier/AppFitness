package io.github.lopezjavier.appfitness.domain.model

/** The user's stored intake ("Para conocerte", US-81). Every field is optional and provisional. */
data class CoachIntake(
    val trainingYears: Double? = null,
    val knownMaintenanceKcal: Int? = null,
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
}
