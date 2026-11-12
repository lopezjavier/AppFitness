package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.domain.model.ChartPoint
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleVolumeHistory
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.domain.usecase.MonthSummary
import io.github.lopezjavier.appfitness.domain.usecase.PeriodAverage
import io.github.lopezjavier.appfitness.feature.activity.ActivityScreen
import io.github.lopezjavier.appfitness.feature.activity.ActivityUiState
import io.github.lopezjavier.appfitness.feature.activity.activityContent
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressScreen
import io.github.lopezjavier.appfitness.feature.analytics.ExerciseProgressUiState
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeScreen
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeUiState
import io.github.lopezjavier.appfitness.feature.analytics.StrengthChip
import io.github.lopezjavier.appfitness.feature.analytics.StrengthScreen
import io.github.lopezjavier.appfitness.feature.analytics.StrengthUiState
import io.github.lopezjavier.appfitness.feature.analytics.stats.MuscleDetail
import io.github.lopezjavier.appfitness.feature.analytics.stats.SectionState
import io.github.lopezjavier.appfitness.feature.analytics.stats.StatsExercise
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsScreen
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsUiState
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryContent
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryDay
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryUiState
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryView
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncStatus
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncUiState
import io.github.lopezjavier.appfitness.quality.qualityStatsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val Today = GalleryFixtures.today
private val Chips =
    listOf(StrengthChip("bench", "Press banca"), StrengthChip("squat", "Sentadilla"), StrengthChip("row", "Remo"))

private fun strength(): List<GalleryEntry> {
    fun entry(state: String, ui: StrengthUiState) = GalleryEntry(
        PERFIL_AREA,
        "Fuerza",
        state,
        covers = setOf("StrengthRoute"),
        alsoOpenedFrom = listOf("Entreno"),
    ) {
        StrengthScreen(ui, onEvent = {
        }, onOpenExercise = {}, onOpenMuscleVolume = {}, onStartWorkout = {}, onBack = {})
    }
    return listOf(
        entry("loading", StrengthUiState.Loading),
        entry("empty", StrengthUiState.Empty),
        entry("error", StrengthUiState.Error),
        entry("data, growing", StrengthUiState.Content(Chips, "bench", StrengthSummary(113.5, 5.0))),
        entry("data, no change", StrengthUiState.Content(Chips, "squat", StrengthSummary(140.0, 0.0))),
        entry("record loading", StrengthUiState.Content(Chips, "row", null)),
    )
}

private fun muscleVolume(): List<GalleryEntry> {
    val weeks = (0..7).map { LocalDate.fromEpochDays(LocalDate(2026, 9, 1).toEpochDays() + 7 * it) }
    val history = MuscleVolumeHistory(
        weeks,
        mapOf(
            MuscleGroup.Chest to listOf(0.0, 1000.0, 0.0, 2500.0, 0.0, 0.0, 4260.0, 5400.0),
            MuscleGroup.Back to listOf(100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 300.0),
        ),
    )

    fun entry(state: String, ui: MuscleVolumeUiState) = GalleryEntry(
        PERFIL_AREA,
        "Volumen",
        state,
        covers = setOf("MuscleVolumeRoute"),
        alsoOpenedFrom = listOf("Entreno"),
    ) {
        MuscleVolumeScreen(ui, onEvent = {}, onStartWorkout = {}, onBack = {})
    }
    return listOf(
        entry("loading", MuscleVolumeUiState.Loading),
        entry("empty", MuscleVolumeUiState.Empty),
        entry("error", MuscleVolumeUiState.Error),
        entry("all groups", MuscleVolumeUiState.Content(history, null)),
        entry("one group", MuscleVolumeUiState.Content(history, MuscleGroup.Chest)),
    )
}

private fun exerciseProgress(): List<GalleryEntry> {
    fun point(day: Int, value: Double, record: Boolean = false) = ChartPoint(LocalDate(2026, 10, day), value, record)

    val two = ExerciseProgress(
        ProgressMetric.OneRepMax,
        listOf(point(1, 100.0), point(8, 107.5, true)),
        listOf(point(1, 3_000.0), point(8, 3_400.0)),
    )
    val one = ExerciseProgress(ProgressMetric.OneRepMax, listOf(point(1, 100.0)), emptyList())

    fun entry(state: String, ui: ExerciseProgressUiState) = GalleryEntry(
        PERFIL_AREA,
        "Progreso del ejercicio",
        state,
        covers = setOf("ExerciseProgressRoute"),
        alsoOpenedFrom = listOf("Entreno"),
    ) {
        ExerciseProgressScreen(ui, onEvent = {}, onBack = {})
    }
    return listOf(
        entry("loading", ExerciseProgressUiState.Loading),
        entry("empty", ExerciseProgressUiState.Empty("Press de banca")),
        entry("error", ExerciseProgressUiState.Error),
        entry("data", ExerciseProgressUiState.Content("Press de banca", ProgressRange.ThreeMonths, two)),
        entry("one session only", ExerciseProgressUiState.Content("Press de banca", ProgressRange.ThreeMonths, one)),
    )
}

private fun trainingStats(): List<GalleryEntry> {
    val bench = StatsExercise("bench", "Press banca", 9.0, 3)
    val full = qualityStatsState() as TrainingStatsUiState

    fun entry(state: String, ui: TrainingStatsUiState, kind: GalleryKind = GalleryKind.Screen, vararg covers: String) =
        GalleryEntry(
            PERFIL_AREA,
            "Estadísticas",
            state,
            kind,
            covers = setOf("StatsRoute") + covers,
            alsoOpenedFrom = listOf("Entreno"),
        ) {
            TrainingStatsScreen(ui, onEvent = {}, onOpenExercise = {}, onBack = {})
        }
    return listOf(
        entry("loading", TrainingStatsUiState()),
        entry("empty", TrainingStatsUiState(summary = SectionState.Empty)),
        entry("data", full),
        entry(
            "every section failed",
            TrainingStatsUiState(
                summary = SectionState.Error,
                setCounts = SectionState.Error,
                muscleSets = SectionState.Error,
                topExercises = SectionState.Error,
                radar = SectionState.Error,
                bodyMap = SectionState.Error,
            ),
        ),
        entry(
            "muscle detail sheet",
            full.copy(
                openMuscle = MuscleGroup.Chest,
                muscleDetail = SectionState.Content(MuscleDetail(MuscleGroup.Chest, 3.0, listOf(bench))),
            ),
            GalleryKind.Sheet,
            "MuscleDetailSheet",
        ),
        entry(
            "muscle detail sheet, not trained",
            full.copy(
                openMuscle = MuscleGroup.Quads,
                muscleDetail = SectionState.Content(MuscleDetail(MuscleGroup.Quads, 0.0, emptyList())),
            ),
            GalleryKind.Sheet,
        ),
    )
}

private fun activity(): List<GalleryEntry> {
    fun entry(
        state: String,
        ui: ActivityUiState,
        health: HealthSyncStatus = HealthSyncStatus.NeverSynced,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
    ) = GalleryEntry(
        PERFIL_AREA,
        "Actividad",
        state,
        kind,
        covers = setOf("ActivityRoute") + covers,
        interact = interact,
    ) {
        ActivityScreen(ui, MutableStateFlow(true), onEvent = {
        }, healthSync = HealthSyncUiState(health), onHealthEvent = {}, onBack = {})
    }
    return listOf(
        entry("loading", ActivityUiState.Loading),
        entry("empty", ActivityUiState.Empty),
        entry("error", ActivityUiState.Error),
        entry("offline", ActivityUiState.Offline),
        entry("data", activityContent(), HealthSyncStatus.Syncing),
        entry("data, permission needed", activityContent(), HealthSyncStatus.PermissionNeeded),
        entry("day loading", activityContent(dayLoading = true)),
        entry("day failed", activityContent(dayError = true)),
        entry(
            "calendar sheet",
            activityContent(),
            interact = { onNodeWithTag("activity_calendar_action").performClick() },
            kind = GalleryKind.Sheet,
            covers = arrayOf("ActivityScreen"),
        ),
        entry(
            "chart metric sheet",
            activityContent(),
            interact = { onNodeWithTag("activity_chart_metric_trigger").performScrollTo().performClick() },
            kind = GalleryKind.Sheet,
            covers = arrayOf("ActivityScreen"),
        ),
    )
}

private fun diaryHistory(): List<GalleryEntry> {
    val goals = DiaryGoals(2400, 180, 270, 67)
    val eighteenth = LocalDate(2026, 10, 18)

    fun content(
        view: DiaryHistoryView = DiaryHistoryView.Day,
        selected: DiaryHistoryDay? = DiaryHistoryDay(eighteenth, MealTotals(1840.0, 142.0, 210.0, 48.0), goals),
        goalsSet: DiaryGoals? = goals,
    ) = DiaryHistoryUiState.Content(
        DiaryHistoryContent(
            month = YearMonth(2026, 10),
            today = Today,
            canGoNext = false,
            markedDays = setOf(eighteenth, LocalDate(2026, 10, 20)),
            view = view,
            selectedDay = selected,
            week = PeriodAverage(3, 2000.0, 150.0, 220.0, 60.0),
            monthSummary = MonthSummary(20, 38000.0, 1900.0, 140.0, 200.0, 55.0),
            goals = goalsSet,
        ),
    )

    fun entry(state: String, ui: DiaryHistoryUiState, wide: Boolean = false) =
        GalleryEntry(PERFIL_AREA, "Calorías", state, covers = setOf("DiaryHistoryRoute"), wide = wide) {
            DiaryHistoryScreen(ui, onEvent = {}, onBack = {})
        }
    return listOf(
        entry("loading", DiaryHistoryUiState.Loading),
        entry("error", DiaryHistoryUiState.Error),
        entry("day with entries", content()),
        entry("day without entries", content(selected = DiaryHistoryDay(LocalDate(2026, 10, 19), null, goals))),
        entry(
            "no goals",
            content(
                goalsSet = null,
                selected = DiaryHistoryDay(eighteenth, MealTotals(1840.0, 142.0, 210.0, 48.0), null),
            ),
        ),
        entry("week", content(DiaryHistoryView.Week)),
        entry("month", content(DiaryHistoryView.Month)),
    )
}

internal fun perfilStatsEntries(): List<GalleryEntry> =
    strength() + muscleVolume() + exerciseProgress() + trainingStats() + activity() + diaryHistory()
