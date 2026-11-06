package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderState
import io.github.lopezjavier.appfitness.domain.coach.builder.DayTypeOption
import io.github.lopezjavier.appfitness.domain.coach.builder.GroupPriority
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.Split
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** A slot of the builder: a required slot of [day] at [index], or the extra about to be added when [extra]. */
data class SlotRef(val day: Int, val index: Int, val extra: Boolean = false)

/** The first routine, in order, that does not fit even at 2 sets per slot (US-72). */
data class NoFitInfo(val day: Int, val neededMinutes: Int, val limitMinutes: Int)

/** Why "Guardar bloque" failed; nothing is saved in any case. */
enum class BuilderSaveError { Generic, PlanClosed, Offline }

/** Maps a failed block save to its [BuilderSaveError]; shared by both builders. */
internal fun Throwable.toBuilderSaveError(): BuilderSaveError = when {
    (this as? CoachException)?.failure == CoachFailure.Conflict -> BuilderSaveError.PlanClosed
    isOfflineFailure() -> BuilderSaveError.Offline
    else -> BuilderSaveError.Generic
}

internal fun Throwable.isOfflineFailure(): Boolean = Result.failure<Unit>(this).appErrorOrNull() == AppError.Network

/** "Sugerencia: +2 series" on a [GroupTargetRow]: offered, or applied (a second tap undoes it). */
enum class SuggestionState { Offered, Applied }

/** One group of the targets step: [before] is the previous block's weekly sets, [after] the next block's. */
data class GroupTargetRow(
    val group: MuscleGroup,
    val choice: GroupPriority,
    val before: Int,
    val after: Int,
    /** Why "Priorizar" gives no own gain or cannot be chosen (the UI decides what to disable); null when it can. */
    val blocked: NextBlockTargets.Blocked?,
    val suggestion: SuggestionState?,
)

/** "Bloque de entrenamiento" (US-72/US-93, "Desde cero"): steps `Splits`, `Types`, `Builder`; no AI call. */
sealed interface BlockBuilderUiState {
    data object Loading : BlockBuilderUiState

    /** The inputs could not be read; [offline] tells a network failure from any other. */
    data class Failed(val offline: Boolean) : BlockBuilderUiState

    /** Step 1: the splits for [days], recommended first. */
    data class Splits(val days: Int, val splits: List<Split>, val selectedIndex: Int) : BlockBuilderUiState

    /**
     * Step 2: one type per day. [options] holds the five types of each day (disabled ones with their reason).
     * [noFit] disables "Continuar" and is recomputed on every type change.
     * [openDay] is the day whose type sheet is open.
     */
    data class Types(
        val split: Split,
        val chosen: List<RoutineType>,
        val options: List<List<DayTypeOption>>,
        val openDay: Int?,
        val noFit: NoFitInfo?,
    ) : BlockBuilderUiState {
        val canContinue: Boolean get() = noFit == null
    }

    /**
     * First step of the next block (US-102): one row per H1 group with the user's choice and the sets it gives.
     * [noFit] disables "Continuar" while the raised targets do not fit the session length.
     */
    data class Targets(
        val rows: List<GroupTargetRow>,
        val adherencePct: Int?,
        val cut: Boolean,
        val noFit: NoFitInfo?,
    ) : BlockBuilderUiState {
        val prioritisedCount: Int get() = rows.count { it.choice == GroupPriority.Prioritise }
        val canContinue: Boolean get() = noFit == null
    }

    /**
     * Step 3: the builder. [pickerEntries] are the rows of the open slot picker (empty when [openSlot] is null);
     * [cardioOptions] the exercises the cardio row can swap to. [score] text and bar come from `state.score`.
     */
    data class Builder(
        val state: BuilderState,
        val selectedDay: Int,
        val openSlot: SlotRef?,
        val pickerEntries: List<PickerEntry>,
        val cardioOptions: List<CatalogExercise>,
        val cardioExercise: CatalogExercise?,
        /** Every catalogue exercise by id, to show the name and picture of a pick. */
        val exercises: Map<String, CatalogExercise>,
        val saving: Boolean,
        val saveError: BuilderSaveError?,
        val discardDialog: Boolean,
        /** Favourite exercise ids, for the R-56 star in the slot picker. */
        val favouriteIds: Set<String> = emptySet(),
        /** Patterns "Añadir ejercicio propio" may offer for the open slot (empty when no slot is open). */
        val validPatterns: List<MovementPattern> = emptyList(),
        /** The last [BlockBuilderEvent.CreateCustomExercise] failed; cleared on the next attempt or slot change. */
        val createFailed: Boolean = false,
        /** Main lifts that stalled in the previous block ("puedes cambiarlo"); empty outside the next block. */
        val stalledExerciseIds: Set<String> = emptySet(),
        /** The plan the block was saved under (the server's `plan_id`), set once the save succeeded. */
        val savedPlanId: String? = null,
    ) : BlockBuilderUiState {
        /** "Guardar bloque" is enabled: 100 % and no save in flight (a plan-closed error also blocks it). */
        val canSave: Boolean get() = state.canSave && !saving && saveError != BuilderSaveError.PlanClosed
    }
}

sealed interface BlockBuilderEvent {
    data class PickSplit(val index: Int) : BlockBuilderEvent

    /** Opens the type sheet of [day]; null closes it. */
    data class OpenDay(val day: Int?) : BlockBuilderEvent

    /** Applies [type] to the open day (ignored when disabled) and closes the sheet. */
    data class PickType(val type: RoutineType) : BlockBuilderEvent

    data object Continue : BlockBuilderEvent

    /** Next block: sets the choice of [group]; a fourth "Priorizar" is ignored. */
    data class SetPriority(val group: MuscleGroup, val priority: GroupPriority) : BlockBuilderEvent

    /** Next block: applies "Sugerencia: +2 series" on [group]; tapping it again undoes it. */
    data class ApplySuggestion(val group: MuscleGroup) : BlockBuilderEvent

    /** Next block: leaves the targets step for the builder with the previous picks. */
    data object ContinueFromTargets : BlockBuilderEvent

    data class SelectDay(val day: Int) : BlockBuilderEvent

    /** Opens the picker of [slot]; null closes it. */
    data class OpenSlot(val slot: SlotRef?) : BlockBuilderEvent

    /** Picks [exerciseId] into the open slot (ignored when the row is disabled or not listed). */
    data class PickExercise(val exerciseId: String) : BlockBuilderEvent

    /**
     * "Añadir ejercicio propio" from the open slot: creates the exercise ([muscleGroup] defaults to the slot's group)
     * and picks it into the slot. Ignored with a blank name or a [movementPattern] that is null or not in
     * `Builder.validPatterns`.
     */
    data class CreateCustomExercise(
        val name: String,
        val equipment: Equipment,
        val movementPattern: MovementPattern?,
        val perHand: Boolean = false,
        val muscleGroup: MuscleGroup? = null,
    ) : BlockBuilderEvent

    data class ClearSlot(val day: Int, val index: Int) : BlockBuilderEvent

    /** Opens the picker of a new extra of [day]. */
    data class AddExtra(val day: Int) : BlockBuilderEvent

    data class RemoveExtra(val day: Int, val index: Int) : BlockBuilderEvent

    data class SwapCardio(val exerciseId: String) : BlockBuilderEvent

    data object Save : BlockBuilderEvent

    data object Back : BlockBuilderEvent

    data object ConfirmDiscard : BlockBuilderEvent

    data object CancelDiscard : BlockBuilderEvent

    data object Retry : BlockBuilderEvent
}

sealed interface BlockBuilderEffect {
    /** Saved: the screen returns to the Trainer root. */
    data object Saved : BlockBuilderEffect

    /** Leave the flow (back from the first step, or discard confirmed). */
    data object Close : BlockBuilderEffect

    /** Confirm haptic (a pick, a save). */
    data object Confirm : BlockBuilderEffect

    /** Reject haptic (a save failure). */
    data object Reject : BlockBuilderEffect
}
