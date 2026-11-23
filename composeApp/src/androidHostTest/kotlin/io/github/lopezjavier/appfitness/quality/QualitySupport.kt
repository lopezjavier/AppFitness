package io.github.lopezjavier.appfitness.quality

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleSetsHistory
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.feature.activity.ActivityUiState
import io.github.lopezjavier.appfitness.feature.activity.activityContent
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeUiState
import io.github.lopezjavier.appfitness.feature.analytics.stats.MuscleRadar
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodExerciseUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodProgressUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodRecordUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.PeriodRecordsUi
import io.github.lopezjavier.appfitness.feature.analytics.stats.SectionState
import io.github.lopezjavier.appfitness.feature.analytics.stats.StatsExercise
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsUiState
import io.github.lopezjavier.appfitness.feature.diary.DiaryContent
import io.github.lopezjavier.appfitness.feature.diary.DiaryGoalsState
import io.github.lopezjavier.appfitness.feature.diary.DiaryUiState
import io.github.lopezjavier.appfitness.feature.diary.MealSectionUi
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncStatus
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncUiState
import io.github.lopezjavier.appfitness.feature.weight.WeightViewModel
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import io.github.lopezjavier.appfitness.feature.workout.LiveExercise
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionUiState
import io.github.lopezjavier.appfitness.feature.workout.LiveSetRow
import io.github.lopezjavier.appfitness.feature.workout.SESSION_START
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import kotlin.time.Instant

/** The accessibility font scale the quality sweep checks (US-46: 130 %). */
internal const val QUALITY_FONT_SCALE = 1.3f

/** Runs [content] under [AppTheme] with the system font scale set to [fontScale]. */
@Composable
internal fun WithFontScale(fontScale: Float = QUALITY_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        AppTheme { content() }
    }
}

/** Asserts the node lies fully inside the window horizontally: nothing is pushed or clipped off screen. */
internal fun ComposeContentTestRule.assertWithinWindowWidth(tag: String) {
    val rootWidth = onRoot().getUnclippedBoundsInRoot().width
    val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
    assertTrue("$tag left ${bounds.left} is off screen", bounds.left.value >= 0f)
    assertTrue("$tag right ${bounds.right} is wider than the window $rootWidth", bounds.right <= rootWidth)
}

internal fun SemanticsNodeInteraction.widthDp() = getUnclippedBoundsInRoot().width

private val Today = LocalDate(2026, 10, 21)

private object QualityClock : Clock {
    override fun today(): LocalDate = Today

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

internal fun qualityDiaryState(): DiaryUiState {
    val slot = MealSlot("breakfast", MealCode.Breakfast, "Desayuno", 0)
    val content = DiaryContent(
        date = Today,
        today = Today,
        label = DateLabel.Today,
        canGoNext = false,
        goals = DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = 560),
        meals = listOf(MealSectionUi(slot, emptyList(), MealTotals(0.0, 0.0, 0.0, 0.0), true, true, 560)),
        dayTotals = MealTotals(1840.0, 142.0, 210.0, 48.0),
        collapsedSlotIds = emptySet(),
        defaultsFailed = false,
        isToday = true,
    )
    return DiaryUiState.Content(content)
}

internal fun qualityWeightModel(): WeightViewModel {
    val repository = FakeWeightRepository(
        BodyMeasurement("m1", Instant.parse("2026-10-21T08:00:00Z"), 82.4, MeasurementSource.Manual, 18.5),
    )
    return WeightViewModel(
        repository = repository,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(repository, QualityClock, TimeZone.UTC),
        validate = ValidateBodyMeasurement(),
        connectivity = FakeConnectivityObserver(),
        clock = QualityClock,
        timeZone = TimeZone.UTC,
        savedState = SavedStateHandle(),
    )
}

internal fun qualityLiveState(): LiveSessionUiState.Active {
    val exercise = LiveExercise(
        0,
        "e1",
        "Press banca",
        null,
        listOf(LiveSetRow(1, "100.5", "12", false), LiveSetRow(2, "", "", false)),
    )
    val state = LiveSessionUiState.Active("Torso", SESSION_START, listOf(exercise))
    return state
}

internal fun qualityActivityState(): ActivityUiState.Content = activityContent()

internal fun qualityMuscleSetsState(): MuscleVolumeUiState.Content {
    val weeks = (0..7).map { LocalDate.fromEpochDays(LocalDate(2026, 9, 7).toEpochDays() + 7 * it) }
    val chest = listOf(0.0, 8.0, 0.0, 12.5, 14.0, 10.0, 16.0, 18.5)
    return MuscleVolumeUiState.Content(MuscleSetsHistory(weeks, mapOf(MuscleGroup.Chest to chest)), null)
}

internal fun qualityStatsState(): TrainingStatsUiState {
    val groups = MuscleGroup.entries.map { MuscleGroupSets(it, if (it == MuscleGroup.Chest) 3.0 else 0.0) }
    val state = TrainingStatsUiState(
        summary = SectionState.Content(
            TrainingSummary(workouts = 24, sets = 412, volume = 0.0, durationSeconds = 0),
        ),
        progress = SectionState.Content(
            PeriodProgressUi(
                listOf(PeriodExerciseUi("bench", "Press banca", true, 3, ProgressGlyph.Up, 95.0, 101.0, 6.3)),
            ),
        ),
        records = SectionState.Content(
            PeriodRecordsUi(listOf(PeriodRecordUi("bench", "Press banca", true, 101.0, 95.0)), 1),
        ),
        setCounts = SectionState.Content(listOf(SetCountPoint(LocalDate(2026, 10, 5), 412))),
        muscleSets = SectionState.Content(groups),
        topExercises = SectionState.Content(
            listOf(StatsExercise("bench", "Press banca", 36.0, 3, changeKg = 6.0, compared = true)),
        ),
        radar = SectionState.Content(MuscleRadar(groups, null)),
        bodyMap = SectionState.Content(groups.filter { it.muscleGroup != MuscleGroup.FullBody }),
    )
    return state
}

internal fun qualityHealthSync(): HealthSyncUiState = HealthSyncUiState(HealthSyncStatus.NeverSynced)
