package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import io.github.lopezjavier.appfitness.domain.model.SplitSession
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-93: "Tipo de rutina", the split options screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockSplitsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockSplitsEvent>()
    private var backs = 0

    private val options = listOf(
        SplitOption("a", "Cuerpo completo", false, "Cada músculo, tres veces.", listOf(SplitSession("Todo", listOf()))),
        SplitOption("b", "Torso-pierna", true, "Encaja con tus días.", listOf(SplitSession("Torso", listOf()))),
        SplitOption(
            "c",
            "Empuje-tirón-pierna",
            false,
            "Más volumen por sesión.",
            listOf(SplitSession("Empuje", listOf())),
        ),
    )

    private fun show(state: BlockSplitsUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { BlockSplitsScreen(state, flowOf(online), { events += it }, { backs++ }) }
        }
        rule.waitForIdle()
    }

    @Test
    fun threeOptions_areShown_withRecommendedAsTextOnOne_andItPreselected() {
        show(BlockSplitsUiState.Options(options, "b"))

        for (id in listOf("a", "b", "c")) rule.onNodeWithTag("block_splits_option_$id").assertExists()
        rule.onAllNodesWithTag("block_splits_recommended_a").assertCountEquals(0)
        rule.onNodeWithTag("block_splits_recommended_b").assertExists()
        rule.onNodeWithText("Torso-pierna · Recomendada").assertExists()
        rule.onNodeWithTag("block_splits_option_b").assertIsSelected()
        rule.onNodeWithText("Tipo de rutina").assertExists()
    }

    @Test
    fun pickingAnotherOption_sendsThePick() {
        show(BlockSplitsUiState.Options(options, "b"))

        rule.onNodeWithTag("block_splits_option_c").performClick()

        assertEquals(listOf<BlockSplitsEvent>(BlockSplitsEvent.Pick("c")), events)
    }

    @Test
    fun crearBloque_sendsCreate_whenOnline() {
        show(BlockSplitsUiState.Options(options, "b"))

        rule.onNodeWithTag("block_splits_create").assertIsEnabled().performClick()

        assertEquals(listOf<BlockSplitsEvent>(BlockSplitsEvent.Create), events)
    }

    @Test
    fun offline_disablesCreateWithTheHint() {
        show(BlockSplitsUiState.Options(options, "b"), online = false)

        rule.onNodeWithTag("block_splits_create").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el bloque").assertExists()
    }

    @Test
    fun askingTheOptions_showsProgress() {
        show(BlockSplitsUiState.Requesting)

        rule.onNodeWithTag("block_splits_progress").assertExists()
        rule.onNodeWithText("Pensando en cómo repartir tus días…").assertExists()
        rule.onAllNodesWithTag("block_splits_create").assertCountEquals(0)
    }

    @Test
    fun building_showsProgress_andDisablesCreate() {
        show(BlockSplitsUiState.Options(options, "b", building = true))

        rule.onNodeWithText("Preparando tu bloque…").assertExists()
        rule.onNodeWithTag("block_splits_create").assertIsNotEnabled()
    }

    @Test
    fun buildFailure_offersReintentar() {
        show(BlockSplitsUiState.Failed(PlanFailureKind.Invalid, BlockSplitsStage.Build))

        rule.onNodeWithTag("block_splits_retry").performClick()

        assertEquals(listOf<BlockSplitsEvent>(BlockSplitsEvent.Retry), events)
    }

    @Test
    fun optionsFailure_usesItsOwnCopy_andReintentar() {
        show(BlockSplitsUiState.Failed(PlanFailureKind.Other, BlockSplitsStage.Options))

        rule.onNodeWithText("No se pudieron preparar las opciones. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("block_splits_retry").assertExists()
    }
}
