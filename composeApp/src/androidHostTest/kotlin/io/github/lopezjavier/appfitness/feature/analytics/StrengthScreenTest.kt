package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Chips = listOf(
    StrengthChip("bench", "Press de banca", imageKey = "barbell-bench-press"),
    StrengthChip("squat", "Sentadilla", imageKey = "barbell-squat"),
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
    private var backs = 0

    private fun show(state: StrengthUiState) {
        rule.setContent {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                AppTheme {
                    StrengthScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenExercise = { opened += it },
                        onStartWorkout = { startedWorkout++ },
                        onBack = { backs++ },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun summaryCard_showsTheThreeTopExercisesWithTheMostFrequentSelected() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithText("1RM estimado").assertExists()
        rule.onNodeWithTag("strength_title").assertTextEquals("Fuerza")
        rule.onNodeWithTag("strength_row_bench").assertIsSelected()
        rule.onNodeWithTag("strength_row_squat").assertIsNotSelected()
        rule.onNodeWithTag("strength_row_row").assertExists()
    }

    @Test
    fun topExercises_areOneRowEach_withNoChipRow() {
        show(content(StrengthSummary(113.5, 5.0)))

        listOf("bench", "squat", "row").forEach { rule.onNodeWithTag("strength_row_$it").assertExists() }
        rule.onNodeWithTag("strength_row_bench").assertTextContains("Press de banca")
        rule.onNodeWithTag("strength_chip_row").assertDoesNotExist()
        rule.onNodeWithTag("strength_chip_bench").assertDoesNotExist()
    }

    @Test
    fun onlyTheSelectedRow_showsTheLimeCheck() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithTag("strength_row_bench_selected_check", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("strength_row_squat_selected_check", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("strength_row_row_selected_check", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aKeyedChip_showsItsPicture_andACustomOneThePlaceholder() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithTag("strength_row_bench_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("strength_row_row_thumbnail_image", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("strength_row_row_thumbnail_placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theRows_stayReadable_atFontScale2_withTheCheckNotTruncated() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalExerciseMediaUris provides FakeExerciseMediaUris,
            ) {
                AppTheme {
                    StrengthScreen(content(StrengthSummary(113.5, 5.0)), {}, {}, {}, {})
                }
            }
        }

        rule.onNodeWithTag("strength_row_bench").assertTextContains("Press de banca")
        rule.onNodeWithTag("strength_row_bench_selected_check", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("strength_row_row").assertIsDisplayed()
    }

    @Test
    fun title_isFuerzaInTheBar_andBackLeaves() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithTag("strength_back").performClick()

        assertEquals(1, backs)
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
    fun delta_insideTheMargin_explainsTheChange() {
        show(content(StrengthSummary(113.5, 1.5, withinMargin = true)))

        rule.onNodeWithTag("strength_delta")
            .assertTextEquals("+1,5 kg este mes · dentro del margen de un día a otro")
    }

    @Test
    fun delta_negativeInsideTheMargin_keepsTheSameTail() {
        show(content(StrengthSummary(113.5, -1.5, withinMargin = true)))

        rule.onNodeWithTag("strength_delta")
            .assertTextEquals("−1,5 kg este mes · dentro del margen de un día a otro")
    }

    @Test
    fun delta_outsideTheMargin_showsTheAmountOnly() {
        show(content(StrengthSummary(113.5, 5.0, withinMargin = false)))

        rule.onNodeWithTag("strength_delta").assertTextEquals("+5,0 kg este mes")
    }

    @Test
    fun delta_insideTheMargin_isFullyShownAtFontScale2() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalExerciseMediaUris provides FakeExerciseMediaUris,
            ) {
                AppTheme {
                    StrengthScreen(content(StrengthSummary(113.5, 1.5, withinMargin = true)), {}, {}, {}, {})
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("strength_delta").assertIsDisplayed()
            .assertTextContains("dentro del margen de un día a otro", substring = true)
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
    fun tappingARow_selectsThatExercise() {
        show(content(StrengthSummary(113.5, 5.0)))

        rule.onNodeWithTag("strength_row_squat").performClick()

        assertEquals(listOf<StrengthEvent>(StrengthEvent.SelectExercise("squat")), events)
    }

    @Test
    fun verProgreso_opensTheSelectedExercise() {
        show(content(StrengthSummary(90.0, null), selectedId = "squat"))

        rule.onNodeWithTag("strength_open_exercise").assertTextContains("Progreso del ejercicio").performClick()

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

    @Test
    fun customExercise_isShownByItsName_andARenameIsShownWithoutRecreating() {
        var chips by mutableStateOf(listOf(StrengthChip("custom-1", "Remo en anillas")))
        rule.setContent {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                AppTheme {
                    StrengthScreen(StrengthUiState.Content(chips, "custom-1", null), {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithTag("strength_row_custom-1").assertTextContains("Remo en anillas")

        chips = listOf(StrengthChip("custom-1", "Remo invertido"))
        rule.waitForIdle()

        rule.onNodeWithTag("strength_row_custom-1").assertTextContains("Remo invertido")
        rule.onNodeWithTag("strength_row_custom-1_thumbnail_placeholder", useUnmergedTree = true).assertExists()
    }
}
