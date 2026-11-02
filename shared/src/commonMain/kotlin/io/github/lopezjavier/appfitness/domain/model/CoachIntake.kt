package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** What the user says about their training situation ("Para conocerte", US-81). */
enum class Situation { FirstTime, Continuous, ReturningBreak, ReturningInjury }

enum class InjuredArea { Shoulder, Elbow, Wrist, LowerBack, Hip, Knee, Ankle, Other }

/** A best lift from before a break; context only, it never sets a load. */
data class PreBreakBest(val exerciseId: String, val weightKg: Double, val reps: Int)

/** The user's stored intake. Every field is optional and provisional. */
data class CoachIntake(
    val trainingYears: Double? = null,
    val situation: Situation? = null,
    val injuredArea: InjuredArea? = null,
    val preBreakBests: List<PreBreakBest> = emptyList(),
    val knownMaintenanceKcal: Int? = null,
    val knownCutKcal: Int? = null,
    val freeText: String? = null,
    /** True while the one-time "your data takes over" card has not been shown yet; null when unknown. */
    val maintenanceHandoverPending: Boolean? = null,
)

/** The fields the extraction found in the free text; null (or empty bests) means "not mentioned". */
data class CoachIntakeDraft(
    val trainingYears: Double? = null,
    val situation: Situation? = null,
    val injuredArea: InjuredArea? = null,
    val preBreakBests: List<PreBreakBest> = emptyList(),
    val knownMaintenanceKcal: Int? = null,
    val knownCutKcal: Int? = null,
)

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
