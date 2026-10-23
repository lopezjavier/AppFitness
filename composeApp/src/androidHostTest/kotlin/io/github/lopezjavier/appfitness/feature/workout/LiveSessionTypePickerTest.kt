package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun liveState(types: List<SetType>, picker: TypePickerTarget?) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = listOf(
        LiveExercise(
            0,
            "e1",
            "Press banca",
            null,
            types.mapIndexed { i, t -> LiveSetRow(i + 1, "", "", completed = false, type = t) },
        ),
    ),
    isOnline = true,
    typePicker = picker,
)

/** US-29 live part: assigning and removing a set type from the badge and the picker sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class LiveSessionTypePickerTest {
    @get:Rule
    val rule = createComposeRule()

    /** A minimal reducer standing in for the ViewModel: picker target and set types. */
    private fun showStateful(initial: List<SetType> = listOf(SetType.Working, SetType.Working)) {
        rule.setContent { AppTheme { StatefulLive(initial) } }
        rule.waitForIdle()
    }

    private fun badge(set: Int) = rule.onNode(
        hasTestTag("set_type_badge") and hasAnyAncestor(hasTestTag("live_set_0_$set")),
        useUnmergedTree = true,
    )

    @Test
    fun badge_hasA48DpTouchTarget() {
        showStateful()

        rule.onNodeWithTag("live_set_0_1_type").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun assigningAType_tapWarmUp_appliesAndClosesAtOnce() {
        showStateful()

        rule.onNodeWithTag("live_set_0_1_type").performClick()
        rule.onNodeWithTag("set_type_picker_option_warm_up").performClick()
        rule.waitForIdle()

        badge(1).assertTextEquals("C")
        rule.onNodeWithTag("set_type_picker").assertDoesNotExist()
    }

    @Test
    fun removingAType_tapNormal_showsTheIndexAgain() {
        showStateful(listOf(SetType.WarmUp, SetType.Working))

        rule.onNodeWithTag("live_set_0_1_type").performClick()
        rule.onNodeWithTag("set_type_picker_option_working").performClick()
        rule.waitForIdle()

        badge(1).assertTextEquals("1")
    }

    @Test
    fun picker_hasNoDoneOrClearButtons_andShowsTheNormalIcon() {
        showStateful()

        rule.onNodeWithTag("live_set_0_1_type").performClick()

        rule.onNodeWithTag("set_type_picker_done").assertDoesNotExist()
        rule.onNodeWithTag("set_type_picker_clear").assertDoesNotExist()
        rule.onNodeWithTag("set_type_picker_normal_icon", useUnmergedTree = true).assertExists()
    }

    @Test
    fun picker_helpOpensTheExplanation_andCloses() {
        showStateful()

        rule.onNodeWithTag("live_set_0_1_type").performClick()
        rule.onNodeWithTag("set_type_help").performClick()
        rule.onNodeWithTag("set_type_help_sheet").assertExists()
        rule.onNodeWithTag("set_type_help_sheet_body").assertExists()
        rule.onNodeWithTag("set_type_help_sheet_close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("set_type_help_sheet").assertDoesNotExist()
    }

    @Test
    fun dismissing_leavesTheTypeUnchanged() {
        showStateful()

        rule.onNodeWithTag("live_set_0_1_type").performClick()
        rule.onNodeWithTag("set_type_picker_close").performClick()
        rule.waitForIdle()

        badge(1).assertTextEquals("1")
        rule.onNodeWithTag("set_type_picker").assertDoesNotExist()
    }

    @Test
    fun openPicker_survivesRecreation_andTapStillApplies() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { StatefulLive(listOf(SetType.Working, SetType.Working)) } }
        rule.onNodeWithTag("live_set_0_2_type").performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("set_type_picker_option_drop").performClick()
        rule.waitForIdle()

        badge(2).assertTextEquals("D")
    }
}

@androidx.compose.runtime.Composable
private fun StatefulLive(initial: List<SetType>) {
    var types by rememberSaveable { mutableStateOf(initial) }
    var picker by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    LiveSessionScreen(
        state = liveState(types, picker?.let { TypePickerTarget(it.first, it.second) }),
        elapsedSeconds = 0,
        onEvent = { event ->
            when (event) {
                is LiveSessionEvent.OpenTypePicker -> picker = event.position to event.setIndex

                LiveSessionEvent.DismissTypePicker -> picker = null

                is LiveSessionEvent.SetTypeChosen -> {
                    types = types.toMutableList().also { it[event.setIndex - 1] = event.type }
                    picker = null
                }

                else -> Unit
            }
        },
        onBack = {},
        onOpenExercise = {},
    )
}
