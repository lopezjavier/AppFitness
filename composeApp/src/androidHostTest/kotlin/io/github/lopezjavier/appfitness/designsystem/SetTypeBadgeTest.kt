package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.junit.Assert.assertFalse
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
    fun eachType_showsItsLetter_andNormalShowsN() {
        val expected = mapOf(SetType.WarmUp to "C", SetType.Failure to "F", SetType.Drop to "D", SetType.Working to "N")
        rule.setContent {
            AppTheme {
                Column {
                    expected.keys.forEach { type ->
                        Box(Modifier.testTag("wrap_${type.name}")) { SetTypeBadge(type) }
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
    fun theNormalBadge_isDescribedByNothing_soTheRowDescriptionOwnsIt() {
        rule.setContent { AppTheme { SetTypeBadge(SetType.Working, Modifier.testTag("badge")) } }

        assertFalse(
            rule.onNodeWithTag("badge").fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription),
        )
    }

    @Test
    fun theOrdinal_showsTheSetOrderOutsideTheBadge() {
        rule.setContent {
            AppTheme {
                Row {
                    SetOrdinal("3")
                    SetTypeBadge(SetType.WarmUp)
                }
            }
        }

        rule.onNodeWithTag("set_ordinal").assertTextEquals("3")
        rule.onNode(hasTestTag("set_type_badge"), useUnmergedTree = true).assertTextEquals("C")
    }

    @Test
    fun aLetterBadge_isDescribedByItsFullLabel() {
        rule.setContent { AppTheme { SetTypeBadge(SetType.WarmUp, Modifier.testTag("badge")) } }

        rule.onNodeWithTag("badge").assertContentDescriptionEquals("Calentamiento")
    }

    @Test
    fun aClickableBadge_isA48DpButton_thatReportsTheTap() {
        var taps = 0
        rule.setContent {
            AppTheme {
                SetTypeBadge(
                    SetType.Failure,
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
