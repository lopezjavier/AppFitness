package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-56 and US-59 on the Preferencias hub: six rows in order, each opening its destination, no state of its own. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1000dp-xxhdpi")
class PreferencesHubScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val sections = mutableListOf<PreferencesSection>()
    private var meals = 0
    private var backs = 0

    private var mealDefaults = 0

    private val keys =
        listOf("personal", "avisos", "meals", "meal_defaults", "cooking", "training")

    private fun show(summary: PreferencesHubSummary = PreferencesHubSummary()) {
        rule.setContent {
            AppTheme {
                PreferencesHubScreen(
                    onBack = { backs++ },
                    actions = PreferencesHubActions({ sections += it }, { meals++ }, { mealDefaults++ }),
                    summary = summary,
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
    fun sixRows_readTheDestinationTitles_inOrder_andThereIsNoFoodAuditRow() {
        show()
        rule.onNodeWithTag("preferences_row_food_audit").assertDoesNotExist()
        val labels = listOf(
            "Datos personales",
            "Avisos",
            "Comidas del día",
            "Comidas por defecto",
            "Cocina",
            "Registro de series",
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
                PreferencesSection.Reminders,
                PreferencesSection.Cooking,
                PreferencesSection.Training,
            ),
            sections,
        )
        assertEquals(1, meals)
        assertEquals(1, mealDefaults)
    }

    @Test
    fun defaultComidasRow_opensTheDefaultsEditor_only() {
        show()
        rule.onNodeWithTag("preferences_row_meal_defaults").performClick()
        assertEquals(1, mealDefaults)
        assertEquals(0, meals)
        assertTrue(sections.isEmpty())
    }

    @Test
    fun threeNamedGroups_thenCuentaLast() {
        show()
        val tops = listOf("General", "Nutrición", "Entreno", "Cuenta").map {
            rule.onNodeWithText(it).getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sortedBy { it.value }, tops)
    }

    @Test
    fun rowsWithAValue_showItAsTheSupportingLine() {
        show(
            PreferencesHubSummary(
                personal = PersonalSummary(30, 180, ActivityLevel.Moderate),
                mealCount = 4,
                remindersOn = 3,
            ),
        )
        rule.onNodeWithTag("preferences_row_meals").assertTextContains("4 comidas", substring = true)
        rule.onNodeWithTag("preferences_row_avisos").assertTextContains("3 de 4 activados", substring = true)
        rule.onNodeWithTag("preferences_row_personal").assertTextContains("30 años · 180 cm", substring = true)
    }

    @Test
    fun nutricion_listsOnlyFoodPreferences_caloriesAndMacrosLiveInTheTrainerPlan() {
        show()
        rule.onNodeWithTag("preferences_row_energy").assertDoesNotExist()
        rule.onNodeWithTag("preferences_row_macros").assertDoesNotExist()
        rule.onNodeWithText("Objetivo calórico").assertDoesNotExist()
        rule.onNodeWithText("Macros").assertDoesNotExist()
    }

    @Test
    fun miEntrenamiento_isNotListed_itLivesInThePerfilRoot() {
        show()
        rule.onNodeWithTag("preferences_row_mi_entrenamiento").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_capsTheContentWidth() {
        show()
        val row = rule.onNodeWithTag("preferences_row_personal").getUnclippedBoundsInRoot()
        assertTrue("row width ${row.width}", row.width <= WindowWidthClassDefaults.FormContentMaxWidth)
    }
}
