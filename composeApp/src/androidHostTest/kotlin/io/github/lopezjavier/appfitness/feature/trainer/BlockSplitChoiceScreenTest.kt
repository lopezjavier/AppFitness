package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 "A split for my days" and US-93 "Choosing how to start / From scratch": the split choice, no AI call. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockSplitChoiceScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockBuilderEvent>()

    private fun show(state: BlockBuilderUiState.Splits = BlockBuilderTestData.splits()) {
        rule.setContent { AppTheme { BlockSplitChoiceScreen(state, { events += it }) } }
        rule.waitForIdle()
    }

    @Test
    fun theSplitsOfFourDays_showTheRecommendedOneFirstAsText_withItsReason() {
        show()

        rule.onNodeWithText("Tipo de rutina").assertExists()
        rule.onNodeWithTag("block_split_card_0").assertIsSelected()
        rule.onNodeWithText("Torso / Pierna / Torso / Pierna · Recomendado").assertExists()
        rule.onNodeWithText("Entrenas cada músculo dos veces por semana", substring = true).assertExists()
        rule.onNodeWithTag("block_split_card_1").assertExists()
    }

    @Test
    fun pickingAnotherSplit_sendsThePick() {
        show()

        rule.onNodeWithTag("block_split_card_1").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.PickSplit(1)), events)
    }

    @Test
    fun continuar_movesOn() {
        show()

        rule.onNodeWithTag("block_split_continue").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.Continue), events)
    }

    @Test
    fun back_sendsBack() {
        show()

        rule.onNodeWithTag("block_split_back").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.Back), events)
    }

    @Test
    fun oneDayAWeek_listsASingleSplitSelected() {
        show(BlockBuilderTestData.splits(days = 1))

        rule.onNodeWithTag("block_split_card_0").assertIsSelected()
    }
}
