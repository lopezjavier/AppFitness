package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SetTypeBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun eachType_showsItsLetter_andNormalShowsTheNumber() {
        val expected = mapOf(SetType.WarmUp to "C", SetType.Failure to "F", SetType.Drop to "D", SetType.Working to "3")
        rule.setContent {
            AppTheme {
                Column {
                    expected.keys.forEach { type ->
                        Box(Modifier.testTag("wrap_${type.name}")) { SetTypeBadge(type, "3") }
                    }
                }
            }
        }

        expected.forEach { (type, text) ->
            rule.onNode(
                hasTestTag("set_type_badge") and hasAnyAncestor(hasTestTag("wrap_${type.name}")),
                useUnmergedTree = true,
            ).assertTextEquals(text)
        }
    }

    @Test
    fun aLetterBadge_isDescribedByItsFullLabel() {
        rule.setContent { AppTheme { SetTypeBadge(SetType.WarmUp, "1", Modifier.testTag("badge")) } }

        rule.onNodeWithTag("badge").assertContentDescriptionEquals("Calentamiento")
    }

    @Test
    fun aClickableBadge_isA48DpButton_thatReportsTheTap() {
        var taps = 0
        rule.setContent {
            AppTheme {
                SetTypeBadge(
                    SetType.Failure,
                    "2",
                    Modifier.testTag("badge"),
                    onClick = { taps++ },
                    clickLabel = "Tipo de la serie 2: Fallo",
                )
            }
        }

        rule.onNodeWithTag("badge").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, taps)
        rule.onNodeWithTag("badge").assertContentDescriptionEquals("Tipo de la serie 2: Fallo")
    }

    @Test
    fun everyType_hasADistinctTone() {
        val colors = SetType.entries.map { SetTypeBadgeDefaults.colors(it, KineticDarkColors) }

        assertEquals(SetType.entries.size, colors.distinct().size)
        assertTrue(colors.none { it.first == KineticDarkColors.neonLime || it.first == KineticDarkColors.athleticCyan })
    }
}
