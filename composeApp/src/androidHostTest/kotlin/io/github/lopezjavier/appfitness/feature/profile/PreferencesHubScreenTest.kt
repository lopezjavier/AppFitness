package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-56 and US-59 on the Preferencias hub: eight rows in order, each opening its destination, no state of its own. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1000dp-xxhdpi")
class PreferencesHubScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val sections = mutableListOf<PreferencesSection>()
    private var meals = 0
    private var account = 0
    private var backs = 0

    private val keys = listOf("personal", "energy", "macros", "meals", "cooking", "training", "checkin", "account")

    private fun show() {
        rule.setContent {
            AppTheme {
                PreferencesHubScreen(
                    onBack = { backs++ },
                    actions = PreferencesHubActions({ sections += it }, { meals++ }, { account++ }),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun title_isPreferencias_andBackLeaves() {
        show()
        rule.onNodeWithTag("preferences_title").assertTextEquals("Preferencias")
        rule.onNodeWithTag("preferences_back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun eightRows_readTheDestinationTitles_inOrder() {
        show()
        val labels = listOf(
            "Datos personales",
            "Objetivo calórico",
            "Macros",
            "Comidas del día",
            "Cocina",
            "Entrenamiento",
            "Check-in semanal",
            "Cuenta",
        )
        keys.zip(labels).forEach { (key, label) ->
            rule.onNodeWithTag("preferences_row_$key").assertTextEquals(label).assertHeightIsAtLeast(48.dp)
        }
        val tops = keys.map { rule.onNodeWithTag("preferences_row_$it").getUnclippedBoundsInRoot().top }
        assertEquals(tops.sortedBy { it.value }, tops)
    }

    @Test
    fun everyRow_callsItsCallback() {
        show()
        keys.forEach { rule.onNodeWithTag("preferences_row_$it").performClick() }
        assertEquals(
            listOf(
                PreferencesSection.PersonalData,
                PreferencesSection.EnergyGoal,
                PreferencesSection.Macros,
                PreferencesSection.Cooking,
                PreferencesSection.Training,
                PreferencesSection.CheckIn,
            ),
            sections,
        )
        assertEquals(1, meals)
        assertEquals(1, account)
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_capsTheContentWidth() {
        show()
        val row = rule.onNodeWithTag("preferences_row_energy").getUnclippedBoundsInRoot()
        assertTrue("row width ${row.width}", row.width <= WindowWidthClassDefaults.FormContentMaxWidth)
    }
}
