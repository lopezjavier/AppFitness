package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.MuscleShare
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun detailWith(split: List<MuscleShare>, rpe: Double? = 8.0) = SessionDetailUiState.Content(
    name = "Torso",
    label = DateLabel.Yesterday,
    stats = SessionStats(durationSeconds = 3_725, completedSets = 3, volumeKg = 2_120.0),
    exercises = listOf(
        DetailExercise(
            0,
            "e1",
            "Press banca",
            listOf(DetailSet(1, SetType.Working, "60 kg × 12", rpe), DetailSet(2, SetType.WarmUp, "40 kg × 12")),
            isPersonalRecord = false,
        ),
    ),
    split = split,
)

/** US-37 detail: muscle split and RPE per set. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SessionDetailSplitTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: SessionDetailUiState) {
        rule.setContent { AppTheme { SessionDetailScreen(state, {}) } }
        rule.waitForIdle()
    }

    @Test
    fun split_showsOneRowPerGroupWithSetsAndPercent() {
        show(detailWith(listOf(MuscleShare(MuscleGroup.Chest, 3, 0.75), MuscleShare(MuscleGroup.Back, 1, 0.25))))

        rule.onNodeWithTag("session_detail_split").assertExists()
        rule.onNodeWithTag("session_detail_split_Chest").assertTextContains("3 series · 75 %")
        rule.onNodeWithTag("session_detail_split_Back").assertTextContains("1 serie · 25 %")
    }

    @Test
    fun split_isHiddenWhenThereIsNoSplit() {
        show(detailWith(emptyList()))

        rule.onNodeWithTag("session_detail_split").assertDoesNotExist()
    }

    @Test
    fun rpe_isShownOnlyOnTheSetsThatHaveIt() {
        show(detailWith(emptyList()))
        rule.onNodeWithTag("session_detail_list").performScrollToNode(hasTestTag("session_detail_set_0_2"))

        rule.onNodeWithTag("session_detail_set_rpe_0_1", useUnmergedTree = true).assertTextContains("RPE 8")
        rule.onNodeWithTag("session_detail_set_rpe_0_2", useUnmergedTree = true).assertDoesNotExist()
    }
}
