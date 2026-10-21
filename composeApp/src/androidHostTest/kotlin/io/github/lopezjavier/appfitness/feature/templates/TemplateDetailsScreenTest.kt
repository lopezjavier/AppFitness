package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-21 comida details: the foods with their quantities, "Editar" and the error state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplateDetailsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val shake = food("shake", "Batido de proteína", kcal = 50.0)
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "shake" to shake))
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
    fun details_showTheNameTheTotalAndEveryFoodWithItsQuantity() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_name").assertTextContains("Desayuno A")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("270 kcal")
        rule.onNodeWithTag("template_details_item_0").assertTextContains("Huevos revueltos")
        rule.onNodeWithTag("template_details_item_0").assertTextContains("120 g")
        rule.onNodeWithTag("template_details_item_1").assertTextContains("Batido de proteína")
        rule.onNodeWithTag("template_details_item_1").assertTextContains("1 vaso")
        rule.onNodeWithTag("template_details_item_1").assertTextContains("300 g")
    }

    @Test
    fun editar_opensTheEditor() {
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_edit").performClick()

        assertTrue(callbacks.edited)
    }

    @Test
    fun aComidaThatCannotBeRead_showsTheErrorState_andRetryLoadsIt() {
        templates.listFailure = AppError.Network
        templates.templates += desayunoA
        showDetails()

        rule.onNodeWithTag("template_details_error_state_action").assertIsDisplayed()
        templates.listFailure = null
        rule.onNodeWithTag("template_details_error_state_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_item_0").assertIsDisplayed()
    }
}
