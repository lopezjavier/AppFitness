package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockSet
import io.github.lopezjavier.appfitness.domain.model.BlockSetType
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 (T-206): the richer rows and the notes above the week tabs of the block preview. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class BlockPreviewDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val names = mapOf("ex-1" to "Press con mancuernas", "ex-2" to "Flexiones")

    private fun show(block: CoachBlock) {
        rule.setContent {
            AppTheme {
                BlockPreviewScreen(
                    BlockPreviewUiState.Preview(block, names, 1),
                    flowOf(true),
                    {},
                    {},
                    {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun blockWith(
        exercises: List<BlockExercise>,
        training: io.github.lopezjavier.appfitness.domain.model.BlockTraining = blockTraining(),
    ) = coachBlock(sessionsPerWeek = 1, training = training).let { block ->
        block.copy(
            sessions = block.sessions.map { s: BlockSession -> s.copy(exercises = exercises) },
        )
    }

    @Test
    fun row_showsRepsRpeAndRest() {
        show(blockWith(listOf(blockExercise("ex-1").copy(targetRpe = 8.0))))

        rule.onNodeWithText("3×8-12 reps · RPE 8 · 1 min 30 s").assertExists()
    }

    @Test
    fun row_withoutRpe_omitsThePart() {
        show(blockWith(listOf(blockExercise("ex-1"))))

        rule.onNodeWithText("3×8-12 reps · 1 min 30 s").assertExists()
    }

    @Test
    fun row_showsThePlannedWeight_andTheDropSet() {
        val dropSets = listOf(
            BlockSet(BlockSetType.Working, 8, 12, 8.0, 90),
            BlockSet(BlockSetType.Drop, 8, 12, 8.0, 90),
        )
        show(blockWith(listOf(blockExercise("ex-1").copy(plannedWeightKg = 62.5, setPlan = dropSets))))

        rule.onNodeWithText("3×8-12 reps · 1 min 30 s · 62,5 kg · Última serie descendente").assertExists()
    }

    @Test
    fun row_showsTheTechniqueNoteUnderTheDetail() {
        show(blockWith(listOf(blockExercise("ex-1").copy(techniqueNote = "Baja despacio."))))

        rule.onNodeWithText("Baja despacio.", substring = true).assertExists()
    }

    @Test
    fun supersetPair_isMarkedWithItsLabels() {
        val pair = listOf(
            blockExercise("ex-1").copy(supersetGroup = 1),
            blockExercise("ex-2").copy(supersetGroup = 1),
        )
        show(blockWith(pair))

        rule.onNodeWithTag("block_preview_superset_1_0_1").assertExists()
        rule.onNodeWithText("Superserie A1").assertExists()
        rule.onNodeWithText("Superserie A2").assertExists()
        rule.onNodeWithTag("block_preview_superset_accent_1_0_0").assertExists()
        rule.onNodeWithTag("block_preview_superset_accent_1_0_1").assertExists()
    }

    @Test
    fun withoutSupersets_noMarkerIsShown() {
        show(blockWith(listOf(blockExercise("ex-1"), blockExercise("ex-2"))))

        rule.onAllNodesWithTag("block_preview_superset_1_0_1").assertCountEquals(0)
        rule.onNodeWithText("Superserie", substring = true).assertDoesNotExist()
    }

    @Test
    fun splitReason_isShownAboveTheWeeks() {
        show(blockWith(listOf(blockExercise()), blockTraining().copy(splitReason = "Entrenas 3 días: torso y pierna.")))

        rule.onNodeWithTag("block_preview_split_reason").assertExists()
        rule.onNodeWithText("Entrenas 3 días: torso y pierna.").assertExists()
    }

    @Test
    fun changes_areShownOnlyWithAPreviousBlock() {
        show(
            blockWith(
                listOf(blockExercise()),
                blockTraining().copy(changesVsLastBlock = "Subes el pecho a 12 series."),
            ),
        )

        rule.onNodeWithTag("block_preview_changes").assertExists()
        rule.onNodeWithText("Qué cambia respecto al bloque anterior").assertExists()
        rule.onNodeWithText("Subes el pecho a 12 series.").assertExists()
    }

    @Test
    fun aLongChangesText_isShownWholeWithoutClamping() {
        val long = "Cambio de volumen. ".repeat(32).trim().take(600)
        show(blockWith(listOf(blockExercise()), blockTraining().copy(changesVsLastBlock = long)))

        val node = rule.onNodeWithTag("block_preview_changes_text").fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        rule.onNodeWithTag("block_preview_changes_text").assertTextEquals(long)
        val layout = layouts.single()
        assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
        assertEquals(false, layout.layoutInput.overflow == androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }

    @Test
    fun noPreviousBlock_hidesTheChangesSection() {
        show(blockWith(listOf(blockExercise()), blockTraining().copy(changesVsLastBlock = null)))

        rule.onAllNodesWithTag("block_preview_changes").assertCountEquals(0)
    }

    @Test
    fun belowRecommended_showsTheNoteAndItsHelp() {
        show(blockWith(listOf(blockExercise()), blockTraining().copy(notes = listOf("below_recommended"))))

        rule.onNodeWithTag("block_preview_below_recommended").assertExists()
        rule.onNodeWithText("Por debajo de lo recomendado").assertExists()
        rule.onNodeWithTag("block_preview_below_recommended_help").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("block_preview_below_recommended_help_sheet").assertExists()
    }

    @Test
    fun enoughVolume_hidesTheBelowRecommendedNote() {
        show(blockWith(listOf(blockExercise())))

        rule.onAllNodesWithTag("block_preview_below_recommended").assertCountEquals(0)
    }
}
