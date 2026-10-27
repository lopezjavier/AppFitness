package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
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
private val Bench = StatsExercise("bench", "Press banca", 9.0, 3)
private val Squat = StatsExercise("squat", null, 6.0, 1)

private fun content(range: ProgressRange = ProgressRange.OneMonth) = TrainingStatsUiState(
    range = range,
    summary = SectionState.Content(Summary),
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
            AppTheme { TrainingStatsScreen(state, { events += it }, { opened += it }, { backs++ }) }
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
    fun timeRanges_tilesShowTheKeyValuesOfTheFrame() {
        show(content(ProgressRange.AllTime))

        rule.onNodeWithTag("stats_range_all").assertIsSelected()
        rule.onNodeWithTag("stats_tile_workouts").assertTextContains("4", substring = true)
        rule.onNodeWithTag("stats_tile_sets").assertTextContains("1.240", substring = true)
        rule.onNodeWithTag("stats_tile_volume").assertTextContains("12.500", substring = true)
        rule.onNodeWithTag("stats_tile_duration").assertTextContains("1 h 30 min", substring = true)
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
        rule.onNodeWithTag("stats_muscle_row_full_body").assertExists()
    }

    @Test
    fun mainExercises_rowShowsSetsAndSessions_andClickOpensTheExercise() {
        show(content())

        scrollTo("stats_exercise_row_bench")
        rule.onNodeWithTag("stats_exercise_row_bench").assertHeightIsAtLeast(48.dp)
            .assertContentDescriptionEquals("Press banca, 9 series · 3 entrenos")
        rule.onNodeWithTag("stats_exercise_row_squat").assertContentDescriptionEquals("Ejercicio, 6 series · 1 entreno")
        rule.onNodeWithTag("stats_exercise_row_bench").performClick()

        assertEquals(listOf("bench"), opened)
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

        rule.onNodeWithTag("stats_title").assertTextEquals("Entrenamiento")
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

        listOf("summary", "chart", "muscles", "exercises", "radar", "body").forEach {
            scrollTo("stats_skeleton_$it")
            rule.onNodeWithTag("stats_skeleton_$it").assertExists()
        }
    }

    @Test
    fun error_isPerSection_withRetry_andLeavesTheRestIntact() {
        show(content().copy(setCounts = SectionState.Error))

        scrollTo("stats_retry_chart")
        rule.onNodeWithText("No se han podido cargar las estadísticas").assertExists()
        rule.onNodeWithTag("stats_retry_chart").performClick()
        rule.onNodeWithTag("stats_tile_workouts").assertExists()
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
}
