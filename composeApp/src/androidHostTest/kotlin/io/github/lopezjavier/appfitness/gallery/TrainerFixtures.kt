package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderStart
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderState
import io.github.lopezjavier.appfitness.domain.coach.builder.DayTypeOption
import io.github.lopezjavier.appfitness.domain.coach.builder.DaysCheck
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockDraft
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockExercise
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockRoutine
import io.github.lopezjavier.appfitness.domain.coach.builder.GroupPriority
import io.github.lopezjavier.appfitness.domain.coach.builder.Pick
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockLiftWeek
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockTraining
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.ProteinDistribution
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.StepsBasis
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryNutrition
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryWeight
import io.github.lopezjavier.appfitness.feature.trainer.AlternativesState
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartMode
import io.github.lopezjavier.appfitness.feature.trainer.BeforeStartUiState
import io.github.lopezjavier.appfitness.feature.trainer.BlockBuilderUiState
import io.github.lopezjavier.appfitness.feature.trainer.BlockPreviewUiState
import io.github.lopezjavier.appfitness.feature.trainer.BlockSummary
import io.github.lopezjavier.appfitness.feature.trainer.BuilderSaveError
import io.github.lopezjavier.appfitness.feature.trainer.CardioEdit
import io.github.lopezjavier.appfitness.feature.trainer.FolderBlockUiState
import io.github.lopezjavier.appfitness.feature.trainer.FolderPickerTarget
import io.github.lopezjavier.appfitness.feature.trainer.GroupTargetRow
import io.github.lopezjavier.appfitness.feature.trainer.NoFitInfo
import io.github.lopezjavier.appfitness.feature.trainer.PlanFailureKind
import io.github.lopezjavier.appfitness.feature.trainer.PlanInputRow
import io.github.lopezjavier.appfitness.feature.trainer.PlanProposalUiState
import io.github.lopezjavier.appfitness.feature.trainer.PlanSummary
import io.github.lopezjavier.appfitness.feature.trainer.SlotRef
import io.github.lopezjavier.appfitness.feature.trainer.StartChoiceStep
import io.github.lopezjavier.appfitness.feature.trainer.SuggestionState
import io.github.lopezjavier.appfitness.feature.trainer.SwapCandidateUi
import io.github.lopezjavier.appfitness.feature.trainer.SwapPickerUi
import io.github.lopezjavier.appfitness.feature.trainer.TrainerUiState
import io.github.lopezjavier.appfitness.feature.trainer.YourDataUiState
import io.github.lopezjavier.appfitness.feature.trainer.toRow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Fixed Trainer data for the gallery: a fat-loss plan from 19 Oct 2026, Spanish exercise names, no personal data.
 * The shapes mirror the domain models the screens read; nothing here calls the core.
 */
@Suppress("LargeClass", "TooManyFunctions") // A fixture with one builder per Trainer state.
internal object TrainerFixtures {
    val planStart = LocalDate(2026, 10, 19)
    private val reviewEnd = LocalDate(2026, 10, 18)

    val activity =
        PlanActivity(cardioMinWeek = 150, cardioEdited = false, stepsDay = 7000, stepsBasis = StepsBasis.Floor)

    /** A fat-loss plan; [limitedBy] Floor reads as the calorie floor note of planning/04 section 8. */
    fun plan(
        status: PlanStatus = PlanStatus.Accepted,
        limitedBy: PlanLimit? = null,
        phase: Phase = Phase.Cut,
        withActivity: Boolean = true,
    ): CoachPlan = CoachPlan(
        id = PLAN_ID,
        blockStart = planStart,
        blockWeeks = 4,
        intention = "fat_loss",
        status = status,
        nutrition = PlanNutrition(
            targetKcal = if (limitedBy == PlanLimit.Floor) 1200 else 2000,
            floorKcal = 1200,
            limitedBy = limitedBy,
            expenditureKcal = 2400.0,
            ratePctWeek = 0.5,
            rateKgWeek = -0.375,
            strategy = GoalStrategy.Deficit,
            proteinG = 150,
            carbsG = 200,
            fatG = 60,
            proteinPct = 30,
            carbsPct = 40,
            fatPct = 30,
            distribution = ProteinDistribution(perMealG = 30, minMeals = 4, gPerKg = 0.4),
            editedByUser = false,
            activity = if (withActivity) activity else null,
            phase = phase,
            phaseReason = PhaseReason.Intention,
            phaseOptions = PhaseOptions(),
            dietBreak = null,
        ),
        outcomes = listOf(
            PlanOutcome(OutcomeKind.Weight, "weight", 72.5, "Bajar de forma gradual.", LocalDate(2026, 11, 16)),
        ),
        acceptedAt = null,
        fromGoalSwitch = false,
    )

    /** A calorie proposal for the accepted plan: from 2000 to 2100 kcal. */
    val calorieProposal = CalorieProposal(
        id = "change-1",
        planId = PLAN_ID,
        fromKcal = 2000,
        toKcal = 2100,
        changePct = 5.0,
        reasonCode = "rate_outside_interval",
        slopeKgWeek = -0.1,
        intervalLow = -0.2,
        intervalHigh = 0.0,
        rateKgWeek = -0.5,
    )

    /** One input row of the plan inputs; [status] Provisional with [blocksAccept] is the "Falta confirmar" case. */
    fun input(
        key: PlanInputKey,
        status: PlanInputStatus = PlanInputStatus.Ok,
        value: PlanInputValue = PlanInputValue.Text("x"),
        source: PlanInputSource = PlanInputSource.Profile,
        fixTarget: PlanFixTarget? = null,
        blocksAccept: Boolean = false,
        blocking: Boolean = false,
    ): PlanInput = PlanInput(key, value, source, status, blocking, fixTarget, null, blocksAccept)

    /** "Tus datos" rows with missing values: one blocking (a flag with its fix) and one optional (a note). */
    fun missingRows(): List<PlanInputRow> = listOf(
        input(
            PlanInputKey.Weight,
            status = PlanInputStatus.Missing,
            fixTarget = PlanFixTarget.Weight,
            blocking = true,
        ),
        input(PlanInputKey.Sex, status = PlanInputStatus.Missing, fixTarget = PlanFixTarget.PersonalData),
        input(PlanInputKey.Age, value = PlanInputValue.Number(31.0), fixTarget = PlanFixTarget.PersonalData),
    ).map { it.toRow() }

    /** A nutrition section of a week: complete (gate on, five days) or not (gate off, nothing averaged). */
    fun nutrition(complete: Boolean): WeekSummaryNutrition = WeekSummaryNutrition(
        diagnosis = null,
        completeDays = if (complete) 5 else 2,
        gateOk = complete,
        avgKcal = if (complete) 2310 else null,
        avgProteinG = if (complete) 148.5 else null,
        targetKcal = if (complete) 2400 else null,
        targetProteinG = if (complete) 150 else null,
        proteinGPerKg = if (complete) 1.8 else null,
        decisions = emptyList(),
    )

    /** Plan inputs whose values are all set (the "Antes de empezar" complete case). */
    fun completeInputs(kind: PlanInputKind = PlanInputKind.Plan) = PlanInputs(
        kind,
        null,
        listOf(
            input(
                PlanInputKey.Intention,
                value = PlanInputValue.Text("fat_loss"),
                source = PlanInputSource.MyTraining,
                fixTarget = PlanFixTarget.MyTraining,
            ),
            input(PlanInputKey.Sex, value = PlanInputValue.Text("male"), fixTarget = PlanFixTarget.PersonalData),
            input(PlanInputKey.Age, value = PlanInputValue.Number(31.0), fixTarget = PlanFixTarget.PersonalData),
            input(
                PlanInputKey.Height,
                value = PlanInputValue.Number(178.0),
                fixTarget = PlanFixTarget.PersonalData,
            ),
            input(
                PlanInputKey.ActivityLevel,
                value = PlanInputValue.Text("light"),
                fixTarget = PlanFixTarget.ActivitySheet,
            ),
        ),
    )

    /** Inputs with a provisional activity level that blocks "Aceptar" until it is confirmed (R-59). */
    fun unconfirmedInputs(): PlanInputs = PlanInputs(
        PlanInputKind.Plan,
        null,
        listOf(
            input(
                PlanInputKey.ActivityLevel,
                status = PlanInputStatus.Provisional,
                fixTarget = PlanFixTarget.ActivitySheet,
                blocksAccept = true,
            ),
        ),
    )

    /** The unconfirmed rows of the proposal screen. */
    fun unconfirmedRows(): List<PlanInputRow> = listOf(
        input(
            PlanInputKey.ActivityLevel,
            status = PlanInputStatus.Provisional,
            fixTarget = PlanFixTarget.ActivitySheet,
            blocksAccept = true,
        ),
    ).map { it.toRow() }

    /** A saved training block of [weeks] weeks, [sessionsPerWeek] workouts each, as "Tu bloque" reads it. */
    fun block(saved: Boolean = true, weeks: Int = 4, sessionsPerWeek: Int = 3): CoachBlock = CoachBlock(
        planId = PLAN_ID,
        training = blockTraining(sessionsPerWeek),
        sessions = (1..weeks).flatMap { week ->
            (0 until sessionsPerWeek).map { index ->
                BlockSession(
                    week = week,
                    sessionIndex = index,
                    name = SESSION_NAMES[index % SESSION_NAMES.size],
                    focus = listOf("chest"),
                    exercises = listOf(blockExercise("bench"), blockExercise("row")),
                    routineId = if (saved) "routine-$index" else null,
                )
            }
        },
        saved = saved,
    )

    private fun blockTraining(days: Int) = BlockTraining(
        daysPerWeek = days,
        daysChosen = true,
        sessionMinutes = 60,
        repScheme = "hypertrophy",
        blockWeeks = 4,
        equipment = listOf("barbell", "dumbbell"),
        weeklySets = mapOf("chest" to 10, "back" to 12),
        sessionsPerMuscle = mapOf("chest" to 2, "back" to 2),
    )

    private fun blockExercise(id: String) = BlockExercise(
        exerciseId = id,
        exerciseKey = "key-$id",
        role = "main",
        muscleGroup = "chest",
        sets = 3,
        repMin = 8,
        repMax = 12,
        restSeconds = 90,
        reason = "Trabaja el pecho con poca fatiga.",
    )

    /** The exercise names the block and the review rows resolve through. */
    val exerciseNames = mapOf("bench" to "Press de banca", "row" to "Remo con barra", "fly" to "Aperturas")

    /** A week of the saved block; week 2 is the current one by default. */
    fun week(week: Int, phase: WeekPhase, deload: Boolean = false, completed: Int = 3) = PlanWeek(
        week = week,
        startDate = LocalDate.fromEpochDays(planStart.toEpochDays() + 7 * (week - 1)),
        endDate = LocalDate.fromEpochDays(planStart.toEpochDays() + 7 * (week - 1) + 6),
        phase = phase,
        deload = deload,
        planned = 4,
        completed = completed,
        adherencePct = completed * 25,
        sessions = listOf(PlanWeekSession(0, "Empuje", "routine-0", done = completed > 0)),
        cardioMinDone = 0,
        cardioMinTarget = null,
        stepsAvg = null,
        stepsTarget = null,
    )

    /** Week 1 done, week 2 in progress, weeks 3 and 4 upcoming. */
    fun weeks(): List<PlanWeek> = listOf(
        week(1, WeekPhase.Past, completed = 4),
        week(2, WeekPhase.Current),
        week(3, WeekPhase.Upcoming, completed = 0),
        week(4, WeekPhase.Upcoming, completed = 0),
    )

    /** A block paused for one week with its moved end date (US-106). */
    val pausedBlock = BlockPause(
        paused = true,
        pausedWeeks = 1,
        blockEnd = LocalDate(2026, 11, 22),
        extensionDays = 0,
        minEnd = LocalDate(2026, 11, 16),
        maxEnd = LocalDate(2026, 11, 29),
        extendable = true,
    )

    /** A block whose current week was extended by three days (the pencil of "Tu bloque"). */
    val extendedBlock = BlockPause(
        paused = false,
        pausedWeeks = 0,
        blockEnd = LocalDate(2026, 11, 25),
        extensionDays = 3,
        minEnd = LocalDate(2026, 11, 16),
        maxEnd = LocalDate(2026, 11, 29),
        extendable = true,
    )

    /** A week summary with every figure of the summary of a finished week. */
    fun weekSummary(seen: Boolean = false, nutrition: WeekSummaryNutrition? = null): WeekSummary = WeekSummary(
        planId = PLAN_ID,
        week = 2,
        startDate = LocalDate(2026, 11, 3),
        endDate = LocalDate(2026, 11, 9),
        seen = seen,
        sessionsDone = 3,
        sessionsPlanned = 4,
        groups = listOf(GroupSets(MuscleGroup.Chest, 10.0), GroupSets(MuscleGroup.Back, 12.0)),
        weight = WeekSummaryWeight(80.2, -0.3, -0.5, 0.0, -0.4),
        completeDays = 5,
        weighInDays = 3,
        changes = emptyList(),
        deload = false,
        stalls = emptyList(),
        nutrition = nutrition,
    )

    /** A block review with adherence, one lift and an open calorie-phase proposal (the "No conseguido" case). */
    fun blockReview(decision: PhaseDecision? = null, seen: Boolean = false, stalled: Boolean = false): BlockReview =
        BlockReview(
            id = "review-1",
            planId = PLAN_ID,
            endedOn = reviewEnd,
            adherence = BlockAdherence(14, 16, 88),
            lifts = listOf(
                // A stalled lift: the first and last estimated 1RM are the same, so the review says "No conseguido".
                BlockLift(
                    exerciseId = "bench",
                    weeks = listOf(BlockLiftWeek(1, 100.0), BlockLiftWeek(3, if (stalled) 100.0 else 105.0)),
                    firstE1rm = 100.0,
                    lastE1rm = if (stalled) 100.0 else 105.0,
                    changeKg = if (stalled) 0.0 else 5.0,
                    stalled = stalled,
                ),
            ),
            groups = listOf(GroupSets(MuscleGroup.Chest, 10.0)),
            weight = null,
            outcomes = emptyList(),
            waist = null,
            stalls = emptyList(),
            phaseProposal = PhaseProposal(
                Phase.Maintenance,
                PhaseChangeReason.CutLength,
                mapOf("deficit_weeks" to 12.0),
            ),
            phaseDecision = decision,
            seen = seen,
        )

    /** A stored report row (weekly or block) with its period. */
    fun reportItem(id: String = "report-1", kind: ReportKind = ReportKind.WeekSummary, week: Int? = 2) = ReportItem(
        id = id,
        kind = kind,
        planId = PLAN_ID,
        week = week,
        paused = false,
        periodStart = LocalDate(2026, 11, 3),
        periodEnd = LocalDate(2026, 11, 9),
        createdAt = Instant.parse("2026-11-10T08:00:00Z"),
        phaseDecision = null,
    )

    /** The Trainer hub with a saved block, its weeks and the calorie proposal of the accepted plan. */
    fun contentWithBlock(
        block: BlockSummary = BlockSummary.Saved(block()),
        pause: BlockPause? = null,
        proposal: CalorieProposal? = null,
        blockReviewDue: Boolean = false,
    ): TrainerUiState.Content = TrainerUiState.Content(
        plan = PlanSummary.Accepted(plan()),
        calorieProposal = proposal,
        block = block,
        weeks = weeks(),
        pause = pause,
        provisionalCount = 2,
        hasWeekSummary = true,
        blockReviewDue = blockReviewDue,
    )

    /** The hub before any plan: the "Crear plan" entry only. */
    fun contentWithoutPlan(): TrainerUiState.Content = TrainerUiState.Content(provisionalCount = null)

    /** The hub with a proposal waiting for its decision. */
    fun contentWithProposal(): TrainerUiState.Content = TrainerUiState.Content(
        plan = PlanSummary.Proposed(plan(status = PlanStatus.Proposed)),
        provisionalCount = 1,
    )

    /** "Tu plan" for an accepted plan with an open cardio-minutes sheet. */
    fun cardioSheet(draft: Int = 150) = CardioEdit(draft = draft, saved = 150)

    /** A proposal of the plan, with its unconfirmed values, open sheets and busy flags as the caller sets them. */
    fun proposal(
        unconfirmed: Boolean = false,
        stale: Boolean = false,
        dataChanged: Boolean = false,
        activitySheet: ActivityLevel? = null,
        cardioEdit: CardioEdit? = null,
        accepting: Boolean = false,
        limitedBy: PlanLimit? = null,
    ): PlanProposalUiState.Proposal = PlanProposalUiState.Proposal(
        plan = plan(status = PlanStatus.Proposed, limitedBy = limitedBy),
        unconfirmedValues = if (unconfirmed) unconfirmedRows() else emptyList(),
        stale = stale,
        activitySheet = activitySheet,
        dataChanged = dataChanged,
        cardioEdit = cardioEdit,
        accepting = accepting,
    )

    /** Failure kinds the proposal screen names; the blocked one carries the missing fields. */
    val failureKinds: List<PlanFailureKind> = listOf(
        PlanFailureKind.Timeout,
        PlanFailureKind.Infeasible,
        PlanFailureKind.BlockInProgress,
        PlanFailureKind.Other,
    )

    /** "Antes de empezar" in plan or block mode, with the inputs and optional open sheets. */
    fun beforeStart(
        mode: BeforeStartMode = BeforeStartMode.Plan,
        sheet: ActivityLevel? = null,
        startChoice: StartChoiceStep = StartChoiceStep.None,
    ) = BeforeStartUiState.Content(
        inputs = completeInputs(if (mode == BeforeStartMode.Plan) PlanInputKind.Plan else PlanInputKind.Block),
        mode = mode,
        planId = PLAN_ID,
        replacingPlanId = null,
        sheet = sheet,
        startChoice = startChoice,
    )

    /** "Tus datos" rows: the complete set, or the set with one provisional value. */
    fun yourDataContent(provisional: Boolean = false): YourDataUiState.Content = YourDataUiState.Content(
        rows = if (provisional) unconfirmedRows() else completeInputs().inputs.map { it.toRow() },
    )

    /** The catalogue exercises of the builder and the slot picker. */
    val bench = catalog("bench", "Press de banca", MuscleGroup.Chest, MovementPattern.HorizontalPush)
    val row = catalog("row", "Remo con barra", MuscleGroup.Back, MovementPattern.HorizontalPull)
    val fly = catalog("fly", "Aperturas", MuscleGroup.Chest, MovementPattern.Isolation)
    val bike = CatalogExercise(
        Exercise(
            "bike",
            "bike",
            null,
            MuscleGroup.FullBody,
            emptyList(),
            Equipment.Barbell,
            ExerciseMetricType.Duration,
            false,
        ),
        "Bicicleta estática",
        hasMedia = false,
    )

    private fun catalog(id: String, name: String, group: MuscleGroup, pattern: MovementPattern) = CatalogExercise(
        Exercise(
            id = id,
            key = id,
            name = null,
            muscleGroup = group,
            secondaryMuscles = emptyList(),
            equipment = Equipment.Barbell,
            metricType = ExerciseMetricType.WeightReps,
            isCustom = false,
            movementPattern = pattern,
        ),
        name,
        hasMedia = false,
    )

    /** The catalogue exercise of the folder's squat, for the folder content's name lookup. */
    val squat = catalog("squat", "Sentadilla", MuscleGroup.Quads, MovementPattern.Squat)

    private fun folderExercise(id: String, group: MuscleGroup, pattern: MovementPattern) = FolderBlockExercise(
        id = id,
        muscleGroup = group,
        secondary = emptyList(),
        countsAsStrength = true,
        movementPattern = pattern,
        equipment = Equipment.Barbell,
        sets = 3,
        repMin = 8,
        repMax = 10,
        rpe = null,
        restSeconds = 120,
        plannedWeightKg = null,
    )

    /** A two-day folder "Fuerza": "Torso A" (bench, row) and "Pierna A" (squat); its status is the core check. */
    val folderDraft = FolderBlockDraft(
        routines = listOf(
            FolderBlockRoutine(
                "Torso A",
                listOf(
                    folderExercise("bench", MuscleGroup.Chest, MovementPattern.HorizontalPush),
                    folderExercise("row", MuscleGroup.Back, MovementPattern.HorizontalPull),
                ),
            ),
            FolderBlockRoutine("Pierna A", listOf(folderExercise("squat", MuscleGroup.Quads, MovementPattern.Squat))),
        ),
        intention = TrainingIntention.Muscle,
        daysPerWeek = 2,
        equipment = setOf(Equipment.Barbell),
    )

    /** The folder block editor (FolderBlockScreen Content) with the given picker, copy state and save state. */
    fun folderContent(
        draft: FolderBlockDraft = folderDraft,
        pickerTarget: FolderPickerTarget? = null,
        pickerEntries: List<PickerEntry> = emptyList(),
        saving: Boolean = false,
        saveError: BuilderSaveError? = null,
        discardDialog: Boolean = false,
        edited: Boolean = false,
    ) = FolderBlockUiState.Content(
        folderName = "Fuerza",
        name = "Fuerza · bloque",
        draft = draft,
        status = draft.status(),
        pickerTarget = pickerTarget,
        pickerEntries = pickerEntries,
        exercises = listOf(bench, row, squat).associateBy { it.id },
        favouriteIds = emptySet(),
        saving = saving,
        saveError = saveError,
        discardDialog = discardDialog,
        edited = edited,
    )

    /** The swap picker of the flagged bench press: two candidates, or still loading them. */
    fun swapPicker(loading: Boolean = false) = SwapPickerUi(
        exerciseId = "bench",
        exerciseName = "Press de banca",
        candidates = listOf(
            SwapCandidateUi("fly", "Aperturas", isFavorite = true),
            SwapCandidateUi("row", "Remo con barra", isFavorite = false),
        ),
        loading = loading,
    )

    /** The block preview with the replace picker open on the first slot, showing [alternatives]. */
    fun replaceState(alternatives: AlternativesState) = BlockPreviewUiState.Preview(
        block = block(saved = false),
        exerciseNames = exerciseNames,
        selectedWeek = 1,
        replaceSlot = "0/0",
        alternatives = alternatives,
    )

    /** The builder after the types step: the start state of the "Desde cero" builder. */
    fun builderState(): BuilderState = (
        BuilderState.start(
            listOf(RoutineType.Upper, RoutineType.Lower),
            io.github.lopezjavier.appfitness.domain.model.TrainingIntention.Muscle,
            180,
            listOf(Equipment.Barbell),
            150,
            "bike",
        ) as BuilderStart.Ready
        ).state

    /** The builder with every required slot picked with [bench]: days that show their exercises. */
    fun filledState(): BuilderState {
        val start = builderState()
        return start.days.indices.fold(start) { acc, day ->
            acc.days[day].slots.indices.fold(acc) { inner, slot ->
                inner.pick(day, slot, Pick(bench.id, bench.exercise.muscleGroup, bench.exercise.movementPattern, null))
            }
        }
    }

    /** The builder step around [state]; the cardio row and every exercise come from the fixture catalogue. */
    fun builderUi(
        state: BuilderState = builderState(),
        selectedDay: Int = 0,
        openSlot: SlotRef? = null,
        pickerEntries: List<PickerEntry> = emptyList(),
        saving: Boolean = false,
        saveError: BuilderSaveError? = null,
        discardDialog: Boolean = false,
    ) = BlockBuilderUiState.Builder(
        state = state,
        selectedDay = selectedDay,
        openSlot = openSlot,
        pickerEntries = pickerEntries,
        cardioOptions = listOf(bike),
        cardioExercise = bike,
        exercises = listOf(bench, row, fly, bike).associateBy { it.id },
        saving = saving,
        saveError = saveError,
        discardDialog = discardDialog,
        validPatterns = listOf(MovementPattern.HorizontalPush, MovementPattern.HorizontalPull),
    )

    /** The types step of the 4-day split, with the day sheet open on [openDay] when given. */
    fun typesState(openDay: Int? = null, noFit: NoFitInfo? = null): BlockBuilderUiState.Types {
        val split = splits(4)[0]
        return BlockBuilderUiState.Types(
            split = split,
            chosen = split.types,
            options = dayTypeOptions(split.types.size),
            openDay = openDay,
            noFit = noFit,
        )
    }

    /** The targets step: one row per group, with a suggestion offered on chest. */
    fun targetsState(noFit: NoFitInfo? = null): BlockBuilderUiState.Targets = BlockBuilderUiState.Targets(
        rows = listOf(
            GroupTargetRow(MuscleGroup.Chest, GroupPriority.Prioritise, 10, 12, null, SuggestionState.Offered),
            GroupTargetRow(MuscleGroup.Back, GroupPriority.Maintain, 12, 12, null, null),
            GroupTargetRow(MuscleGroup.Triceps, GroupPriority.None, 2, 2, null, null),
        ),
        adherencePct = 88,
        cut = false,
        noFit = noFit,
    )

    /** The day types of a split: five options per day, all enabled, the first one recommended. */
    fun dayTypeOptions(days: Int): List<List<DayTypeOption>> = List(days) {
        RoutineType.entries.mapIndexed { index, type ->
            DayTypeOption(type, recommended = index == 0, disabledReason = null, strengthNote = false)
        }
    }

    /** Splits of [days] days, recommended first. */
    fun splits(days: Int = 4) = SplitCatalog.splitsFor(days)

    /** Folder-review days check that refuses a block with one lifting routine and two days. */
    val refusedDays = DaysCheck.Refused(routines = 1, days = 2)

    private const val PLAN_ID = "plan-1"
    private val SESSION_NAMES = listOf("Empuje", "Tirón", "Piernas")
}
