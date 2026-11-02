package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-82: a block requested with "Tengo poco tiempo" says so on the preview. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockPreviewMinimalTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(minimal: Boolean) {
        rule.setContent {
            AppTheme {
                BlockPreviewScreen(
                    BlockPreviewUiState.Preview(
                        coachBlock(),
                        mapOf("ex-1" to "Press", "ex-2" to "Flexiones"),
                        1,
                        null,
                        false,
                        minimal = minimal,
                    ),
                    flowOf(true),
                    {},
                    {},
                    {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun minimalBlock_showsTheMinimoNote() {
        show(minimal = true)

        rule.onNodeWithTag("block_preview_minimal_note").assertExists()
        rule.onNodeWithText("Mínimo").assertExists()
        rule.onNodeWithText(
            "Para épocas con poco tiempo: un entreno a la semana y una serie por ejercicio, con el mismo peso. " +
                "Así se mantienen la fuerza y el músculo durante meses.",
        ).assertExists()
    }

    @Test
    fun ordinaryBlock_hasNoMinimoNote() {
        show(minimal = false)

        rule.onNodeWithTag("block_preview_minimal_note").assertDoesNotExist()
    }
}
