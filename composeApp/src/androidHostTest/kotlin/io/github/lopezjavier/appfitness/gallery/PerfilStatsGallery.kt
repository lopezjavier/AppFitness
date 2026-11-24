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
import io.github.lopezjavier.appfitness.domain.model.MuscleSetsHistory
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
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryContent
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryDay
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryUiState
import io.github.lopezjavier.appfitness.feature.diary.DiaryHistoryView
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncStatus
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val Today = GalleryFixtures.today
private val Chips =
    listOf(
        StrengthChip("bench", "Press banca", imageKey = "barbell-bench-press"),
        StrengthChip("squat", "Sentadilla", imageKey = "barbell-squat"),
        StrengthChip("row", "Remo"),
    )

private fun strength(): List<GalleryEntry> {
    fun entry(state: String, ui: StrengthUiState) = GalleryEntry(
        PERFIL_AREA,
        "Fuerza",
        state,
        covers = setOf("StrengthRoute"),
        alsoOpenedFrom = listOf("Entreno"),
    ) {
        StrengthScreen(ui, onEvent = {
        }, onOpenExercise = {}, onStartWorkout = {}, onBack = {})
    }
    return listOf(
        entry("loading", StrengthUiState.Loading),
        entry("empty", StrengthUiState.Empty),
        entry("error", StrengthUiState.Error),
        entry(
            "data, growing",
            StrengthUiState.Content(
                Chips,
                "bench",
                StrengthSummary(113.5, 5.0),
                listOf(98.0, 101.5, 100.0, 104.0, 108.5, 107.0, 111.0, 113.5),
            ),
        ),
        entry(
            "data, change inside the margin",
            StrengthUiState.Content(Chips, "bench", StrengthSummary(113.5, 1.5, withinMargin = true)),
        ),
        entry("data, no change", StrengthUiState.Content(Chips, "squat", StrengthSummary(140.0, 0.0))),
        entry("record loading", StrengthUiState.Content(Chips, "row", null)),
    )
}

private fun muscleVolume(): List<GalleryEntry> {
    val weeks = (0..7).map { LocalDate.fromEpochDays(LocalDate(2026, 9, 1).toEpochDays() + 7 * it) }
    val history = MuscleSetsHistory(
        weeks,
        mapOf(
            MuscleGroup.Chest to listOf(0.0, 8.0, 0.0, 12.5, 14.0, 10.0, 16.0, 18.5),
            MuscleGroup.Back to listOf(6.0, 0.0, 0.0, 9.0, 11.0, 10.5, 12.0, 8.0),
            MuscleGroup.Quads to listOf(10.0, 12.0, 12.0, 14.0, 0.0, 16.0, 16.0, 16.0),
        ),
    )

    fun entry(state: String, ui: MuscleVolumeUiState, wide: Boolean = false, fontScale: Float = 1f) = GalleryEntry(
        PERFIL_AREA,
        "Series por grupo muscular",
        state,
        covers = setOf("MuscleVolumeRoute"),
        alsoOpenedFrom = listOf("Entreno"),
        wide = wide,
        tall = ui is MuscleVolumeUiState.Content,
        fontScale = fontScale,
    ) {
        MuscleVolumeScreen(ui, onEvent = {}, onStartWorkout = {}, onBack = {})
    }
    return listOf(
        entry("loading", MuscleVolumeUiState.Loading),
        entry("empty", MuscleVolumeUiState.Empty),
        entry("error", MuscleVolumeUiState.Error),
        entry("all groups", MuscleVolumeUiState.Content(history, null)),
        entry("one group", MuscleVolumeUiState.Content(history, MuscleGroup.Chest)),
        entry("all groups, large font (1.3)", MuscleVolumeUiState.Content(history, null), fontScale = 1.3f),
        entry("all groups, tablet", MuscleVolumeUiState.Content(history, null), wide = true),
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
        entry("empty", ExerciseProgressUiState.Empty("Press banca")),
        entry("error", ExerciseProgressUiState.Error),
        entry("data", ExerciseProgressUiState.Content("Press banca", ProgressRange.ThreeMonths, two)),
        entry("one session only", ExerciseProgressUiState.Content("Press banca", ProgressRange.ThreeMonths, one)),
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
        week: PeriodAverage? = PeriodAverage(7, 5, 2000.0, 150.0, 220.0, 60.0),
        monthSummary: MonthSummary? = MonthSummary(20, 19, 38000.0, 1900.0, 140.0, 200.0, 55.0),
    ) = DiaryHistoryUiState.Content(
        DiaryHistoryContent(
            month = YearMonth(2026, 10),
            today = Today,
            canGoNext = false,
            markedDays = setOf(eighteenth, LocalDate(2026, 10, 20)),
            view = view,
            selectedDay = selected,
            week = week,
            monthSummary = monthSummary,
            goals = goalsSet,
            weekKcal = listOf(2100f, 2350f, 2600f, 1900f, 2250f, 2480f, 1840f, 900f),
            monthKcal = listOf(2050f, 2300f, 1950f, 0f),
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
        entry("week, no complete days", content(DiaryHistoryView.Week, week = PeriodAverage(2, 0, 0.0, 0.0, 0.0, 0.0))),
        entry("month", content(DiaryHistoryView.Month)),
        entry(
            "month, no complete days",
            content(DiaryHistoryView.Month, monthSummary = MonthSummary(1, 0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        ),
    )
}

internal fun perfilStatsEntries(): List<GalleryEntry> =
    strength() + muscleVolume() + exerciseProgress() + perfilTrainingStats() + activity() + diaryHistory()
