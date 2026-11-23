package io.github.lopezjavier.appfitness.gallery

import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.feature.analytics.stats.MuscleDetail
import io.github.lopezjavier.appfitness.feature.analytics.stats.MuscleRadar
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodExerciseUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodProgressUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodRecordUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodRecordsUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.SectionState
import io.github.lopezjavier.appfitness.feature.analytics.stats.StatsExercise
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsScreen
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsUiState
import kotlinx.datetime.LocalDate

private const val BENCH = "Press banca"

private val ProgressAll = PeriodProgressUi(
    listOf(
        PeriodExerciseUi("bench", BENCH, true, 11, ProgressGlyph.Up, 95.0, 101.0, 6.3),
        PeriodExerciseUi("squat", "Sentadilla", true, 10, ProgressGlyph.Up, 110.0, 120.0, 9.1),
        PeriodExerciseUi("row", "Remo con barra", true, 9, ProgressGlyph.Same, 82.5, 84.0, 1.8),
        PeriodExerciseUi("ohp", "Press militar", true, 7, ProgressGlyph.Down, 62.0, 58.0, -6.5),
        PeriodExerciseUi("curl", "Curl con mancuernas", false, 8, ProgressGlyph.Up, 25.0, 28.0, 12.0),
        PeriodExerciseUi("lat", "Jalón al pecho", false, 6, ProgressGlyph.Same, 70.0, 69.2, -1.2),
        PeriodExerciseUi("face", "Face pull", false, 1, ProgressGlyph.Uncompared, 30.0),
    ),
)

private val RecordsThree = PeriodRecordsUi(
    listOf(
        PeriodRecordUi("bench", BENCH, true, 101.0, 95.0),
        PeriodRecordUi("squat", "Sentadilla", true, 120.0, 110.0),
        PeriodRecordUi("curl", "Curl con mancuernas", false, 28.0, 25.0),
    ),
    total = 5,
)

/** The principal exercises repeat the figures of the progress list: bench 95 to 101 kg is +6 kg, and so on. */
private fun principals() = listOf(
    StatsExercise("bench", BENCH, 36.0, 11, imageKey = "barbell-bench-press", changeKg = 6.0, compared = true),
    StatsExercise("squat", "Sentadilla", 30.0, 10, imageKey = "barbell-squat", changeKg = 10.0, compared = true),
    StatsExercise("ohp", "Press militar", 21.0, 7, changeKg = -4.0, compared = true),
    StatsExercise("face", "Face pull", 3.0, 1),
)

/** The weekly bars add up to the 412 sets of the context line. */
private val WeeklySets = listOf(30, 34, 28, 36, 40, 32, 38, 35, 33, 37, 29, 40)

private val TrainedGroups = setOf(
    MuscleGroup.Chest,
    MuscleGroup.Back,
    MuscleGroup.Quads,
    MuscleGroup.Shoulders,
    MuscleGroup.Biceps,
    MuscleGroup.Triceps,
    MuscleGroup.Hamstrings,
)

private fun statsGroups(): List<MuscleGroupSets> =
    MuscleGroup.entries.map { MuscleGroupSets(it, if (it in TrainedGroups) it.ordinal % 6 * 4 + 6.5 else 0.0) }

private fun statsBase(): TrainingStatsUiState {
    val groups = statsGroups()
    val weeks = (0..11).map { LocalDate.fromEpochDays(LocalDate(2026, 7, 6).toEpochDays() + 7 * it) }
    return TrainingStatsUiState(
        range = ProgressRange.ThreeMonths,
        summary = SectionState.Content(TrainingSummary(workouts = 24, sets = 412, volume = 0.0, durationSeconds = 0)),
        progress = SectionState.Content(ProgressAll),
        records = SectionState.Content(RecordsThree),
        setCounts = SectionState.Content(weeks.mapIndexed { i, w -> SetCountPoint(w, WeeklySets[i]) }),
        muscleSets = SectionState.Content(groups.filter { it.sets > 0 }.sortedByDescending { it.sets }),
        topExercises = SectionState.Content(principals()),
        radar = SectionState.Content(MuscleRadar(groups, groups.map { it.copy(sets = it.sets * 0.8) })),
        bodyMap = SectionState.Content(groups.filter { it.muscleGroup != MuscleGroup.FullBody }),
    )
}

private class StatsLook(
    val kind: GalleryKind = GalleryKind.Screen,
    val wide: Boolean = false,
    val fontScale: Float = 1f,
    val tallest: Boolean = false,
    val covers: Set<String> = emptySet(),
)

private fun statsEntry(state: String, ui: TrainingStatsUiState, look: StatsLook = StatsLook()) = GalleryEntry(
    PERFIL_AREA,
    "Estadísticas",
    state,
    look.kind,
    covers = setOf("StatsRoute") + look.covers,
    alsoOpenedFrom = listOf("Entreno"),
    wide = look.wide,
    tallest = look.tallest,
    fontScale = look.fontScale,
) {
    TrainingStatsScreen(ui, onEvent = {}, onOpenExercise = {}, onBack = {})
}

internal fun perfilTrainingStats(): List<GalleryEntry> {
    val full = statsBase()
    val long = StatsLook(tallest = true)
    val oneSession = PeriodProgressUi(
        listOf(
            PeriodExerciseUi("bench", BENCH, true, 1, ProgressGlyph.Uncompared, 95.0),
            PeriodExerciseUi("squat", "Sentadilla", true, 1, ProgressGlyph.Uncompared, 110.0),
        ),
    )
    val noRecords = SectionState.Content(PeriodRecordsUi(emptyList(), 0))
    val noSets = SectionState.Content(statsGroups().filter { it.muscleGroup != MuscleGroup.FullBody })
    val allUp = PeriodProgressUi(ProgressAll.exercises.filter { it.state == ProgressGlyph.Up })
    val noneUp = PeriodProgressUi(ProgressAll.exercises.filter { it.state != ProgressGlyph.Up })
    return listOf(
        statsEntry("loading", TrainingStatsUiState()),
        statsEntry("empty range", TrainingStatsUiState(summary = SectionState.Empty, bodyMap = noSets)),
        statsEntry(
            "empty range, no body map",
            TrainingStatsUiState(summary = SectionState.Empty, bodyMap = SectionState.Empty),
        ),
        statsEntry("data, all states", full, long),
        statsEntry("data, all up", full.copy(progress = SectionState.Content(allUp)), long),
        statsEntry("data, nothing up", full.copy(progress = SectionState.Content(noneUp)), long),
        statsEntry("no records", full.copy(records = noRecords), long),
        statsEntry(
            "one record",
            full.copy(records = SectionState.Content(PeriodRecordsUi(RecordsThree.items.take(1), 1))),
            long,
        ),
        statsEntry(
            "one-session exercises",
            full.copy(
                progress = SectionState.Content(oneSession),
                topExercises = SectionState.Content(principals().takeLast(1)),
                records = noRecords,
            ),
            long,
        ),
        statsEntry("progress failed", full.copy(progress = SectionState.Error, records = SectionState.Error), long),
        statsEntry("large font (1.3)", full, StatsLook(fontScale = 1.3f)),
        statsEntry("large font (2.0)", full, StatsLook(fontScale = 2f)),
        statsEntry("tablet", full, StatsLook(wide = true)),
    ) + statsFailureAndSheets(full)
}

private fun statsFailureAndSheets(full: TrainingStatsUiState): List<GalleryEntry> {
    val bench = StatsExercise("bench", BENCH, 9.0, 3, imageKey = "barbell-bench-press")
    return listOf(
        statsEntry(
            "every section failed",
            TrainingStatsUiState(
                summary = SectionState.Error,
                progress = SectionState.Error,
                records = SectionState.Error,
                setCounts = SectionState.Error,
                muscleSets = SectionState.Error,
                topExercises = SectionState.Error,
                radar = SectionState.Error,
                bodyMap = SectionState.Error,
            ),
        ),
        statsEntry(
            "muscle detail sheet",
            full.copy(
                openMuscle = MuscleGroup.Chest,
                muscleDetail = SectionState.Content(MuscleDetail(MuscleGroup.Chest, 3.0, listOf(bench))),
            ),
            StatsLook(kind = GalleryKind.Sheet, covers = setOf("MuscleDetailSheet")),
        ),
        statsEntry(
            "muscle detail sheet, not trained",
            full.copy(
                openMuscle = MuscleGroup.Quads,
                muscleDetail = SectionState.Content(MuscleDetail(MuscleGroup.Quads, 0.0, emptyList())),
            ),
            StatsLook(kind = GalleryKind.Sheet),
        ),
    )
}
