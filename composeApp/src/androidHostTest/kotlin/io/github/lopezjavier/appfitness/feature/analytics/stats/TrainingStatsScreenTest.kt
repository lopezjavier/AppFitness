package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.trainer.assertTextContainsIgnoringNbsp
import io.github.lopezjavier.appfitness.feature.trainer.hasTextIgnoringNbsp
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun groups(vararg sets: Pair<MuscleGroup, Double>): List<MuscleGroupSets> =
    MuscleGroup.entries.map { g -> MuscleGroupSets(g, sets.toMap()[g] ?: 0.0) }

private val Summary = TrainingSummary(workouts = 4, sets = 1240, volume = 12_500.0, durationSeconds = 5_400)
private val Weeks = listOf(
    SetCountPoint(LocalDate(2026, 9, 28), 10),
    SetCountPoint(LocalDate(2026, 10, 5), 24),
    SetCountPoint(LocalDate(2026, 10, 12), 0),
)
private val Bench = StatsExercise(
    "bench",
    "Press banca",
    9.0,
    3,
    imageKey = "barbell-bench-press",
    changeKg = 5.0,
    compared = true,
)
private val Custom = StatsExercise("custom-1", "Dominadas lastradas", 4.0, 2)
private val Squat = StatsExercise("squat", "Sentadilla", 6.0, 1)

private fun lift(id: String, name: String, state: ProgressGlyph, pct: Double? = null, last: Double? = null) =
    PeriodExerciseUi(id, name, false, 4, state, 90.0, last, pct)

/** 11 compared exercises, 8 of them up. */
private val Progress = PeriodProgressUi(
    listOf(
        PeriodExerciseUi("bench", "Press banca", true, 11, ProgressGlyph.Up, 95.0, 101.0, 6.0),
        PeriodExerciseUi("row", "Remo con barra", true, 9, ProgressGlyph.Same, 82.5, 84.0, 1.8),
        PeriodExerciseUi("ohp", "Press militar", true, 7, ProgressGlyph.Down, 62.0, 58.0, -6.5),
        lift("u1", "Curl", ProgressGlyph.Up, 3.0, 93.0),
        lift("u2", "Fondos", ProgressGlyph.Up, 3.0, 93.0),
        lift("u3", "Remo mancuerna", ProgressGlyph.Up, 3.0, 93.0),
        lift("u4", "Press inclinado", ProgressGlyph.Up, 3.0, 93.0),
        lift("u5", "Elevaciones", ProgressGlyph.Up, 3.0, 93.0),
        lift("u6", "Zancadas", ProgressGlyph.Up, 3.0, 93.0),
        lift("u7", "Hip thrust", ProgressGlyph.Up, 3.0, 93.0),
        lift("d1", "Dominadas", ProgressGlyph.Down, -3.0, 87.0),
    ),
)
private val Records = PeriodRecordsUi(
    listOf(
        PeriodRecordUi("bench", "Press banca", true, 101.0, 95.0),
        PeriodRecordUi("squat", "Sentadilla", true, 120.0, 110.0),
        PeriodRecordUi("curl", "Curl", false, 28.0, 25.0),
    ),
    total = 5,
)

private fun content(range: ProgressRange = ProgressRange.OneMonth) = TrainingStatsUiState(
    range = range,
    summary = SectionState.Content(Summary),
    progress = SectionState.Content(Progress),
    records = SectionState.Content(Records),
    setCounts = SectionState.Content(Weeks),
    muscleSets = SectionState.Content(groups(MuscleGroup.Chest to 3.0, MuscleGroup.Triceps to 1.5)),
    topExercises = SectionState.Content(listOf(Bench, Squat)),
    radar = SectionState.Content(
        MuscleRadar(
            current = groups(MuscleGroup.Chest to 3.0, MuscleGroup.Triceps to 1.5),
            previous = if (range == ProgressRange.AllTime) null else groups(MuscleGroup.Chest to 1.0),
        ),
    ),
    bodyMap = SectionState.Content(
        groups(MuscleGroup.Chest to 3.0, MuscleGroup.Triceps to 1.5).filter { it.muscleGroup != MuscleGroup.FullBody },
    ),
)

/** US-40 and US-41: the "Estadisticas" screen rendered from its state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainingStatsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainingStatsEvent>()
    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun show(state: TrainingStatsUiState) {
        rule.setContent {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                AppTheme { TrainingStatsScreen(state, { events += it }, { opened += it }, { backs++ }) }
            }
        }
        rule.waitForIdle()
    }

    private fun assertRow(tag: String, expected: String) {
        val description = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(expected, description)
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag(tag).performScrollTo()

    @Test
    fun timeRanges_defaultIsOneMonth_andPickingTodoSendsTheEvent() {
        show(content())

        rule.onNodeWithTag("stats_range_month").assertIsSelected()
        rule.onNodeWithTag("stats_range_all").performClick()

        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.SelectRange(ProgressRange.AllTime)), events)
    }

    @Test
    fun contextLine_showsWorkoutsAndSetsInOneLine_withNoVolumeOrDuration() {
        show(content(ProgressRange.AllTime))

        rule.onNodeWithTag("stats_range_all").assertIsSelected()
        rule.onNodeWithTag("stats_context_line").assertTextContains("4 entrenos · 1.240 series")
        rule.onNodeWithText("Volumen", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Duración", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Kilos por grupo muscular", substring = true).assertDoesNotExist()
    }

    @Test
    fun setCountChart_summarisesTotalAndPeakInSemantics() {
        show(content())

        scrollTo("stats_set_chart")
        val description = rule.onNodeWithTag("stats_set_chart").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertEquals("34 series en el periodo, máximo 24 series en 5 oct", description)
        rule.onNodeWithText("Series por semana").assertExists()
    }

    @Test
    fun setCountChart_isMonthlyForOneYear() {
        show(content(ProgressRange.OneYear))

        rule.onNodeWithText("Series por mes").assertExists()
    }

    @Test
    fun setCountChart_withNoSets_showsTheZeroText() {
        show(content().copy(setCounts = SectionState.Empty))

        scrollTo("stats_set_chart_empty")
        rule.onNodeWithTag("stats_set_chart_empty").assertTextEquals("Sin series en este periodo")
    }

    @Test
    fun setsPerMuscleGroup_listsEveryGroupWithDecimalComma() {
        show(content())

        scrollTo("stats_muscle_row_triceps")
        assertRow("stats_muscle_row_triceps", "Tríceps: 1,5 series")
        assertRow("stats_muscle_row_chest", "Pecho: 3 series")
        assertRow("stats_muscle_row_quads", "Cuádriceps: 0 series")
        scrollTo("stats_muscle_row_adductors")
        assertRow("stats_muscle_row_adductors", "Aductores: 0 series")
        rule.onNodeWithTag("stats_muscle_row_full_body").assertExists()
    }

    @Test
    fun principalExercises_rowShowsTheChangeAndSessions_andClickOpensTheExercise() {
        show(content())

        scrollTo("stats_principal_bench")
        rule.onNodeWithTag("stats_principal_bench").assertHeightIsAtLeast(48.dp)
            .assertTextContains("Press banca").assertTextContainsIgnoringNbsp("+5 kg · 3 entrenos")
        rule.onNodeWithTag("stats_principal_squat")
            .assertTextContains("Sentadilla").assertTextContains("Sin comparar · 1 entreno")
        rule.onNodeWithTag("stats_principal_bench").performClick()

        assertEquals(listOf("bench"), opened)
    }

    @Test
    fun principalExercises_rowsShowTheirPicture_orThePlaceholderWhenCustom() {
        show(content().copy(topExercises = SectionState.Content(listOf(Bench, Custom))))

        scrollTo("stats_principal_bench")
        rule.onNodeWithTag("stats_principal_bench_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("stats_principal_custom-1_thumbnail_image", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("stats_principal_custom-1_thumbnail_placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun muscleSheet_rowsShowTheSameThumbnails() {
        val detail = MuscleDetail(MuscleGroup.Chest, 3.0, listOf(Bench, Custom))
        show(content().copy(openMuscle = MuscleGroup.Chest, muscleDetail = SectionState.Content(detail)))

        rule.onNodeWithTag("stats_muscle_sheet_exercise_bench_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("stats_muscle_sheet_exercise_custom-1_thumbnail_placeholder", useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithTag("stats_muscle_sheet_exercise_bench").assertTextContains("3 entrenos", substring = true)
    }

    @Test
    fun principalExercises_rowsStayReadable_atFontScale2() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
                LocalExerciseMediaUris provides FakeExerciseMediaUris,
            ) {
                AppTheme { TrainingStatsScreen(content(), {}, {}, {}) }
            }
        }

        scrollTo("stats_principal_bench")
        rule.onNodeWithTag("stats_principal_bench").assertIsDisplayed()
            .assertTextContains("3 entrenos", substring = true)
    }

    @Test
    fun radar_legendNamesBothSeries_andOmitsThePreviousForTodo() {
        show(content())
        scrollTo("stats_radar")
        rule.onNodeWithTag("stats_radar_legend_current").assertTextEquals("Actual")
        rule.onNodeWithTag("stats_radar_legend_previous").assertTextEquals("Periodo anterior")
    }

    @Test
    fun radar_withoutPreviousShowsOnlyActual() {
        show(content(ProgressRange.AllTime))

        scrollTo("stats_radar")
        rule.onNodeWithTag("stats_radar_legend_current").assertExists()
        rule.onNodeWithTag("stats_radar_legend_previous").assertDoesNotExist()
        val description = rule.onNodeWithTag("stats_radar").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertEquals(true, description.contains("Tríceps 1,5 series"))
        assertEquals(true, description.contains("Cuerpo completo 0 series"))
    }

    @Test
    fun radar_axisUsesTheShortHamstringsLabel() {
        show(content())

        scrollTo("stats_radar")
        val description = rule.onNodeWithTag("stats_radar").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertEquals(true, description.contains("Isquios 0 series"))
        assertEquals(false, description.contains("Isquiotibiales"))
    }

    @Test
    fun radar_previousWindowFailed_keepsActualAndExplainsTheMissingPeriod() {
        val radar = MuscleRadar(groups(MuscleGroup.Chest to 3.0), previous = null, previousFailed = true)
        show(content().copy(radar = SectionState.Content(radar)))

        scrollTo("stats_radar")
        rule.onNodeWithTag("stats_radar_legend_current").assertTextEquals("Actual")
        rule.onNodeWithTag("stats_radar_legend_previous").assertDoesNotExist()
        rule.onNodeWithTag("stats_radar_previous_failed")
            .assertTextEquals("No se pudo cargar el periodo anterior.")
    }

    @Test
    fun radar_withPreviousLoaded_showsNoFailureHint() {
        show(content())

        scrollTo("stats_radar")
        rule.onNodeWithTag("stats_radar_previous_failed").assertDoesNotExist()
    }

    @Test
    fun title_isEstadisticasInTheBar_andBackLeaves() {
        show(content())

        rule.onNodeWithTag("stats_title").assertTextEquals("Entrenos")
        rule.onNodeWithTag("stats_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun rangeSelector_showsTheFiveRanges_andSelectingEmitsTheEvent() {
        show(content())

        listOf("1M" to "month", "3M" to "threemonths", "6M" to "sixmonths", "1A" to "year", "Todo" to "all")
            .forEach { (label, name) -> rule.onNodeWithTag("stats_range_$name").assertTextEquals(label) }
        rule.onNodeWithTag("stats_range_sixmonths").performClick()

        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.SelectRange(ProgressRange.SixMonths)), events)
    }

    @Test
    fun radar_withEveryValueZero_showsTheEmptyText() {
        val zero = MuscleRadar(groups(), groups())
        show(content().copy(radar = SectionState.Content(zero)))

        scrollTo("stats_radar_empty")
        rule.onNodeWithTag("stats_radar_empty").assertTextEquals("Sin series en este periodo")
    }

    @Test
    fun bodyHeatmap_showsBothSilhouettesRegionsAndTheVisibleList() {
        show(content())

        scrollTo("stats_body_front")
        rule.onNodeWithTag("stats_body_back").assertExists()
        rule.onNodeWithTag("stats_body_muscle_chest").assertContentDescriptionEquals("Pecho, 3 series")
        rule.onNodeWithTag("stats_body_muscle_triceps").assertContentDescriptionEquals("Tríceps, 1,5 series")
        rule.onNodeWithTag("stats_body_muscle_quads").assertContentDescriptionEquals("Cuádriceps, 0 series")
        rule.onNodeWithTag("stats_body_muscle_full_body").assertDoesNotExist()
        rule.onNodeWithTag("stats_body_list_full_body").assertDoesNotExist()
        rule.onNodeWithTag("stats_body_legend").assertExists()
        rule.onNodeWithTag("stats_body_list_chest", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun bodyHeatmap_captionsNameFrontAndBack() {
        show(content())

        scrollTo("stats_body_front_caption")
        rule.onNodeWithTag("stats_body_front_caption").assertTextEquals("Frente")
        rule.onNodeWithTag("stats_body_back_caption").assertTextEquals("Espalda")
    }

    @Test
    fun bodyHeatmap_withNothingTrained_explainsIt_andStaysTappable() {
        show(content().copy(bodyMap = SectionState.Content(groups().filter { it.muscleGroup != MuscleGroup.FullBody })))

        scrollTo("stats_body_empty")
        rule.onNodeWithTag("stats_body_empty").assertTextEquals("No has entrenado en los últimos 7 días")
        rule.onNodeWithTag("stats_body_muscle_quads").performClick()
        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.OpenMuscle(MuscleGroup.Quads)), events)
    }

    @Test
    fun tapForDetail_regionAndListRowOpenTheMuscle() {
        show(content())

        scrollTo("stats_body_muscle_triceps")
        rule.onNodeWithTag("stats_body_muscle_triceps").performClick()
        scrollTo("stats_body_list_chest")
        rule.onNodeWithTag("stats_body_list_chest").performClick()

        assertEquals(
            listOf<TrainingStatsEvent>(
                TrainingStatsEvent.OpenMuscle(MuscleGroup.Triceps),
                TrainingStatsEvent.OpenMuscle(MuscleGroup.Chest),
            ),
            events,
        )
    }

    @Test
    fun tapForDetail_sheetShowsSetsAndExercises_andCloses() {
        val detail = MuscleDetail(MuscleGroup.Triceps, 1.5, listOf(Bench))
        show(content().copy(openMuscle = MuscleGroup.Triceps, muscleDetail = SectionState.Content(detail)))

        rule.onNodeWithTag("stats_muscle_sheet").assertExists()
        rule.onNodeWithTag("stats_muscle_sheet_title").assertTextEquals("Tríceps")
        rule.onNodeWithTag("stats_muscle_sheet_subtitle").assertTextEquals("Últimos 7 días · 1,5 series")
        rule.onNodeWithTag("stats_muscle_sheet_exercise_bench").assertExists()
        rule.onNodeWithTag("stats_muscle_sheet_close").assertHeightIsAtLeast(48.dp).performClick()

        rule.waitUntil(timeoutMillis = 5_000) { events.isNotEmpty() }
        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.CloseMuscle), events)
    }

    @Test
    fun tapForDetail_untrainedMuscleShowsZeroSetsAndNoList() {
        val detail = MuscleDetail(MuscleGroup.Quads, 0.0, emptyList())
        show(content().copy(openMuscle = MuscleGroup.Quads, muscleDetail = SectionState.Content(detail)))

        rule.onNodeWithTag("stats_muscle_sheet_subtitle").assertTextEquals("Últimos 7 días · 0 series")
        rule.onNodeWithTag("stats_muscle_sheet_empty")
            .assertTextEquals("No has entrenado este músculo en los últimos 7 días.")
    }

    @Test
    fun tapForDetail_sheetExerciseOpensItsProgress() {
        val detail = MuscleDetail(MuscleGroup.Chest, 3.0, listOf(Bench))
        show(content().copy(openMuscle = MuscleGroup.Chest, muscleDetail = SectionState.Content(detail)))

        rule.onNodeWithTag("stats_muscle_sheet_exercise_bench").performClick()

        assertEquals(listOf("bench"), opened)
    }

    @Test
    fun loading_showsASkeletonPerSection() {
        show(TrainingStatsUiState())

        listOf("progress", "records", "summary", "chart", "muscles", "exercises", "radar", "body").forEach {
            scrollTo("stats_skeleton_$it")
            rule.onNodeWithTag("stats_skeleton_$it").assertExists()
        }
    }

    @Test
    fun error_isPerSection_withRetry_andLeavesTheRestIntact() {
        show(content().copy(setCounts = SectionState.Error))

        scrollTo("stats_retry_chart")
        rule.onNodeWithText("No se han podido cargar las estadísticas.").assertExists()
        rule.onNodeWithTag("stats_retry_chart").performClick()
        rule.onNodeWithTag("stats_context_line").assertExists()
        rule.onNodeWithTag("stats_retry_summary").assertDoesNotExist()

        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.Retry(StatsSection.Chart)), events)
    }

    @Test
    fun emptyFrame_showsTheEmptyStateAndKeepsTheChipsAndTheBodyMap() {
        show(content().copy(summary = SectionState.Empty))

        rule.onNodeWithTag("stats_empty_title").assertTextEquals("Sin entrenos en este periodo")
        rule.onNodeWithTag("stats_empty_message")
            .assertTextEquals("Registra un entreno o prueba con otro periodo.")
        rule.onNodeWithTag("stats_range_threemonths").performClick()
        rule.onNodeWithTag("stats_set_chart").assertDoesNotExist()
        scrollTo("stats_body_front")
        rule.onNodeWithTag("stats_body_front").assertExists()
        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.SelectRange(ProgressRange.ThreeMonths)), events)
    }

    @Test
    fun allSectionsFailed_showOneErrorWithoutTheRangeSelector_andRetryReloadsEverything() {
        show(
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
        )

        rule.onNodeWithTag("stats_range_month").assertDoesNotExist()
        rule.onNodeWithTag("stats_retry_chart").assertDoesNotExist()
        rule.onNodeWithTag("stats_error_action").assertTextEquals("Reintentar").performClick()

        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.Retry()), events)
    }

    @Test
    fun fullyEmpty_hidesTheBodyMap_andShowsOnlyTheEmptyState() {
        val noSets = SectionState.Content(groups().filter { it.muscleGroup != MuscleGroup.FullBody })
        show(content().copy(summary = SectionState.Empty, bodyMap = noSets))

        rule.onNodeWithTag("stats_empty_title").assertExists()
        rule.onNodeWithTag("stats_body_front").assertDoesNotExist()
        rule.onNodeWithTag("stats_body_empty").assertDoesNotExist()
    }

    @Test
    fun setsGroupsCard_titleRowIsTheLinkToTheSetsScreen() {
        var opens = 0
        rule.setContent {
            AppTheme { TrainingStatsScreen(content(), {}, {}, {}, onOpenMuscleVolume = { opens++ }) }
        }
        scrollTo("stats_sets_groups_link")

        rule.onNodeWithTag("stats_sets_groups_link").assertTextContains("Series por grupo muscular")
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(1, opens)
    }

    @Test
    fun progressCard_isOnTopWithTheSummaryAndTheStateOfEachExercise() {
        show(content())

        rule.onNode(hasTextIgnoringNbsp("8 de 11"), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("ejercicios suben", substring = true), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("1RM estimado").assertExists()
        scrollTo("stats_progress_row_bench")
        rule.onNodeWithTag("stats_progress_row_bench").assertTextContainsIgnoringNbsp("95 → 101 kg")
        rule.onNode(hasTextIgnoringNbsp("Sube +6 %"), useUnmergedTree = true).assertExists()
        scrollTo("stats_progress_row_row")
        rule.onNodeWithTag("stats_progress_row_row").assertTextContains("Dentro del margen de un día a otro")
        scrollTo("stats_progress_row_ohp")
        rule.onNode(hasTextIgnoringNbsp("Baja −6"), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Igual").assertDoesNotExist()
    }

    @Test
    fun progressCard_mainLiftsComeFirst_andARowOpensTheExercise() {
        show(content())

        scrollTo("stats_progress_row_ohp")
        val bench = rule.onNodeWithTag("stats_progress_row_bench").getUnclippedBoundsInRoot()
        val curl = rule.onNodeWithTag("stats_progress_row_u1").getUnclippedBoundsInRoot()
        assertTrue(bench.top < curl.top)
        rule.onNodeWithTag("stats_progress_row_ohp").performClick()

        assertEquals(listOf("ohp"), opened)
    }

    @Test
    fun progressCard_aSingleSessionExerciseReadsSinComparar() {
        val single = PeriodExerciseUi("face", "Face pull", false, 1, ProgressGlyph.Uncompared, 30.0)
        show(content().copy(progress = SectionState.Content(PeriodProgressUi(listOf(single)))))

        rule.onNodeWithTag("stats_progress_row_face").assertTextContains("Sin comparar", substring = true)
        rule.onNodeWithTag("stats_progress_none").assertExists()
    }

    @Test
    fun progressCard_withNothingToCompare_explainsWhenItWillShow() {
        show(content().copy(progress = SectionState.Empty))

        rule.onNodeWithTag("stats_progress_none")
            .assertTextEquals("Con dos entrenos de un ejercicio en este periodo, verás si sube.")
    }

    @Test
    fun recordsCard_showsThreeBadges_andTheTotalOnlyWhenThereAreMore() {
        show(content())

        scrollTo("stats_records_card")
        listOf(1, 2, 3).forEach { rule.onNodeWithTag("stats_record_$it").assertExists() }
        rule.onNodeWithTag("stats_record_1").assertTextContainsIgnoringNbsp("1RM estimado · antes 95 kg")
        rule.onNode(hasTextIgnoringNbsp("101 kg (+6 kg)"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("stats_records_total").assertTextContains("5 récords en este periodo")
    }

    @Test
    fun recordsCard_withExactlyThreeRecords_hasNoTotalLine() {
        show(content().copy(records = SectionState.Content(Records.copy(total = 3))))

        scrollTo("stats_records_card")
        rule.onNodeWithTag("stats_records_total").assertDoesNotExist()
    }

    @Test
    fun recordsCard_isAbsentWithNoRecords() {
        show(content().copy(records = SectionState.Empty))

        rule.onNodeWithTag("stats_records_card").assertDoesNotExist()
        rule.onNodeWithTag("stats_progress_card").assertExists()
    }

    @Test
    fun progressFailure_showsOneRetry_noRecordsCard_andTheRestStaysIntact() {
        show(content().copy(progress = SectionState.Error, records = SectionState.Error))

        rule.onNodeWithTag("stats_retry_progress").performClick()
        rule.onNodeWithTag("stats_retry_records").assertDoesNotExist()
        rule.onNodeWithTag("stats_records_card").assertDoesNotExist()
        rule.onNodeWithTag("stats_context_line").assertExists()

        assertEquals(listOf<TrainingStatsEvent>(TrainingStatsEvent.Retry(StatsSection.Progress)), events)
    }

    @Test
    fun recordsFailureAlone_hidesTheCardWithoutAnErrorCard() {
        show(content().copy(records = SectionState.Error))

        rule.onNodeWithTag("stats_records_card").assertDoesNotExist()
        rule.onNodeWithTag("stats_retry_records").assertDoesNotExist()
        rule.onNodeWithTag("stats_progress_row_bench").assertExists()
    }

    @Test
    fun distribution_staysBelowInOrder_progressRecordsContextChartGroupsPrincipalRadarBody() {
        show(content())

        val tops = listOf(
            "stats_progress_card",
            "stats_records_card",
            "stats_context_line",
            "stats_set_chart",
            "stats_sets_groups_link",
            "stats_principal_bench",
            "stats_radar",
            "stats_body_front",
        ).map { tag ->
            tag to rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        }

        assertEquals(tops.sortedBy { it.second }.map { it.first }, tops.map { it.first })
    }

    @Test
    fun noScreenTextSaysVolumenDuracionOrKilosPorGrupoMuscular() {
        show(content())

        listOf("Volumen", "Duración", "Kilos por grupo muscular").forEach {
            rule.onNodeWithText(it, substring = true, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun emptyRange_keepsTheEmptyStateAndHidesTheProgressCards() {
        show(content().copy(summary = SectionState.Empty))

        rule.onNodeWithTag("stats_empty_title").assertExists()
        rule.onNodeWithTag("stats_progress_card").assertDoesNotExist()
        rule.onNodeWithTag("stats_records_card").assertDoesNotExist()
    }

    @Test
    fun customExercise_isShownByItsName_andARenameIsShownWithoutRecreating() {
        var top by mutableStateOf(listOf(StatsExercise("custom-1", "Dominadas lastradas", 4.0, 2)))
        rule.setContent {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                AppTheme {
                    TrainingStatsScreen(content().copy(topExercises = SectionState.Content(top)), {}, {}, {})
                }
            }
        }
        scrollTo("stats_principal_custom-1")
        rule.onNodeWithText("Dominadas lastradas").assertExists()

        top = listOf(StatsExercise("custom-1", "Dominadas con lastre", 4.0, 2))
        rule.waitForIdle()

        rule.onNodeWithText("Dominadas con lastre").assertExists()
    }
}
