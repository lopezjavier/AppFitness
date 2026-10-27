package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Host tests of the T-120 list and card components: content, 48 dp targets, clicks and descriptions. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One behaviour per test, six small components.
class ListComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        clicks = 0
        rule.setContent { AppTheme { content() } }
    }

    @Test
    fun navigationRow_disabled_isNotClickable_andReadsAsDisabled() {
        show { NavigationRow("Comidas por defecto", { clicks++ }, Modifier.testTag("nav"), enabled = false) }

        rule.onNodeWithTag("nav").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun navigationRow_enabledByDefault_stillClicks() {
        show { NavigationRow("Comidas por defecto", { clicks++ }, Modifier.testTag("nav")) }

        rule.onNodeWithTag("nav").assertIsEnabled().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun appListRow_showsHeadlineAndSupporting_andIsAtLeast48dp() {
        show { AppListRow("Avena", Modifier.testTag("row"), supporting = "100 g") }

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Avena").assertIsDisplayed()
        rule.onNodeWithText("100 g").assertIsDisplayed()
    }

    @Test
    fun appListRow_withOnClick_isOneClickableButton() {
        show { AppListRow("Avena", Modifier.testTag("row"), onClick = { clicks++ }) }

        rule.onNodeWithTag("row").assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun appListRow_withoutOnClick_isNotClickable() {
        show { AppListRow("Avena", Modifier.testTag("row")) }

        assertFalse(
            rule.onNodeWithTag(
                "row",
            ).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick),
        )
    }

    @Test
    fun navigationRow_showsLabelAndValue_andOpens() {
        show { NavigationRow("Unidades", { clicks++ }, Modifier.testTag("nav"), value = "kg") }

        rule.onNodeWithTag("nav").assertHeightIsAtLeast(48.dp).assertTextContains("Unidades", substring = true)
        rule.onNodeWithTag("nav").assertTextContains("kg", substring = true).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun navigationRow_chevronIsDecorative() {
        show { NavigationRow("Unidades", {}, Modifier.testTag("nav")) }

        val description = rule.onNodeWithTag(
            "nav",
        ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertTrue(description.isNullOrEmpty())
    }

    @Test
    fun listSectionHeader_isAHeading_withTheCount() {
        show { ListSectionHeader("Comidas", Modifier.testTag("header"), count = 3) }

        val config = rule.onNodeWithTag("header").fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsProperties.Heading))
        rule.onNodeWithTag(
            "header",
        ).assertTextContains("Comidas", substring = true).assertTextContains("3", substring = true)
    }

    @Test
    fun addItemButton_readsAnadirObject_andClicks() {
        show { AddItemButton("comida", { clicks++ }, Modifier.testTag("add")) }

        rule.onNodeWithTag("add").assertTextContains("Añadir comida").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun addItemButton_disabled_doesNotClick() {
        show { AddItemButton("comida", { clicks++ }, Modifier.testTag("add"), enabled = false) }

        rule.onNodeWithTag("add").assertIsNotEnabled()
        rule.onNodeWithTag("add").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun metricCard_showsLabelValueAndSlots() {
        show {
            MetricCard(
                label = "PESO ACTUAL",
                value = "74,8 kg",
                modifier = Modifier.testTag("metric"),
                supporting = { Text("Desde tu primer peso") },
                trailingAction = { Text("Editar") },
                progress = { Text("Barra") },
            )
        }

        rule.onNodeWithTag("metric").assertHeightIsAtLeast(48.dp)
        listOf("PESO ACTUAL", "74,8 kg", "Desde tu primer peso", "Editar", "Barra").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun metricCard_withOnClick_isClickable() {
        show { MetricCard("PESO", "74,8 kg", Modifier.testTag("metric"), onClick = { clicks++ }) }

        rule.onNodeWithTag("metric").assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun metricCard_withOnClickAndTrailingAction_tappingTheValueFiresOnlyOnClick() {
        var pencil = 0
        show {
            MetricCard(
                "PESO",
                "74,8 kg",
                Modifier.testTag("metric"),
                trailingAction = { Text("Editar", Modifier.clickable { pencil++ }) },
                onClick = { clicks++ },
            )
        }

        rule.onNodeWithText("74,8 kg").assertHasClickAction().performClick()

        assertEquals(1, clicks)
        assertEquals(0, pencil)
    }

    @Test
    fun metricCard_withOnClickAndTrailingAction_tappingTheActionFiresOnlyTheAction() {
        var pencil = 0
        show {
            MetricCard(
                "PESO",
                "74,8 kg",
                Modifier.testTag("metric"),
                trailingAction = { Text("Editar", Modifier.clickable { pencil++ }) },
                onClick = { clicks++ },
            )
        }

        rule.onNodeWithText("Editar").performClick()

        assertEquals(0, clicks)
        assertEquals(1, pencil)
    }

    @Test
    fun metricCard_withOnClickAndTrailingAction_keepsTheSupportingContentOutsideTheButton() {
        show {
            MetricCard(
                "PESO",
                "74,8 kg",
                Modifier.testTag("metric"),
                supporting = { Text("Sincronizar", Modifier.testTag("sync")) },
                trailingAction = { Text("Editar") },
                onClick = {},
            )
        }

        rule.onNodeWithTag("sync").assertIsDisplayed()
        // The button node reads label and value only; the supporting line is a separate node.
        rule.onNodeWithText("74,8 kg").assertTextContains("PESO", substring = true)
        assertFalse(
            rule.onNodeWithText("74,8 kg").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Sincronizar" },
        )
    }

    @Test
    fun timeRangeSelector_marksTheSelected_andReportsTaps() {
        var selected by mutableStateOf(TimeRange.Month)
        show {
            TimeRangeSelector(
                selected = selected,
                onSelect = { selected = it },
                ranges = listOf(TimeRange.Week, TimeRange.Month, TimeRange.Year, TimeRange.All),
                tagPrefix = "range",
            )
        }

        rule.onNodeWithTag("range_month").assertIsSelected()
        rule.onNodeWithTag("range_year").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("range_year").assertIsSelected()
        assertEquals(TimeRange.Year, selected)
    }

    @Test
    fun timeRangeSelector_speaksTheFullRangeNames() {
        show {
            TimeRangeSelector(
                selected = TimeRange.Week,
                onSelect = {},
                ranges = listOf(
                    TimeRange.Week,
                    TimeRange.Month,
                    TimeRange.ThreeMonths,
                    TimeRange.SixMonths,
                    TimeRange.Year,
                ),
                tagPrefix = "range",
            )
        }

        mapOf(
            "range_week" to "1 semana",
            "range_month" to "1 mes",
            "range_threemonths" to "3 meses",
            "range_sixmonths" to "6 meses",
            "range_year" to "1 año",
        ).forEach { (tag, spoken) -> rule.onNodeWithTag(tag).assertContentDescriptionEquals(spoken).assertIsEnabled() }
    }

    @Test
    fun timeRangeSelector_segmentsAreAtLeast48dp() {
        show {
            TimeRangeSelector(TimeRange.Week, {}, listOf(TimeRange.Week, TimeRange.All), "range")
        }

        rule.onNodeWithTag("range_all").assertHeightIsAtLeast(48.dp)
    }
}
