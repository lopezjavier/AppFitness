package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun rpeState(
    rpes: List<String?>,
    done: List<Boolean>,
    picker: RpePickerTarget? = null,
    target: String? = null,
    previous: Map<Int, String> = emptyMap(),
) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = listOf(
        LiveExercise(
            0,
            "e1",
            "Press banca",
            null,
            rpes.mapIndexed { i, r ->
                LiveSetRow(
                    i + 1,
                    "102,25",
                    "123",
                    completed = done[i],
                    type = if (i == 2) SetType.WarmUp else SetType.Working,
                    previous = previous[i + 1],
                    rpe = r,
                )
            },
            targetRpeText = target,
        ),
    ),
    isOnline = true,
    rpePicker = picker,
)

/** US-30 live part: logging the RPE, the previous RPE in the ghost and the target RPE line. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionRpeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showStateful(
        initial: List<String?> = listOf(null, null, null),
        target: String? = null,
        previous: Map<Int, String> = emptyMap(),
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, fontScale)) {
                AppTheme { StatefulRpeLive(initial, target, previous) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun rpeCell_hasA48DpTouchTarget_andStartsEmpty() {
        showStateful()

        rule.onNodeWithTag("live_set_0_2_rpe").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assertTextEquals("—")
    }

    @Test
    fun rpeHelp_opensItsExplanation_andClosesKeepingThePicker() {
        showStateful()

        rule.onNodeWithTag("live_set_0_2_rpe").performClick()
        rule.onNodeWithTag("live_rpe_help").performClick()
        rule.onNodeWithTag("live_rpe_help_sheet").assertExists()
        rule.onNodeWithTag("live_rpe_help_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_rpe_help_sheet").assertDoesNotExist()
        rule.onNodeWithTag("rpe_picker").assertExists()
    }

    @Test
    fun loggingTheRpe_pick7_5_showsItInTheCellAndClosesTheSheet() {
        showStateful()

        rule.onNodeWithTag("live_set_0_2_rpe").performClick()
        rule.onNodeWithTag("rpe_picker_option_7_5").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_set_0_2_rpe").assertTextEquals("7,5")
        rule.onNodeWithTag("rpe_picker").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_2_check").assertIsOff()
    }

    @Test
    fun changingTheRpeOfACompletedSet_pickUpdatesItAndKeepsItChecked() {
        showStateful(listOf("8", null, null))

        rule.onNodeWithTag("live_set_0_1_rpe").performClick()
        rule.onNodeWithTag("rpe_picker_option_8").assertIsSelected()
        rule.onNodeWithTag("rpe_picker_option_9").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_set_0_1_rpe").assertTextEquals("9")
        rule.onNodeWithTag("live_set_0_1_check").assertIsOn()
    }

    @Test
    fun checkingAWorkingSetWithoutRpe_opensTheScale_andPickingCompletesIt() {
        showStateful()

        rule.onNodeWithTag("live_set_0_2_check").performClick()
        rule.onNodeWithTag("rpe_picker").assertExists()
        rule.onNodeWithTag("live_set_0_2_check").assertIsOff()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rpe_picker").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_2_check").assertIsOn()
        rule.onNodeWithTag("live_set_0_2_rpe").assertTextEquals("8")
    }

    @Test
    fun closingThePromptWithoutPicking_leavesTheSetPending() {
        showStateful()

        rule.onNodeWithTag("live_set_0_2_check").performClick()
        rule.onNodeWithTag("rpe_picker_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rpe_picker").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_2_check").assertIsOff()
        rule.onNodeWithTag("live_set_0_2_rpe").assertTextEquals("—")
    }

    @Test
    fun checkingAWarmUpSet_completesWithoutPrompt() {
        showStateful()

        rule.onNodeWithTag("live_set_0_3_check").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rpe_picker").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_3_check").assertIsOn()
    }

    @Test
    fun checkingASetThatAlreadyHasRpe_completesWithoutPrompt() {
        showStateful(listOf("8", "7,5", null))

        rule.onNodeWithTag("live_set_0_2_check").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("rpe_picker").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_2_check").assertIsOn()
    }

    @Test
    fun previousRpe_ghostShowsRpe8OnSet2Only() {
        showStateful(previous = mapOf(2 to "85 kg × 8\nRPE 8", 1 to "80 kg × 10"))

        rule.onNodeWithTag("live_set_0_2_previous").assertTextEquals("85 kg × 8\nRPE 8")
        rule.onNodeWithTag("live_set_0_1_previous").assertTextEquals("80 kg × 10")
    }

    @Test
    fun targetRpe_isShownOncePerExercise() {
        showStateful(target = "8")

        rule.onNodeWithTag("live_exercise_0_target_rpe").assertTextEquals("Objetivo RPE 8")
    }

    @Test
    fun targetRpe_isAbsentWithoutTarget() {
        showStateful(target = null)

        rule.onNodeWithTag("live_exercise_0_target_rpe").assertDoesNotExist()
    }

    @Test
    fun openPrompt_survivesRecreation_andPickingThenCompletes() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { StatefulRpeLive(listOf(null, null, null), null, emptyMap()) } }
        rule.onNodeWithTag("live_set_0_2_check").performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("rpe_picker").assertExists()
        rule.onNodeWithTag("rpe_picker_option_7_5").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_set_0_2_rpe").assertTextEquals("7,5")
        rule.onNodeWithTag("live_set_0_2_check").assertIsOn()
    }

    @Test
    fun at360DpAnd130PercentFont_weightAndRepsAreNotClipped() {
        showStateful(
            listOf("7,5", null, null),
            target = "8",
            previous = mapOf(2 to "85 kg × 8\nRPE 8"),
            fontScale = 1.3f,
        )

        val weight = rule.onNodeWithTag("live_set_0_2_weight").getUnclippedBoundsInRoot()
        val reps = rule.onNodeWithTag("live_set_0_2_reps").getUnclippedBoundsInRoot()
        assertTrue("weight too narrow: ${weight.width}", weight.width >= 56.dp)
        assertTrue("reps too narrow: ${reps.width}", reps.width >= 36.dp)
        rule.onNodeWithTag("live_set_0_2_rpe").assertWidthIsAtLeast(48.dp)
    }
}

@Suppress("CyclomaticComplexMethod") // Test harness that mimics the view model's reaction to each event.
@androidx.compose.runtime.Composable
private fun StatefulRpeLive(initial: List<String?>, target: String?, previous: Map<Int, String>) {
    var rpes by rememberSaveable { mutableStateOf(initial) }
    var done by rememberSaveable { mutableStateOf(listOf(true, false, false)) }
    var picker by rememberSaveable { mutableStateOf<Triple<Int, Int, Boolean>?>(null) }
    LiveSessionScreen(
        state = rpeState(
            rpes,
            done,
            picker?.let { RpePickerTarget(it.first, it.second, it.third) },
            target,
            previous,
        ),
        elapsedSeconds = 0,
        onEvent = { event ->
            when (event) {
                is LiveSessionEvent.OpenRpePicker -> picker = Triple(event.position, event.setIndex, false)

                LiveSessionEvent.DismissRpePicker -> picker = null

                is LiveSessionEvent.SetChecked -> {
                    val i = event.setIndex - 1
                    if (event.checked && i > 1) {
                        done = done.toMutableList().also { it[i] = true }
                    } else if (event.checked && rpes[i] == null) {
                        picker = Triple(event.position, event.setIndex, true)
                    } else {
                        done = done.toMutableList().also { it[i] = event.checked }
                    }
                }

                is LiveSessionEvent.SetRpeChosen -> {
                    val i = event.setIndex - 1
                    rpes = rpes.toMutableList().also { it[i] = RpeScale.text(event.rpe) }
                    if (picker?.third == true && !done[i]) done = done.toMutableList().also { it[i] = true }
                    picker = null
                }

                else -> Unit
            }
        },
        onBack = {},
        onOpenExercise = {},
    )
}
