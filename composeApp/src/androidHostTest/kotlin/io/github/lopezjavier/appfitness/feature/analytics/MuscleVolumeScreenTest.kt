package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleVolumeHistory
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val FirstWeek = LocalDate(2026, 9, 1)
private val Weeks = (0..7).map { LocalDate.fromEpochDays(FirstWeek.toEpochDays() + 7 * it) }

private val History = MuscleVolumeHistory(
    weeks = Weeks,
    series = mapOf(
        MuscleGroup.Chest to listOf(0.0, 1000.0, 0.0, 2500.0, 0.0, 0.0, 4260.0, 5400.0),
        MuscleGroup.Back to listOf(100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 300.0),
    ),
)

/** US-15 weekly volume per muscle group: 8 weeks per group, selecting a group, and the edge states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class MuscleVolumeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<MuscleVolumeEvent>()
    private var startedWorkout = 0

    private fun show(state: MuscleVolumeUiState) {
        rule.setContent {
            AppTheme {
                MuscleVolumeScreen(state, { events += it }, { startedWorkout++ })
            }
        }
        rule.waitForIdle()
    }

    private fun content(selected: MuscleGroup? = null) = MuscleVolumeUiState.Content(History, selected)

    @Test
    fun allGroups_showsOneCardPerGroupWithTheEightWeeks() {
        show(content())

        rule.onNodeWithTag("muscle_volume_card_chest").assertExists()
        rule.onNodeWithTag("muscle_volume_card_back").assertExists()
        rule.onNodeWithTag("muscle_volume_card_quads").assertDoesNotExist()
        for (i in 0..7) rule.onNodeWithTag("muscle_volume_week_chest_$i", useUnmergedTree = true).assertExists()
        for (i in 0..7) rule.onNodeWithTag("muscle_volume_week_back_$i", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_volume_week_chest_6", useUnmergedTree = true).assertTextEquals("4.260")
        rule.onNodeWithTag("muscle_volume_week_chest_0", useUnmergedTree = true).assertTextEquals("0")
        rule.onNodeWithTag("muscle_volume_current_chest", useUnmergedTree = true).assertTextEquals("5.400 kg")
    }

    @Test
    fun allGroups_chipTodosIsSelected_andNoChartIsShown() {
        show(content())

        rule.onNodeWithTag("muscle_volume_chip_all").assertIsSelected()
        rule.onNodeWithTag("muscle_volume_chip_chest").assertIsNotSelected()
        rule.onNodeWithTag("muscle_volume_chart").assertDoesNotExist()
    }

    @Test
    fun tappingAGroupChip_selectsThatGroup() {
        show(content())

        rule.onNodeWithTag("muscle_volume_chip_back").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back)), events)
    }

    @Test
    fun tappingTodosChip_clearsTheSelection() {
        show(content(MuscleGroup.Chest))

        rule.onNodeWithTag("muscle_volume_chip_all").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(null)), events)
    }

    @Test
    fun tappingAGroupCard_selectsThatGroup() {
        show(content())

        rule.onNodeWithTag("muscle_volume_card_chest").performClick()

        assertEquals(listOf<MuscleVolumeEvent>(MuscleVolumeEvent.SelectGroup(MuscleGroup.Chest)), events)
    }

    @Test
    fun selectedGroup_showsOnlyItsChartAndItsEightWeeks() {
        show(content(MuscleGroup.Chest))

        rule.onNodeWithTag("muscle_volume_chip_chest").assertIsSelected()
        rule.onNodeWithTag("muscle_volume_chart").assertExists()
        for (i in 0..7) rule.onNodeWithTag("muscle_volume_week_chest_$i", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("muscle_volume_week_back_0", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("muscle_volume_card_back").assertDoesNotExist()
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

        rule.onNodeWithText("Semanas de lunes a domingo · últimas 8").assertExists()
    }

    @Test
    fun empty_invitesToStartAWorkout() {
        show(MuscleVolumeUiState.Empty)

        rule.onNodeWithTag("muscle_volume_empty_action", useUnmergedTree = true).assertExists()
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

        rule.onNodeWithTag("muscle_volume_loading").assertExists()
    }
}
