package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Which creation reads the list: a nutrition plan or a training block. */
enum class PlanInputKind { Plan, Block }

/** One piece of data a plan or block reads; [Unknown] when the server sends a key this app version lacks. */
enum class PlanInputKey {
    Intention,
    Sex,
    Age,
    Height,
    Weight,
    ActivityLevel,
    TotalExpenditure,
    WeightTrend,
    Diary,
    Place,
    Equipment,
    Cardio,
    DaysPerWeek,
    SessionMinutes,
    Experience,

    /** The three "Para conocerte" inputs: informational, never blocking, shown as intake rows (not summary lines). */
    TrainingYears,
    KnownMaintenance,
    FreeText,
    Unknown,
    ;

    /** The T-174 missing-data field this key corresponds to, or null when it has none. */
    fun toMissingField(): MissingField? = when (this) {
        Intention -> MissingField.Intention
        Sex -> MissingField.Sex
        Age -> MissingField.Age
        Height -> MissingField.Height
        Weight -> MissingField.Weight
        TotalExpenditure -> MissingField.Expenditure
        Place -> MissingField.Place
        Unknown -> MissingField.Unknown
        else -> null
    }
}

enum class PlanInputStatus { Ok, Missing, UnsetOptional, Provisional, Stale, Unknown }

/** Where the user fixes an input by hand. */
enum class PlanFixTarget { MyTraining, PersonalData, Weight, ActivitySheet, TrainingYears, Maintenance, FreeText }

enum class PlanInputSource { Profile, MyTraining, WeighIn, CoachEstimate, Intake, Unknown }

/** The plan the new one would replace, when there is one. */
enum class CurrentPlan { Accepted, Proposed }

sealed interface PlanInputValue {
    data class Text(val text: String) : PlanInputValue

    data class Number(val number: Double) : PlanInputValue

    data class Date(val date: LocalDate) : PlanInputValue

    data class Flag(val flag: Boolean) : PlanInputValue

    /** A list of values, such as the equipment. */
    data class Items(val items: List<String>) : PlanInputValue

    data object Absent : PlanInputValue
}

data class PlanInput(
    val key: PlanInputKey,
    val value: PlanInputValue,
    val source: PlanInputSource,
    val status: PlanInputStatus,
    val blocking: Boolean,
    val fixTarget: PlanFixTarget?,
    /** Date of the last weigh-in; only the weight input has one. */
    val date: LocalDate?,
)

data class PlanInputs(val kind: PlanInputKind, val currentPlan: CurrentPlan?, val inputs: List<PlanInput>) {
    val hasBlocking: Boolean get() = inputs.any { it.blocking && it.status == PlanInputStatus.Missing }

    /** The fields the server would report in a `coach_missing_data` answer for this list. */
    val missingFields: Set<MissingField>
        get() = inputs.filter { it.blocking && it.status == PlanInputStatus.Missing }
            .mapNotNull { it.key.toMissingField() }.toSet()
}
