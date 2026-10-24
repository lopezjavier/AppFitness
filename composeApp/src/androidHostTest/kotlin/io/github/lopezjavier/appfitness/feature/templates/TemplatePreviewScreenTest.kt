package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-53: the read-only comida details, "Añadir" and "Editar", with the real view model and fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TemplatePreviewScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val rice = food("rice", "Arroz", kcal = 200.0)
    private val milk = food("milk", "Leche", kcal = 100.0, baseUnit = "ml")
    private val templates = FakeMealTemplateRepository(mutableMapOf("rice" to rice, "milk" to milk))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val callbacks = PreviewCallbacks()
    private val comida = MealTemplate(
        id = "t1",
        name = "Comida A",
        mealSlotId = "lunch",
        items = listOf(MealTemplateItem(rice, 100.0, null), MealTemplateItem(milk, 50.0, "medio vaso")),
    )

    private fun show(onAdded: () -> Unit = {}, onEdit: () -> Unit = {}) {
        rule.setContent {
            PreviewHost(previewModel(templates, diary, connectivity), callbacks, onAdded, onEdit)
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun opening_showsTheNameTheRowsWithAmountsAndTheTotals_andAddsNothing() {
        templates.templates += comida
        show()

        rule.onNodeWithTag("template_preview_name").assertTextContains("Comida A")
        rule.onNodeWithTag("template_preview_kcal").assertTextContains("Total · 250 kcal")
        rule.onNodeWithTag("template_preview_macros").assertIsDisplayed()
        rule.onNodeWithTag("template_preview_item_0").assertTextContains("Arroz", substring = true)
        rule.onNodeWithTag("template_preview_item_0").assertTextContains("100 g", substring = true)
        rule.onNodeWithTag("template_preview_item_1").assertTextContains("Leche", substring = true)
        rule.onNodeWithTag("template_preview_item_1").assertTextContains("50 ml", substring = true)
        rule.onNodeWithTag("template_preview_item_1").assertTextContains("medio vaso", substring = true)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun tappingARow_doesNothing() {
        templates.templates += comida
        show()

        tap("template_preview_item_0")

        assertEquals(emptyList<Any>(), diary.addedBatches)
        assertEquals(0, callbacks.edited)
    }

    @Test
    fun anadir_logsEveryFoodToTheRouteMeal_andReportsAdded() {
        templates.templates += comida
        show()

        tap("template_preview_add")

        val batch = diary.addedBatches.single()
        assertEquals(listOf("rice" to 100.0, "milk" to 50.0), batch.map { it.foodId to it.amount })
        assertEquals(setOf("lunch"), batch.map { it.mealSlotId }.toSet())
        assertEquals(1, callbacks.added)
    }

    @Test
    fun editar_reportsTheEditorRequest_andAddsNothing() {
        templates.templates += comida
        show()

        tap("template_preview_edit")

        assertEquals(1, callbacks.edited)
        assertEquals(emptyList<Any>(), diary.addedBatches)
    }

    @Test
    fun offline_showsTheBanner_andDisablesAnadirButNotEditar() {
        templates.templates += comida
        connectivity.state.value = false
        show()

        rule.onNodeWithTag("template_preview_offline").assertIsDisplayed()
        rule.onNodeWithTag("template_preview_add").assertIsNotEnabled()
        rule.onNodeWithTag("template_preview_edit").assertIsEnabled()
    }

    @Test
    fun failingList_showsTheError_andRetryLoadsTheComida() {
        templates.listFailure = AppError.Network
        templates.templates += comida
        show()

        rule.onNodeWithTag("template_preview_error_state").assertIsDisplayed()
        templates.listFailure = null
        tap("template_preview_error_state_action")

        rule.onNodeWithTag("template_preview_name").assertTextContains("Comida A")
    }

    @Test
    fun comidaWithNoFoods_showsTheEmptyState_andAnadirIsDisabled() {
        templates.templates += comida.copy(items = emptyList())
        show()

        rule.onNodeWithTag("template_preview_empty").assertIsDisplayed()
        rule.onNodeWithTag("template_preview_add").assertIsNotEnabled()
    }

    @Test
    fun failedAdd_showsTheFailureText_andNothingIsLogged() {
        templates.templates += comida
        diary.failBatch()
        show()

        tap("template_preview_add")

        rule.onNodeWithTag("template_preview_add_failed").assertIsDisplayed()
        assertEquals(0, callbacks.added)
    }
}
