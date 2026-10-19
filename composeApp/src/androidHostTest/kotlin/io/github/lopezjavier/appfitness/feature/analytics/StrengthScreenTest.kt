package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Chips = listOf(
    StrengthChip("bench", "Press de banca"),
    StrengthChip("squat", "Sentadilla"),
    StrengthChip("row", "Remo"),
)

private fun content(summary: StrengthSummary?, selectedId: String = "bench") =
    StrengthUiState.Content(Chips, selectedId, summary)

/** US-15 "Fuerza" card: top exercises, estimated 1RM record and monthly delta, plus its edge states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class StrengthScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<StrengthEvent>()
    private val opened = mutableListOf<String>()
    private var startedWorkout = 0

    private fun show(state: StrengthUiState) {
        rule.setContent {
            AppTheme {
                StrengthScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpenExercise = { opened += it },
                    onStartWorkout = { startedWorkout++ },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun summaryCard_showsTheThreeTopExercisesWithTheMostFrequentSelected() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithText("Fuerza y 1RM estimado").assertExists()
        rule.onNodeWithTag("strength_chip_bench").assertIsSelected()
        rule.onNodeWithTag("strength_chip_squat").assertIsNotSelected()
        rule.onNodeWithTag("strength_chip_row").assertExists()
    }

    @Test
    fun record_showsTheEstimatedOneRepMaxInKilosWithComma() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithTag("strength_record").assertTextEquals("Récord 1RM estimado: 113,5 kg")
    }

    @Test
    fun delta_growth_isShownWithPlusSign() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithText("+5,0 kg este mes").assertExists()
        rule.onNodeWithTag("strength_delta").assertExists()
    }

    @Test
    fun delta_zero_saysNoChange() {
        show(content(StrengthSummary(113.5, 0.0)))

        rule.onNodeWithText("Sin cambios este mes").assertExists()
    }

    @Test
    fun delta_negative_isShownWithMinusSign() {
        show(content(StrengthSummary(110.0, -2.5)))

        rule.onNodeWithText("−2,5 kg este mes").assertExists()
    }

    @Test
    fun delta_withoutPriorData_hidesThePill() {
        show(content(StrengthSummary(113.5, null)))

        rule.onNodeWithTag("strength_delta").assertDoesNotExist()
        rule.onNodeWithTag("strength_record").assertExists()
    }

    @Test
    fun record_missing_showsAMessageInstead() {
        show(content(StrengthSummary(null, null)))

        rule.onNodeWithTag("strength_record").assertTextEquals("Sin récord de 1RM todavía")
    }

    @Test
    fun summaryStillLoading_showsASkeletonInsteadOfTheRecord() {
        show(content(null))

        rule.onNodeWithTag("strength_summary_loading").assertExists()
        rule.onNodeWithTag("strength_record").assertDoesNotExist()
    }

    @Test
    fun tappingAChip_selectsThatExercise() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithTag("strength_chip_squat").performClick()

        assertEquals(listOf<StrengthEvent>(StrengthEvent.SelectExercise("squat")), events)
    }

    @Test
    fun verProgreso_opensTheSelectedExercise() {
        show(content(StrengthSummary(90.0, null), selectedId = "squat"))

        rule.onNodeWithTag("strength_open_exercise").assertTextEquals("Ver progreso").performClick()

        assertEquals(listOf("squat"), opened)
    }

    @Test
    fun empty_invitesToStartAWorkout() {
        show(StrengthUiState.Empty)

        rule.onNodeWithTag("strength_empty_action", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Empezar entreno").performClick()
        assertEquals(1, startedWorkout)
    }

    @Test
    fun error_offersRetry() {
        show(StrengthUiState.Error)

        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<StrengthEvent>(StrengthEvent.Retry), events)
    }

    @Test
    fun loading_showsSkeleton() {
        show(StrengthUiState.Loading)

        rule.onNodeWithTag("strength_loading").assertExists()
    }
}
