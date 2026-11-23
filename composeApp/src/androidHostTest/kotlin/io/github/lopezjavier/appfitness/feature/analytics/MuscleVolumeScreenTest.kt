package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleSetsHistory
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val FirstWeek = LocalDate(2026, 9, 1)
private val Weeks = (0..7).map { LocalDate.fromEpochDays(FirstWeek.toEpochDays() + 7 * it) }

private val History = MuscleSetsHistory(
    weeks = Weeks,
    series = mapOf(
        MuscleGroup.Chest to listOf(0.0, 10.0, 0.0, 12.5, 0.0, 0.0, 14.5, 18.5),
        MuscleGroup.Back to listOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 3.0),
    ),
)

/** US-15, US-145 weekly sets per muscle group: 8 weeks per group, selecting a group, and the edge states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class MuscleVolumeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<MuscleVolumeEvent>()
    private var startedWorkout = 0
    private var backs = 0

    private fun show(state: MuscleVolumeUiState) {
        rule.setContent {
            AppTheme {
                MuscleVolumeScreen(state, { events += it }, { startedWorkout++ }, { backs++ })
            }
        }
        rule.waitForIdle()
    }

    private fun content(selected: MuscleGroup? = null) = MuscleVolumeUiState.Content(History, selected)

    @Test
    fun allGroups_showsOneCardPerGroupWithTheEightWeeks() {
        show(content())

        rule.onNodeWithTag("muscle_sets_group_chest").assertExists()
        rule.onNodeWithTag("muscle_sets_group_back").assertExists()
        rule.onNodeWithTag("muscle_sets_group_quads").assertDoesNotExist()
        rule.onNodeWithTag("muscle_sets_overview_chart_chest", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_sets_overview_chart_back", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_sets_week_chest_0", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("muscle_sets_current_chest", useUnmergedTree = true).assertTextEquals("18,5 series")
    }

    @Test
    fun singleGroup_showsTheEightWeeksOfThatGroup() {
        show(content(MuscleGroup.Chest))

        for (i in 0..7) rule.onNodeWithTag("muscle_sets_week_chest_$i", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_sets_week_chest_6", useUnmergedTree = true).assertTextEquals("14,5")
        rule.onNodeWithTag("muscle_sets_week_chest_0", useUnmergedTree = true).assertTextEquals("0")
    }

    @Test
    fun title_isSeriesPorGrupoMuscularInTheBar_andBackLeaves() {
        show(content())

        rule.onNodeWithTag("muscle_sets_title").assertTextEquals("Series por grupo muscular")
        rule.onNodeWithTag("muscle_sets_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun allGroups_chipTodosIsSelected_andNoChartIsShown() {
        show(content())

        rule.onNodeWithTag("muscle_sets_chip_all").assertIsSelected()
        rule.onNodeWithTag("muscle_sets_chip_chest").assertIsNotSelected()
        rule.onNodeWithTag("muscle_sets_chart").assertDoesNotExist()
    }

    @Test
    fun tappingAGroupChip_selectsThatGroup() {
        show(content())

        rule.onNodeWithTag("muscle_sets_chip_back").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back)), events)
    }

    @Test
    fun tappingTodosChip_clearsTheSelection() {
        show(content(MuscleGroup.Chest))

        rule.onNodeWithTag("muscle_sets_chip_all").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(null)), events)
    }

    @Test
    fun tappingAGroupCard_selectsThatGroup() {
        show(content())

        rule.onNodeWithTag("muscle_sets_group_chest").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(MuscleGroup.Chest)), events)
    }

    @Test
    fun selectedGroup_showsOnlyItsChartAndItsEightWeeks() {
        show(content(MuscleGroup.Chest))

        rule.onNodeWithTag("muscle_sets_chip_chest").assertIsSelected()
        rule.onNodeWithTag("muscle_sets_chart").assertExists()
        for (i in 0..7) rule.onNodeWithTag("muscle_sets_week_chest_$i", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_sets_week_back_0", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("muscle_sets_group_back").assertDoesNotExist()
    }

    @Test
    fun weekLabels_useTheMondayInSpanishShortFormat() {
        show(content(MuscleGroup.Chest))

        rule.onNodeWithText("1 sep", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("20 oct", useUnmergedTree = true).assertExists()
    }

    @Test
    fun caption_explainsTheWeeks() {
        show(content())

        rule.onNodeWithText("Últimas 8 semanas, de lunes a domingo.").assertExists()
    }

    @Test
    fun threeWeekHistory_showsOnlyThreeWeekCellsAndStillSelectsGroups() {
        val threeWeeks = MuscleSetsHistory(
            weeks = Weeks.takeLast(3),
            series = mapOf(
                MuscleGroup.Chest to listOf(10.0, 0.0, 18.5),
                MuscleGroup.Back to listOf(0.0, 0.0, 3.0),
            ),
        )
        show(MuscleVolumeUiState.Content(threeWeeks, null))

        rule.onNodeWithTag("muscle_sets_overview_chart_chest", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_sets_current_chest", useUnmergedTree = true).assertTextEquals("18,5 series")
        rule.onNodeWithTag("muscle_sets_chip_back").performClick()
        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back)), events)
    }

    @Test
    fun threeWeekHistory_singleGroupShowsOnlyThreeWeekCells() {
        val threeWeeks = MuscleSetsHistory(
            weeks = Weeks.takeLast(3),
            series = mapOf(MuscleGroup.Chest to listOf(10.0, 0.0, 18.5)),
        )
        show(MuscleVolumeUiState.Content(threeWeeks, MuscleGroup.Chest))

        for (i in 0..2) rule.onNodeWithTag("muscle_sets_week_chest_$i", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_sets_week_chest_3", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("muscle_sets_week_chest_1", useUnmergedTree = true).assertTextEquals("0")
    }

    @Test
    fun empty_invitesToStartAWorkout() {
        show(MuscleVolumeUiState.Empty)

        rule.onNodeWithTag("muscle_sets_empty_action", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Empezar entreno").performClick()
        assertEquals(1, startedWorkout)
    }

    @Test
    fun error_offersRetry() {
        show(MuscleVolumeUiState.Error)

        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.Retry), events)
    }

    @Test
    fun loading_showsSkeleton() {
        show(MuscleVolumeUiState.Loading)

        rule.onNodeWithTag("muscle_sets_loading").assertExists()
    }

    @Test
    fun groupCard_labelsTheCurrentWeekAsEstaSemana_withNoWeekOverWeekPill() {
        show(content())

        rule.onNodeWithTag("muscle_sets_group_chest").assertTextContains("Esta semana", substring = true)
        rule.onNodeWithText("vs. semana anterior", substring = true, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Última semana", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun everyFigure_isInSets_neverInKilos() {
        show(content())

        rule.onNodeWithTag("muscle_sets_overview_chart_chest", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("kg", substring = true, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Kilos por grupo muscular", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Volumen", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun singleGroup_isOneCard_withTheChartOfThatGroupOnly() {
        show(content(MuscleGroup.Chest))

        rule.onNodeWithTag("muscle_sets_chart").assertExists()
        rule.onNodeWithTag("muscle_sets_overview_chart_chest", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("muscle_sets_overview_chart_back", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun help_explainsTheHalfSetRule() {
        show(content())

        rule.onNodeWithTag("muscle_sets_help").assertHeightIsAtLeast(48.dp).performClick()

        rule.onNodeWithText(
            "Cada serie cuenta una serie para el músculo principal y media serie para cada músculo secundario.",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun error_endsWithAPeriod() {
        show(MuscleVolumeUiState.Error)

        rule.onNodeWithText("No se han podido cargar las series por grupo muscular.").assertExists()
    }
}
