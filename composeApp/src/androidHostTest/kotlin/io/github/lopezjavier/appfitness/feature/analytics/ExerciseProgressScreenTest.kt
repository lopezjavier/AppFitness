package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.assertStateDescription
import io.github.lopezjavier.appfitness.domain.model.ChartPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun point(day: Int, value: Double, record: Boolean = false) =
    ChartPoint(LocalDate(2026, 10, day), value, record)

private fun content(
    progress: ExerciseProgress,
    range: ProgressRange = ProgressRange.ThreeMonths,
    name: String? = "Press de banca",
) = ExerciseProgressUiState.Content(name, range, progress)

private val TwoSessions = ExerciseProgress(
    ProgressMetric.OneRepMax,
    primary = listOf(point(1, 100.0), point(8, 107.5)),
    volume = listOf(point(1, 3_000.0), point(8, 3_400.0)),
)

// A single session shows no chart: the chart scroll state is not restorable in a host test.
private val OneSession = ExerciseProgress(ProgressMetric.OneRepMax, listOf(point(1, 100.0)), emptyList())

/** US-15 exercise progress: the 1RM and volume charts, the range filter and the not-enough-data states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class ExerciseProgressScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ExerciseProgressEvent>()
    private var backs = 0

    private fun show(state: ExerciseProgressUiState) {
        rule.setContent { AppTheme { ExerciseProgressScreen(state, { events += it }, { backs++ }) } }
        rule.waitForIdle()
    }

    @Test
    fun exerciseChart_showsTheOneRepMaxAndVolumeChartsForTheExercise() {
        show(content(TwoSessions))

        rule.onNodeWithText("Press de banca").assertExists()
        rule.onNodeWithTag("exercise_progress_chart_1rm").assertExists()
        rule.onNodeWithTag("exercise_progress_chart_volume").assertExists()
        rule.onNodeWithText("1RM estimado (kg)").assertExists()
        rule.onNodeWithText("Volumen (kg)").assertExists()
    }

    @Test
    fun charts_exposeASpanishSummaryAsStateDescription() {
        show(content(TwoSessions))

        rule.onNodeWithTag("exercise_progress_chart_1rm").assertStateDescription(
            "1RM estimado (kg). 2 entrenos, de 100,0 a 107,5.",
        )
        rule.onNodeWithTag("exercise_progress_chart_volume").assertStateDescription(
            "Volumen (kg). 2 entrenos, de 3.000 a 3.400.",
        )
        rule.onNodeWithTag("exercise_progress_record_legend").assertDoesNotExist()
    }

    @Test
    fun records_showTheLegendAndAreMentionedInTheSummary() {
        val progress = ExerciseProgress(
            ProgressMetric.OneRepMax,
            primary = listOf(point(1, 100.0, true), point(8, 98.0), point(15, 105.0, true)),
            volume = listOf(point(1, 3_000.0), point(8, 3_400.0, true), point(15, 3_300.0)),
        )
        show(content(progress))

        rule.onNodeWithTag("exercise_progress_record_legend").assertExists()
        rule.onAllNodesWithText("Récord").assertCountEquals(2)
        rule.onNodeWithTag("exercise_progress_record_legend_volume").assertExists()
        rule.onNodeWithTag("exercise_progress_chart_1rm").assertStateDescription(
            "1RM estimado (kg). 3 entrenos, de 100,0 a 105,0. 2 marcadas como récord personal en su fecha.",
        )
        rule.onNodeWithTag("exercise_progress_chart_volume").assertStateDescription(
            "Volumen (kg). 3 entrenos, de 3.000 a 3.300. 1 marcada como récord personal en su fecha.",
        )
    }

    @Test
    fun allTimeChip_isShownSelectedAndSendsTheRange() {
        show(content(TwoSessions, ProgressRange.AllTime))

        rule.onNodeWithTag("exercise_progress_range_all").assertIsSelected()
        rule.onNodeWithTag("exercise_progress_range_month").performClick()
        assertEquals(listOf<ExerciseProgressEvent>(ExerciseProgressEvent.SelectRange(ProgressRange.OneMonth)), events)
    }

    @Test
    fun allTimeChip_survivesRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { ExerciseProgressScreen(content(OneSession, ProgressRange.AllTime), { events += it }, {}) }
        }
        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("exercise_progress_range_all").assertIsSelected()
    }

    @Test
    fun plank_showsADurationChartWithoutOneRepMaxOrVolume() {
        show(
            content(
                ExerciseProgress(ProgressMetric.Duration, listOf(point(1, 45.0, true), point(8, 3_900.0)), emptyList()),
                name = "Plancha",
            ),
        )

        rule.onNodeWithTag("exercise_progress_chart_duration").assertStateDescription(
            "Duración. 2 entrenos, de 0:45 a 1:05. 1 marcada como récord personal en su fecha.",
        )
        rule.onNodeWithTag("exercise_progress_chart_1rm").assertDoesNotExist()
        rule.onNodeWithTag("exercise_progress_chart_volume").assertDoesNotExist()
    }

    @Test
    fun treadmill_showsDistanceWithADecimalComma() {
        show(
            content(
                ExerciseProgress(ProgressMetric.Distance, listOf(point(1, 800.0), point(8, 5_200.0)), emptyList()),
                name = "Cinta de correr",
            ),
        )

        rule.onNodeWithTag("exercise_progress_chart_distance").assertStateDescription(
            "Distancia. 2 entrenos, de 800 m a 5,2 km.",
        )
    }

    @Test
    fun steps_haveTheirOwnChart() {
        val twoPoints = listOf(point(1, 1_500.0), point(8, 2_000.0))
        show(content(ExerciseProgress(ProgressMetric.Steps, twoPoints, emptyList())))
        rule.onNodeWithTag(
            "exercise_progress_chart_steps",
        ).assertStateDescription("Pasos. 2 entrenos, de 1.500 a 2.000.")
    }

    @Test
    fun floors_haveTheirOwnChart() {
        val twoPoints = listOf(point(1, 10.0), point(8, 12.0))
        show(content(ExerciseProgress(ProgressMetric.Floors, twoPoints, emptyList())))
        rule.onNodeWithTag("exercise_progress_chart_floors").assertExists()
    }

    @Test
    fun repsOnlyExercise_showsARepsChart() {
        val twoPoints = listOf(point(1, 30.0), point(8, 35.0))
        show(content(ExerciseProgress(ProgressMetric.Reps, twoPoints, emptyList())))
        rule.onNodeWithTag(
            "exercise_progress_chart_reps",
        ).assertStateDescription("Repeticiones. 2 entrenos, de 30 a 35.")
    }

    @Test
    fun rangeFilters_markTheActiveRange() {
        show(content(TwoSessions, ProgressRange.SixMonths))

        rule.onNodeWithTag("exercise_progress_range_sixmonths").assertIsSelected()
    }

    @Test
    fun rangeFilters_sendTheChosenRange() {
        show(content(TwoSessions, ProgressRange.ThreeMonths))

        rule.onNodeWithTag("exercise_progress_range_month").performClick()
        rule.onNodeWithTag("exercise_progress_range_year").performClick()

        assertEquals(
            listOf<ExerciseProgressEvent>(
                ExerciseProgressEvent.SelectRange(ProgressRange.OneMonth),
                ExerciseProgressEvent.SelectRange(ProgressRange.OneYear),
            ),
            events,
        )
    }

    @Test
    fun singleSession_showsItsValueAndTheHintInsteadOfCharts() {
        show(content(ExerciseProgress(ProgressMetric.OneRepMax, listOf(point(1, 100.0)), listOf(point(1, 3_000.0)))))

        rule.onNodeWithText("Necesitas al menos 2 entrenos para ver la evolución").assertExists()
        rule.onNodeWithTag("exercise_progress_single_value").assertTextEquals("Último entreno: 100,0")
        rule.onNodeWithTag("exercise_progress_chart_1rm").assertDoesNotExist()
        rule.onNodeWithTag("exercise_progress_chart_volume").assertDoesNotExist()
    }

    @Test
    fun noSessionInTheRange_showsTheHint() {
        show(content(ExerciseProgress(ProgressMetric.OneRepMax, emptyList(), emptyList()), ProgressRange.OneMonth))

        rule.onNodeWithTag("exercise_progress_single_session").assertExists()
        rule.onNodeWithTag("exercise_progress_single_value").assertDoesNotExist()
    }

    @Test
    fun exerciseWithoutLoad_showsTheMaxRepsChartAndNoVolumeChart() {
        show(
            content(
                ExerciseProgress(ProgressMetric.MaxReps, listOf(point(1, 8.0), point(8, 10.0)), emptyList()),
                name = "Dominadas",
            ),
        )

        rule.onNodeWithText("Repeticiones máximas").assertExists()
        rule.onNodeWithTag("exercise_progress_chart_1rm").assertStateDescription(
            "Repeticiones máximas. 2 entrenos, de 8 a 10.",
        )
        rule.onNodeWithTag("exercise_progress_chart_volume").assertDoesNotExist()
    }

    @Test
    fun title_isTheExerciseNameInTheBar_andNotRepeatedInTheBody() {
        show(content(TwoSessions))

        rule.onNodeWithTag("exercise_progress_title").assertTextEquals("Press de banca")
        rule.onAllNodesWithText("Press de banca").assertCountEquals(1)
        rule.onNodeWithTag("exercise_progress_name").assertDoesNotExist()
    }

    @Test
    fun unnamedExercise_usesTheGenericTitle() {
        show(content(TwoSessions, name = null))

        rule.onNodeWithTag("exercise_progress_title").assertTextEquals("Progreso del ejercicio")
    }

    @Test
    fun backArrow_leavesTheScreen() {
        show(content(TwoSessions))

        rule.onNodeWithTag("exercise_progress_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun rangeSelector_showsTheFiveRanges() {
        show(content(TwoSessions))

        listOf("1M" to "month", "3M" to "threemonths", "6M" to "sixmonths", "1A" to "year", "Todo" to "all")
            .forEach { (label, name) ->
                rule.onNodeWithTag("exercise_progress_range_$name").assertTextEquals(label)
            }
    }

    @Test
    fun loading_showsChartSkeletons_andTheGenericTitle() {
        show(ExerciseProgressUiState.Loading)

        rule.onNodeWithTag("exercise_progress_loading").assertExists()
        rule.onNodeWithTag("exercise_progress_title").assertTextEquals("Progreso del ejercicio")
    }

    @Test
    fun empty_explainsTheExerciseHasNoSessions_withTheExerciseNameAsTitle() {
        show(ExerciseProgressUiState.Empty("Press de banca"))

        rule.onNodeWithText("Aún no has registrado este ejercicio en ningún entreno.").assertExists()
        rule.onNodeWithTag("exercise_progress_title").assertTextEquals("Press de banca")
    }

    @Test
    fun error_offersRetry() {
        show(ExerciseProgressUiState.Error)

        rule.onNodeWithTag("exercise_progress_error_action").assertTextEquals("Reintentar").performClick()

        assertEquals(listOf<ExerciseProgressEvent>(ExerciseProgressEvent.Retry), events)
    }
}
