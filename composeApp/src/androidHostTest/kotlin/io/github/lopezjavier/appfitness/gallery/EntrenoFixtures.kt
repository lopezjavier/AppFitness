package io.github.lopezjavier.appfitness.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.MuscleShare
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.exercises.ChatFailureReason
import io.github.lopezjavier.appfitness.feature.exercises.ChatMessage
import io.github.lopezjavier.appfitness.feature.exercises.EditExerciseForm
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseChatUiState
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseHistoryRow
import io.github.lopezjavier.appfitness.feature.exercises.ExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.HistorySetUi
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.RecordsState
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseRow
import io.github.lopezjavier.appfitness.feature.routines.BuilderRowUi
import io.github.lopezjavier.appfitness.feature.routines.FolderSheetState
import io.github.lopezjavier.appfitness.feature.routines.PlannedSetUi
import io.github.lopezjavier.appfitness.feature.routines.RoutineDetailsExerciseUi
import io.github.lopezjavier.appfitness.feature.routines.RoutinesUiState
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import io.github.lopezjavier.appfitness.feature.workout.DetailExercise
import io.github.lopezjavier.appfitness.feature.workout.DetailSet
import io.github.lopezjavier.appfitness.feature.workout.HistoryRow
import io.github.lopezjavier.appfitness.feature.workout.SaveAsRoutineState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.time.Instant

/** Fixed Entreno data for the gallery: Spanish names, no personal data, the shared dates of [GalleryFixtures]. */
internal object EntrenoFixtures {
    private val now: Instant = GalleryFixtures.now

    val strengthFolder = RoutineFolder("folder_strength", "Fuerza")
    val hypertrophyFolder = RoutineFolder("folder_hypertrophy", "Hipertrofia")
    val folders = listOf(strengthFolder, hypertrophyFolder)

    val upperRoutine = RoutineSummary("routine_upper", "Torso A", 5, folderId = strengthFolder.id, isNext = true)
    val lowerRoutine = RoutineSummary("routine_lower", "Pierna A", 4, folderId = strengthFolder.id)
    val fullBodyRoutine = RoutineSummary("routine_full_body", "Cuerpo completo", 6)
    val routines = listOf(upperRoutine, lowerRoutine, fullBodyRoutine)

    /** The routines list: the three routines in two folders plus "Mis rutinas", with the given sheet or state. */
    fun listContent(
        isOnline: Boolean = true,
        collapsed: Set<String> = emptySet(),
        menuFor: RoutineSummary? = null,
        moveFor: RoutineSummary? = null,
        folderSheet: FolderSheetState? = null,
        folderSaving: Boolean = false,
        folderMenuFor: RoutineFolder? = null,
        routineList: List<RoutineSummary> = routines,
        folderList: List<RoutineFolder> = folders,
    ) = RoutinesUiState.Content(
        routines = routineList,
        isOnline = isOnline,
        menuFor = menuFor,
        folders = folderList,
        collapsedFolderIds = collapsed,
        folderSheet = folderSheet,
        moveFor = moveFor,
        folderMenuFor = folderMenuFor,
        folderSaving = folderSaving,
    )

    private fun set(weight: Double?, reps: Int, type: SetType = SetType.Working) = PlannedSet(weight, reps, type)

    private fun detailExercise(id: String, name: String, vararg sets: PlannedSet) =
        RoutineDetailsExerciseUi(id, name, null, sets.toList())

    val routineDetailsExercises = listOf(
        detailExercise(
            "bench",
            "Press de banca",
            set(40.0, 10, SetType.WarmUp),
            set(60.0, 8),
            set(60.0, 8),
            set(60.0, 8),
        ),
        detailExercise("row", "Remo con barra", set(50.0, 10), set(50.0, 10), set(50.0, 10)),
        detailExercise("plank", "Plancha", PlannedSet.ofMinutes(1), PlannedSet.ofMinutes(1)),
    )

    fun builderRow(
        rowId: Long,
        exerciseId: String,
        name: String,
        sets: List<PlannedSetUi> = emptyList(),
        label: SupersetLabel? = null,
        index: Int = 0,
        restSeconds: Int = 120,
    ) = BuilderRowUi(
        rowId = rowId,
        exerciseId = exerciseId,
        name = name,
        imageKey = null,
        plannedSets = sets,
        restSeconds = restSeconds,
        supersetLabel = label,
        canMoveUp = index > 0,
        canMoveDown = true,
        canGroupWithNext = true,
        equipment = Equipment.Barbell,
    )

    fun plannedSet(id: Long, weight: String = "", reps: String = "8") =
        PlannedSetUi(setId = id, weight = weight, reps = reps, type = SetType.Working)

    val draftRows = listOf(
        builderRow(1, "bench", "Press de banca", listOf(plannedSet(1, "60", "8"), plannedSet(2, "60", "8"))),
        builderRow(2, "row", "Remo con barra", listOf(plannedSet(3, "50", "10")), index = 1),
    )

    private fun catalog(
        id: String,
        name: String,
        group: MuscleGroup,
        equipment: Equipment,
        metric: ExerciseMetricType = ExerciseMetricType.WeightReps,
    ) = CatalogExercise(
        Exercise(id, id, null, group, emptyList(), equipment, metric, isCustom = false),
        name,
        hasMedia = false,
    )

    val bench = catalog("bench", "Press de banca", MuscleGroup.Chest, Equipment.Barbell)
    val incline = catalog("incline", "Press inclinado con mancuernas", MuscleGroup.Chest, Equipment.Dumbbell)
    val row = catalog("row", "Remo con barra", MuscleGroup.Back, Equipment.Barbell)
    val squat = catalog("squat", "Sentadilla", MuscleGroup.Quads, Equipment.Barbell)
    val plank = catalog("plank", "Plancha", MuscleGroup.Core, Equipment.Barbell, ExerciseMetricType.Duration)
    val pickerCatalog = listOf(bench, incline, row, squat, plank)

    val benchDetail = ExerciseDetail(
        exercise = bench.exercise,
        name = "Press de banca",
        info = ExerciseInfo(
            key = "bench",
            name = "Press de banca",
            description = "Empuje horizontal de pecho con barra.",
            instructions = listOf("Tumbado en el banco, baja la barra al pecho.", "Empuja hasta extender los brazos."),
            tips = listOf("Mantén los omóplatos juntos."),
        ),
    )

    val similarRows = listOf(
        SimilarExerciseRow(incline, similarity = 0.87),
    )

    val historyRows = listOf(
        ExerciseHistoryRow(
            sessionId = "session_1",
            startedAt = now,
            name = "Torso A",
            sets = listOf(
                HistorySetUi(1, SetType.Working, "60 kg × 8", 8.0),
                HistorySetUi(2, SetType.Working, "60 kg × 8", null),
            ),
            records = setOf(PersonalRecordType.HeaviestLift),
        ),
    )

    val detailSets = listOf(
        DetailSet(1, SetType.WarmUp, "40 kg × 10"),
        DetailSet(2, SetType.Working, "60 kg × 8", rpe = 8.0),
        DetailSet(3, SetType.Working, "60 kg × 8"),
    )

    val sessionExercises = listOf(
        DetailExercise(
            0,
            "bench",
            "Press de banca",
            detailSets,
            isPersonalRecord = true,
            records = setOf(PersonalRecordType.HeaviestLift),
        ),
        DetailExercise(
            1,
            "row",
            "Remo con barra",
            listOf(DetailSet(1, SetType.Working, "50 kg × 10")),
            isPersonalRecord = false,
        ),
    )

    val sessionStats = SessionStats(durationSeconds = 3_300, completedSets = 14, volumeKg = 4_250.0)
    val sessionSplit = listOf(MuscleShare(MuscleGroup.Chest, 6, 0.5), MuscleShare(MuscleGroup.Back, 4, 0.33))

    val saveAsRoutine = SaveAsRoutineState(existingNames = listOf("Torso A"))

    val calendarMonth = YearMonth(2026, 10)
    val calendarMarked = setOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 20))

    val dayRows = listOf(
        HistoryRow("s_1", DateLabel.Today, "Torso A", 3_300, 4_250.0, 14),
        HistoryRow("s_2", DateLabel.Today, "Cardio", 1_200, 0.0, 3),
    )

    val editForm = EditExerciseForm(
        name = "Press con mancuerna propio",
        muscleGroup = MuscleGroup.Chest,
        equipment = Equipment.Dumbbell,
        perHand = false,
    )

    val today: LocalDate = GalleryFixtures.today

    val records = RecordsState.Content(
        listOf(
            PersonalRecord(
                PersonalRecordType.HeaviestLift,
                65.0,
                sessionId = "session_1",
                date = GalleryFixtures.today,
            ),
        ),
    )

    private const val QUESTION_ID = 1
    private const val ANSWER_ID = 2

    val chatAsking = ExerciseChatUiState(
        messages = listOf(ChatMessage.Question(QUESTION_ID, "¿Cómo mejoro la técnica en la bajada?")),
        draft = "",
        sending = true,
    )

    val chatAnswered = ExerciseChatUiState(
        messages = listOf(
            ChatMessage.Question(QUESTION_ID, "¿Cómo mejoro la técnica en la bajada?"),
            ChatMessage.Answer(ANSWER_ID, "Baja la barra despacio y controla los codos hasta el pecho."),
        ),
    )

    val chatFailed = ExerciseChatUiState(
        messages = listOf(
            ChatMessage.Question(QUESTION_ID, "¿Cuántas series hacen falta?"),
            ChatMessage.Failure(ANSWER_ID, ChatFailureReason.Busy),
        ),
    )

    /** Exercise thumbnails are placeholders in the gallery: no bundled images are needed. */
    private object NoExerciseMedia : ExerciseMediaUris {
        override fun start(key: String): String? = null

        override fun peak(key: String): String? = null
    }

    /** Wraps an entry so its exercise images are the placeholders (the bundle is not needed to render). */
    @Composable
    fun WithoutMedia(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalExerciseMediaUris provides NoExerciseMedia, content = content)
    }
}
