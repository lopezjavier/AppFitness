package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.GroupPriority
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.Pick
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-102 scenarios that have a UI: the targets step and the pre-filled builder of the next block. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One scenario per test.
class BlockBuilderNextBlockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockBuilderEvent>()

    private fun show(state: BlockBuilderUiState) {
        rule.setContent { AppTheme { BlockBuilderScreen(state, flowOf(true), { events += it }, {}) } }
        rule.waitForIdle()
    }

    private fun row(
        group: MuscleGroup,
        choice: GroupPriority = GroupPriority.None,
        before: Int = 10,
        after: Int = 10,
        blocked: NextBlockTargets.Blocked? = null,
        suggestion: SuggestionState? = null,
    ) = GroupTargetRow(group, choice, before, after, blocked, suggestion)

    private fun targets(rows: List<GroupTargetRow>, noFit: NoFitInfo? = null, cut: Boolean = false) =
        BlockBuilderUiState.Targets(rows, adherencePct = 85, cut = cut, noFit = noFit)

    @Test
    fun targetsStep_showsTheBeforeAndAfterSetsOfEachGroup() {
        show(targets(listOf(row(MuscleGroup.Chest, GroupPriority.Prioritise, after = 12))))

        rule.onNodeWithText("Objetivo por grupo").assertExists()
        rule.onNodeWithTag("block_builder_targets_group_chest_line").assertTextEquals("Pecho: 10 → 12 series")
    }

    @Test
    fun tappingPriorizar_sendsThePriority() {
        show(targets(listOf(row(MuscleGroup.Chest))))

        rule.onNodeWithTag("block_builder_targets_group_chest_prioritise").performClick()

        assertEquals(
            listOf<BlockBuilderEvent>(BlockBuilderEvent.SetPriority(MuscleGroup.Chest, GroupPriority.Prioritise)),
            events,
        )
    }

    @Test
    fun tappingMantener_sendsThePriority() {
        show(targets(listOf(row(MuscleGroup.Shoulders))))

        rule.onNodeWithTag("block_builder_targets_group_shoulders_maintain").performClick()

        assertEquals(
            listOf<BlockBuilderEvent>(BlockBuilderEvent.SetPriority(MuscleGroup.Shoulders, GroupPriority.Maintain)),
            events,
        )
    }

    @Test
    fun theClearIcon_appearsOnlyWithAChoice_andSendsNone() {
        show(targets(listOf(row(MuscleGroup.Chest, GroupPriority.Maintain), row(MuscleGroup.Back))))

        rule.onNodeWithTag("block_builder_targets_group_back_clear").assertDoesNotExist()
        rule.onNodeWithTag("block_builder_targets_group_chest_clear").performClick()

        assertEquals(
            listOf<BlockBuilderEvent>(BlockBuilderEvent.SetPriority(MuscleGroup.Chest, GroupPriority.None)),
            events,
        )
    }

    @Test
    fun aFourthPriorizar_isDisabledWithItsReasonAsText() {
        show(
            targets(
                listOf(
                    row(MuscleGroup.Chest, GroupPriority.Prioritise, after = 12),
                    row(MuscleGroup.Back, blocked = NextBlockTargets.Blocked.ThreeChosen),
                ),
            ),
        )

        rule.onNodeWithTag("block_builder_targets_group_back_prioritise").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("block_builder_targets_group_back_maintain").assertIsEnabled()
        rule.onNodeWithTag("block_builder_targets_group_back_reason").assertTextEquals("Ya priorizas tres grupos.")
        assertTrue(events.isEmpty())
    }

    @Test
    fun lowAdherence_explainsThatNoSetsAreAdded() {
        show(
            targets(
                listOf(
                    row(MuscleGroup.Chest, GroupPriority.Prioritise, blocked = NextBlockTargets.Blocked.LowAdherence),
                ),
            ),
        )

        rule.onNodeWithTag("block_builder_targets_group_chest_reason")
            .assertTextEquals("Tu adherencia fue menor del 80 %: no añadimos series.")
    }

    @Test
    fun inACut_explainsThatOnlyFreedSetsArrive() {
        show(
            targets(
                listOf(row(MuscleGroup.Chest, GroupPriority.Prioritise, blocked = NextBlockTargets.Blocked.Cut)),
                cut = true,
            ),
        )

        rule.onNodeWithTag("block_builder_targets_group_chest_reason")
            .assertTextEquals("En definición solo recibe las series que liberes con Mantener.")
    }

    @Test
    fun theSuggestion_changesNothingUntilTapped_thenSendsTheEvent() {
        show(targets(listOf(row(MuscleGroup.Chest, suggestion = SuggestionState.Offered))))

        rule.onNodeWithTag("block_builder_targets_group_chest_line").assertTextEquals("Pecho: 10 → 10 series")
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("block_builder_targets_group_chest_suggestion")
            .assertTextContains("Sugerencia: +2 series")
            .performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.ApplySuggestion(MuscleGroup.Chest)), events)
    }

    @Test
    fun anAppliedSuggestion_saysAplicada_andTappingItAgainSendsTheSameEvent() {
        show(targets(listOf(row(MuscleGroup.Chest, after = 12, suggestion = SuggestionState.Applied))))

        rule.onNodeWithTag("block_builder_targets_group_chest_suggestion")
            .assertTextContains("Aplicada", substring = true)
            .performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.ApplySuggestion(MuscleGroup.Chest)), events)
    }

    @Test
    fun continuar_sendsContinueFromTargets_andIsDisabledWhenNothingFits() {
        show(targets(listOf(row(MuscleGroup.Chest))))
        rule.onNodeWithTag("block_builder_targets_continue").assertIsEnabled().performClick()
        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.ContinueFromTargets), events)
    }

    @Test
    fun continuar_isDisabledWithTheReasonWhenTheTargetsDoNotFit() {
        show(targets(listOf(row(MuscleGroup.Chest)), noFit = NoFitInfo(day = 0, neededMinutes = 80, limitMinutes = 60)))

        rule.onNodeWithTag("block_builder_targets_continue").assertIsNotEnabled()
        rule.onNodeWithTag("block_builder_targets_no_fit").assertExists()
    }

    @Test
    fun theNoFitNoteOfTheTargets_isATapToMiEntrenamiento() {
        show(targets(listOf(row(MuscleGroup.Chest)), noFit = NoFitInfo(day = 0, neededMinutes = 80, limitMinutes = 60)))

        rule.onNodeWithText("Cambiar en Mi entrenamiento", substring = true).assertExists()
        rule.onNodeWithTag("block_builder_targets_no_fit").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.OpenMyTraining), events)
    }

    @Test
    fun aStalledLift_showsPuedesCambiarlo_andTheQuestionMarkExplainsIt() {
        val state = BlockBuilderTestData.start()
        val picked = state.pick(0, 0, Pick(BlockBuilderTestData.bench.id, MuscleGroup.Chest, null, 60.0))
        show(BlockBuilderTestData.builderUi(picked, stalledExerciseIds = setOf(BlockBuilderTestData.bench.id)))

        rule.onNodeWithTag("block_builder_slot_0_0_swap_marker").assertTextEquals("puedes cambiarlo")
        rule.onNodeWithTag("block_builder_slot_0_0_swap_help").performClick()
        rule.onNodeWithText("Este ejercicio no ha progresado en el bloque", substring = true).assertExists()
    }

    @Test
    fun aLiftThatDidNotStall_hasNoMarker() {
        val state = BlockBuilderTestData.start()
        val picked = state.pick(0, 0, Pick(BlockBuilderTestData.bench.id, MuscleGroup.Chest, null, 60.0))
        show(BlockBuilderTestData.builderUi(picked))

        rule.onNodeWithTag("block_builder_slot_0_0_swap_marker").assertDoesNotExist()
    }

    @Test
    fun theSeededPick_showsItsCarriedOverLoad() {
        val state = BlockBuilderTestData.start()
        val picked = state.pick(0, 0, Pick(BlockBuilderTestData.bench.id, MuscleGroup.Chest, null, 62.5))
        show(BlockBuilderTestData.builderUi(picked))

        rule.onNodeWithTag("block_builder_slot_0_0").assertTextContains("kg", substring = true)
    }
}
