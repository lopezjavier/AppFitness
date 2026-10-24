package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-21 / US-47 renaming and deleting a comida from the editor: the name field with its duplicate-name hint, the
 * immediate delete, and the entries already in the Diary keeping their values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateManageScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 10, 19)
    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val callbacks = DetailsCallbacks()
    private val desayunoA = MealTemplate(
        id = "t1",
        name = "Desayuno A",
        mealSlotId = "breakfast",
        items = listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(shake, 300.0, "1 vaso")),
    )

    private fun showDetails() {
        rule.setContent { DetailsHost(detailsModel(templates, connectivity), callbacks) }
        rule.waitForIdle()
    }

    @Test
    fun renamingAComida_changesOnlyTheDraft_andGuardarPersistsIt() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_name_field").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        assertEquals("Desayuno A", templates.templates.single().name)
        rule.onNodeWithTag("template_details_save").performClick()
        rule.waitForIdle()
        assertEquals("Desayuno B", templates.templates.single().name)
        assertTrue(callbacks.saved)
    }

    @Test
    fun leavingWithoutGuardar_changesNothing() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_name_field").performTextReplacement("Desayuno B")
        rule.waitForIdle()

        assertEquals(0, templates.saveCalls)
        assertEquals(desayunoA, templates.templates.single())
    }

    @Test
    fun renamingToATakenName_showsTheDuplicateHint_andCannotBeSaved() {
        templates.templates += desayunoA
        templates.templates += desayunoA.copy(id = "t2", name = "Comida 2")
        showDetails()

        rule.onNodeWithTag("template_details_name_field").performTextReplacement("comida 2")
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_name_hint").assertTextEquals("Ya existe una comida con ese nombre")
        rule.onNodeWithTag("template_details_save").assertIsNotEnabled()
        assertEquals("Desayuno A", templates.templates.first { it.id == "t1" }.name)
    }

    @Test
    fun eliminarComida_deletesAtOnce_withoutADialog_andLeavesTheDetails() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_delete").performClick()
        rule.waitForIdle()

        assertTrue(templates.templates.isEmpty())
        assertTrue(callbacks.deleted)
        rule.onNodeWithTag("template_delete_confirm").assertDoesNotExist()
    }

    @Test
    fun deletingAComida_leavesTheEntriesAlreadyInTheDiary_withTheirValues() {
        templates.templates += desayunoA
        val logged = FoodLogEntry(
            id = "e1",
            logDate = day,
            mealSlotId = "lunch",
            foodId = "eggs",
            foodName = "Huevos revueltos",
            amount = 120.0,
            portionLabel = null,
            baseUnit = "g",
            kcal = 100.0,
            proteinG = 10.0,
            carbsG = 10.0,
            fatG = 5.0,
        )
        diary.server[day] = listOf(logged)
        showDetails()
        rule.onNodeWithTag("template_details_delete").performClick()
        rule.waitForIdle()

        assertEquals(listOf(logged), diary.server[day])
    }

    @Test
    fun offline_theDeleteButtonIsDisabled_andTheOfflineBannerShows() {
        templates.templates += desayunoA
        showDetails()
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_delete").assertIsNotEnabled()
        rule.onNodeWithTag("template_details_offline").assertIsDisplayed()
    }
}
