package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.RestSound
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** TEMPORARY (T-146, removed by T-148): the three candidates, the selected one and the tap. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RestSoundChooserScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val chosen = mutableListOf<RestSound>()

    private fun show(selected: RestSound = RestSound.SYSTEM) {
        rule.setContent {
            AppTheme {
                RestSoundChooserScreen(RestSoundChooserUiState(selected = selected), { chosen += it }, onBack = {})
            }
        }
    }

    @Test
    fun screen_listsTheThreeCandidatesInSpanish() {
        show()

        rule.onNodeWithTag("rest_sound_chooser_row_system").assertTextContains("Sonido del sistema")
        rule.onNodeWithTag("rest_sound_chooser_row_chime").assertTextContains("Campana suave")
        rule.onNodeWithTag("rest_sound_chooser_row_double_beep").assertTextContains("Doble pitido")
    }

    @Test
    fun screen_marksOnlyTheSelectedCandidate() {
        show(RestSound.CHIME)

        rule.onNodeWithTag("rest_sound_chooser_row_chime")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithTag("rest_sound_chooser_row_system")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    fun tappingARow_choosesThatCandidate() {
        show()

        rule.onNodeWithTag("rest_sound_chooser_row_double_beep").performClick()

        assertEquals(listOf(RestSound.DOUBLE_BEEP), chosen)
    }
}
