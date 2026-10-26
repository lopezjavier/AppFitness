package io.github.lopezjavier.appfitness.designsystem.a11y

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBar
import io.github.lopezjavier.appfitness.designsystem.MacroSlider
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.ProgressRing
import io.github.lopezjavier.appfitness.designsystem.RestTimerBar
import io.github.lopezjavier.appfitness.designsystem.SetCompletionCheckbox
import io.github.lopezjavier.appfitness.designsystem.SetRow
import io.github.lopezjavier.appfitness.designsystem.SetRowValues
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Semantics of the design-system components as a screen reader sees them: the exact Spanish strings TalkBack
 * would read (US-03 "Accessibility of interactive components").
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One method per accessibility contract.
class ComponentSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun stateDescription(expected: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    @Test
    fun progressRing_stateDescription_normal() {
        rule.setContent { AppTheme { ProgressRing(1840, 2400) } }
        rule.onNodeWithContentDescription("Calorías")
            .assert(stateDescription("1.840 de 2.400 kcal"))
    }

    @Test
    fun progressRing_withinTarget_showsOfTargetAndNoRemainingPill() {
        rule.setContent { AppTheme { ProgressRing(1840, 2400) } }
        rule.onNodeWithText("de 2.400 kcal", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("restantes", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun progressRing_overTarget_showsExcessText() {
        rule.setContent { AppTheme { ProgressRing(2550, 2400) } }
        rule.onNodeWithText("+150 kcal en exceso", useUnmergedTree = true).assertExists()
    }

    @Test
    fun macroBar_text_hasGramsOnlyNoPercent() {
        rule.setContent { AppTheme { MacroProgressBar("Proteínas", Macro.Protein, 9, 233) } }
        rule.onNodeWithText("9 g / 233 g", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("%", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun progressRing_stateDescription_excess() {
        rule.setContent { AppTheme { ProgressRing(2520, 2400) } }
        rule.onNodeWithContentDescription("Calorías")
            .assert(stateDescription("2.520 de 2.400 kcal, 120 kcal por encima del objetivo"))
    }

    @Test
    fun macroSlider_stateDescription_overridesRangePercent() {
        rule.setContent { AppTheme { MacroSlider("Proteínas", Macro.Protein, 30, 180, 720, {}) } }
        rule.onNodeWithContentDescription("Proteínas")
            .assert(stateDescription("30 por ciento, 180 gramos, 720 kilocalorías"))
    }

    @Test
    fun macroSlider_outOfRangePercent_isClampedNotCrashing() {
        rule.setContent { AppTheme { MacroSlider("Proteínas", Macro.Protein, 95, 180, 720, {}) } }
        rule.onNodeWithContentDescription("Proteínas")
            .assert(stateDescription("90 por ciento, 180 gramos, 720 kilocalorías"))
    }

    @Test
    fun macroSlider_header_mergedIntoOneStop() {
        rule.setContent { AppTheme { MacroSlider("Proteínas", Macro.Protein, 30, 180, 720, {}) } }
        // hideFromAccessibility() would drop "30 %" from the semantics tree, so the
        // header is merged instead: one TalkBack stop for the three texts rather than three.
        rule.onNode(hasText("30 %"))
            .assert(
                SemanticsMatcher("header merges label, percent and detail") { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ==
                        listOf("Proteínas", "30 %", "180 g · 720 kcal")
                },
            )
    }

    @Test
    fun macroBar_stateDescription() {
        rule.setContent { AppTheme { MacroProgressBar("Proteínas", Macro.Protein, 142, 180) } }
        rule.onNodeWithContentDescription("Proteínas")
            .assert(stateDescription("142 de 180 gramos"))
    }

    @Test
    fun macroBar_negativeConsumed_spokenAsZero() {
        rule.setContent { AppTheme { MacroProgressBar("Proteínas", Macro.Protein, -5, 180) } }
        rule.onNodeWithContentDescription("Proteínas")
            .assert(stateDescription("0 de 180 gramos"))
    }

    @Test
    fun trendPill_toneStateDescription_eachTone() {
        rule.setContent {
            AppTheme {
                Column {
                    TrendPill("favorable", TrendTone.Favorable)
                    TrendPill("unfavorable", TrendTone.Unfavorable)
                    TrendPill("caution", TrendTone.Caution)
                }
            }
        }
        rule.onNode(hasText("favorable", substring = false)).assert(stateDescription("A favor de tu objetivo"))
        rule.onNodeWithText("unfavorable").assert(stateDescription("En contra de tu objetivo"))
        rule.onNodeWithText("caution").assert(stateDescription("Fuera del rango de mantenimiento"))
    }

    @Test
    fun trendPill_neutral_hasNoStateDescription() {
        rule.setContent { AppTheme { TrendPill("Sin datos", TrendTone.Neutral) } }
        rule.onNodeWithText("Sin datos")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun setRow_weightField_cdAndValue_notPlaceholder() {
        val values = SetRowValues(2, "85 kg × 8", "", "", false, weightPlaceholder = "85", repsPlaceholder = "8")
        rule.setContent { AppTheme { SetRow(values, {}, {}, {}) } }
        val weight = rule.onNodeWithContentDescription("Kilos, serie 2")
        weight.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        // The placeholder is ghost data: it must not be read as if the user had typed it.
        rule.onAllNodes(hasText("85", substring = false), useUnmergedTree = false)
            .assertCountEquals(0)
    }

    @Test
    fun setRow_typedValue_isTheEditableText() {
        val values = SetRowValues(2, null, "87,5", "8", false, weightPlaceholder = "85")
        rule.setContent { AppTheme { SetRow(values, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Kilos, serie 2").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("87,5")),
        )
    }

    @Test
    fun setRow_completed_fieldsNotEditable() {
        val values = SetRowValues(1, null, "85", "8", true)
        rule.setContent { AppTheme { SetRow(values, {}, {}, {}) } }
        val notEditable = SemanticsMatcher.keyNotDefined(SemanticsActions.SetText)
        rule.onNodeWithContentDescription("Kilos, serie 1").assert(notEditable)
        rule.onNodeWithContentDescription("Repeticiones, serie 1").assert(notEditable)
    }

    @Test
    fun setRow_pending_fieldsEditable() {
        val values = SetRowValues(1, null, "", "", false)
        rule.setContent { AppTheme { SetRow(values, {}, {}, {}) } }
        val editable = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)
        rule.onNodeWithContentDescription("Kilos, serie 1").assert(editable)
    }

    @Test
    fun setCheckbox_cd_andToggleStateAfterClick() {
        rule.setContent {
            AppTheme {
                var checked by remember { mutableStateOf(false) }
                SetCompletionCheckbox(checked, { checked = it }, "Completar serie 2")
            }
        }
        val checkbox = rule.onNodeWithContentDescription("Completar serie 2")
        checkbox.assertIsOff()
        checkbox.performClick()
        checkbox.assertIsOn()
    }

    @Test
    fun restTimer_cd_minutesBranch() {
        rule.setContent { AppTheme { RestTimerBar(72.seconds, 120.seconds, {}, {}) } }
        rule.onNodeWithContentDescription("Descanso: 1 min 12 s").assertExists()
    }

    @Test
    fun restTimer_cd_secondsOnlyBranch() {
        rule.setContent { AppTheme { RestTimerBar(30.seconds, 120.seconds, {}, {}) } }
        rule.onNodeWithContentDescription("Descanso: 30 s").assertExists()
    }

    @Test
    fun errorState_liveRegionPolite_titleHeading_actionLabel() {
        rule.setContent {
            AppTheme {
                ErrorState(
                    "Comprueba tu conexión e inténtalo de nuevo.",
                    AppStateAction("Reintentar") {
                    },
                    title = "No se pudo cargar",
                )
            }
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        rule.onNodeWithText("No se pudo cargar").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithText("Reintentar").assertHasClickAction()
    }

    @Test
    fun emptyState_icon_notInSemantics() {
        rule.setContent { AppTheme { EmptyState("Todavía no hay datos para este periodo.") } }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun bottomSheet_handleIsDecorative() {
        rule.setContent {
            AppTheme { AppBottomSheet(onDismissRequest = {}, testTagPrefix = "sheet") { Text("contenido") } }
        }
        rule.waitForIdle()
        val handle = rule.onNodeWithTag("sheet_handle", useUnmergedTree = true).fetchSemanticsNode()
        val cds = handle.children.mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription) }
        assertTrue(cds.isEmpty())
        assertTrue(handle.children.none { it.config.getOrNull(SemanticsActions.OnClick) != null })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun bottomSheet_showsContent_withHeadingTitle() {
        rule.setContent {
            AppTheme {
                AppBottomSheet(onDismissRequest = {}, testTagPrefix = "sheet") {
                    Text("Título", Modifier.semantics { heading() }.testTag("sheet_title"))
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet_title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun numberStepper_exposesTargetsDescriptionsAndState() {
        var taps = 0
        rule.setContent {
            AppTheme {
                NumberStepper(
                    valueText = "−400 kcal",
                    onDecrement = { taps-- },
                    onIncrement = { taps++ },
                    decrementEnabled = false,
                    incrementEnabled = true,
                    decrementContentDescription = "Reducir ajuste 50 kcal",
                    incrementContentDescription = "Aumentar ajuste 50 kcal",
                    stateDescription = "Ajuste: −400 kcal",
                    testTagPrefix = "stepper",
                )
            }
        }

        rule.onNodeWithTag("stepper_decrement").assertContentDescriptionEquals("Reducir ajuste 50 kcal")
            .assertIsNotEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("stepper_increment").assertContentDescriptionEquals("Aumentar ajuste 50 kcal")
            .assertIsEnabled().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("stepper_decrement").performClick()
        rule.onNodeWithTag("stepper_value").assertTextEquals("−400 kcal")
        assertEquals(1, taps)
    }

    @Test
    fun textPill_showsItsText() {
        rule.setContent { AppTheme { TextPill("−400 kcal · Déficit moderado", Modifier.testTag("pill")) } }

        rule.onNodeWithTag("pill").assertTextEquals("−400 kcal · Déficit moderado")
    }
}
