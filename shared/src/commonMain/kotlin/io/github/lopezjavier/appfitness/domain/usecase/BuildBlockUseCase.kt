package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderScore
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderScoreResult
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderStart
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderState
import io.github.lopezjavier.appfitness.domain.coach.builder.DayTypeOption
import io.github.lopezjavier.appfitness.domain.coach.builder.FitResult
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockDraft
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerContext
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutinePlan
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SectionPrescription
import io.github.lopezjavier.appfitness.domain.coach.builder.Slot
import io.github.lopezjavier.appfitness.domain.coach.builder.SlotEligibility
import io.github.lopezjavier.appfitness.domain.coach.builder.Split
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.coach.builder.TimeFit
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import kotlinx.datetime.LocalDate

/**
 * The block builder as the ViewModels use it (07 section 3 "Block builder", ADR-99): the rules are pure functions on
 * the device; only [loadInputs] and [save] reach the repository. Nothing is stored until [save].
 */
@Suppress("TooManyFunctions") // One method per step of the builder.
class BuildBlockUseCase(private val blocks: CoachBlockRepository, private val folders: LoadFolderBlockUseCase) {
    fun splitsFor(days: Int): List<Split> = SplitCatalog.splitsFor(days)

    fun dayOptions(
        split: Split,
        chosen: List<RoutineType>,
        dayIndex: Int,
        intention: TrainingIntention?,
    ): List<DayTypeOption> = SplitCatalog.dayOptions(split, chosen, dayIndex, intention)

    fun prescribe(
        types: List<RoutineType>,
        intention: TrainingIntention?,
        targets: Map<MuscleGroup, Int> = emptyMap(),
        hasMainLift: (MuscleGroup) -> Boolean = { true },
    ): List<RoutinePlan> = SectionPrescription.prescribe(types, intention, targets, hasMainLift)

    fun fit(plan: RoutinePlan, intention: TrainingIntention?, sessionMinutes: Int): FitResult =
        TimeFit.fit(plan, intention, sessionMinutes)

    /** Prescribes, fits and returns an empty builder, or the day that does not fit. */
    @Suppress("LongParameterList") // The preferences and inputs the block is built from.
    fun start(
        types: List<RoutineType>,
        intention: TrainingIntention?,
        sessionMinutes: Int,
        equipment: List<Equipment>,
        cardioTargetMinWeek: Int,
        inputs: BuilderInputs,
        catalogue: List<CatalogExercise>,
        targets: Map<MuscleGroup, Int> = emptyMap(),
    ): BuilderStart {
        val ctx = PickerContext(equipment.toSet(), inputs.history, emptySet())
        return BuilderState.start(
            types,
            intention,
            sessionMinutes,
            equipment,
            cardioTargetMinWeek,
            inputs.cardioDefault,
            targets,
        ) { group -> SlotEligibility.hasMainLift(group, catalogue, ctx) }
    }

    fun eligible(
        slot: Slot,
        catalogue: List<CatalogExercise>,
        ctx: PickerContext,
        usedInRoutine: Set<String>,
    ): List<PickerEntry> = SlotEligibility.picker(slot, catalogue, ctx, usedInRoutine)

    fun score(state: BuilderState): BuilderScoreResult = BuilderScore.score(state.days, state.intention)

    /** Reads a folder as an editable copy (nothing is written). */
    suspend fun loadFolder(folderId: String): Result<FolderBlockSource> = folders(folderId)

    suspend fun loadInputs(today: LocalDate): Result<BuilderInputs> = blocks.loadBuilderInputs(today)

    /** Saves a builder block as a new folder; the server re-checks the structural rules. */
    suspend fun save(
        planId: String,
        state: BuilderState,
        folderName: String? = null,
        previousPlanId: String? = null,
    ): Result<BlockSaved>? = state.toBuiltBlock()?.let { blocks.saveBuiltBlock(planId, it, folderName, previousPlanId) }

    /** Saves a block built from a folder (the copy), or null while the folder is not at 100 %. */
    @Suppress("LongParameterList") // The preferences and inputs the block is built from.
    suspend fun saveFolder(
        planId: String,
        draft: FolderBlockDraft,
        sessionMinutes: Int,
        cardioTargetMinWeek: Int,
        inputs: BuilderInputs,
        folderName: String?,
    ): Result<BlockSaved>? = draft
        .toBuiltBlock(sessionMinutes, cardioTargetMinWeek, inputs.cardioDefault, inputs.history)
        ?.let { blocks.saveBuiltBlock(planId, it, folderName) }
}
