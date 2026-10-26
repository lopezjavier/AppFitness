package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.diary.DiaryEffect
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)
private val Eggs = food("eggs", "Huevos revueltos")
private val Shake = food("shake", "Batido de proteína")
private val Rice = food("rice", "Arroz")

/**
 * US-21 "Saving a section as a comida" and "Duplicate name" end to end: the Diary section's save icon opens the
 * sheet over the Diary, the sheet stores the section and closes; a taken name is refused inline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class SaveTemplateFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository()
    private val templates = FakeMealTemplateRepository(
        mutableMapOf("eggs" to Eggs, "shake" to Shake, "rice" to Rice),
    )
    private val connectivity = FakeConnectivityObserver()

    private fun entry(id: String, slotId: String, foodId: String, name: String, amount: Double) = FoodLogEntry(
        id = id,
        logDate = Today,
        mealSlotId = slotId,
        foodId = foodId,
        foodName = name,
        amount = amount,
        portionLabel = null,
        baseUnit = "g",
        kcal = 100.0,
        proteinG = 10.0,
        carbsG = 10.0,
        fatG = 5.0,
    )

    private fun start() {
        diary.server[Today] = listOf(
            entry("e1", "breakfast", "eggs", "Huevos revueltos", 120.0),
            entry("e2", "breakfast", "shake", "Batido de proteína", 300.0),
            entry("e3", "lunch", "rice", "Arroz", 180.0),
        )
        val diaryModel = DiaryViewModel(
            FakeMealSlotRepository(),
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            connectivity,
            FakeMealDefaultsRepository(),
            SavedStateHandle(),
        )
        rule.setContent { Flow(diaryModel) }
        rule.waitForIdle()
    }

    @Composable
    private fun Flow(diaryModel: DiaryViewModel) {
        var sheetSlotId by remember { mutableStateOf<String?>(null) }
        val diaryState by diaryModel.uiState.collectAsState()
        LaunchedEffect(diaryModel) {
            diaryModel.effects.collect { if (it is DiaryEffect.OpenSaveTemplate) sheetSlotId = it.slotId }
        }
        AppTheme {
            DiaryScreen(diaryState, diaryModel::onEvent)
            val slotId = sheetSlotId
            if (slotId != null) {
                val model = remember(slotId) {
                    SaveTemplateViewModel(
                        slotId = slotId,
                        date = Today,
                        foodDiary = diary,
                        buildTemplateItems = BuildTemplateItems(),
                        validateTemplateName = ValidateTemplateName(),
                        mealTemplates = templates,
                        connectivity = connectivity,
                        savedState = SavedStateHandle(),
                    )
                }
                val state by model.uiState.collectAsState()
                LaunchedEffect(model) {
                    model.effects.collect { if (it == SaveTemplateEffect.Saved) sheetSlotId = null }
                }
                SaveTemplateSheet(state, model::onEvent, onDismiss = { sheetSlotId = null })
            }
        }
    }

    private fun saveBreakfastAs(name: String) {
        rule.onNodeWithTag("diary_section_breakfast_save_template").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("save_template_name_input").performTextReplacement(name)
        rule.waitForIdle()
        rule.onNodeWithTag("save_template_name_commit").performClick()
        rule.waitForIdle()
    }

    @Test
    fun savingTheBreakfastSection_createsTheComida_withItsFoodsAndQuantities() {
        start()

        saveBreakfastAs("Desayuno A")

        val saved = templates.templates.single()
        assertEquals("Desayuno A", saved.name)
        assertEquals("breakfast", saved.mealSlotId)
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), saved.items.map { it.food.id to it.amount })
        rule.onNodeWithTag("save_template_name_input").assertDoesNotExist()
    }

    @Test
    fun savingAnExistingName_showsTheDuplicateError_andKeepsTheSheetOpen() {
        templates.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        start()

        saveBreakfastAs("Desayuno A")

        rule.onNodeWithTag("save_template_name_input_error").assertIsDisplayed()
        rule.onNodeWithTag("save_template_name_input_error").assertTextEquals("Ya existe una comida con ese nombre")
        rule.onNodeWithTag("save_template_name_input").assertIsDisplayed()
        assertEquals(1, templates.templates.size)
    }

    @Test
    fun theSheet_isTitledGuardarComoComida_andFocusesTheName() {
        start()

        rule.onNodeWithTag("diary_section_breakfast_save_template").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("save_template_name_title").assertTextEquals("Guardar como comida")
        rule.onNodeWithTag("save_template_name_input").assertIsFocused()
    }

    @Test
    fun aDuplicateName_ignoresCaseAndSurroundingSpaces_andDisablesGuardar() {
        templates.templates += MealTemplate("t0", "Desayuno A", null, emptyList())
        start()
        rule.onNodeWithTag("diary_section_breakfast_save_template").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("save_template_name_input").performTextReplacement("  desayuno a ")
        rule.waitForIdle()

        rule.onNodeWithTag("save_template_name_input_error").assertTextEquals("Ya existe una comida con ese nombre")
        rule.onNodeWithTag("save_template_name_commit").assertIsNotEnabled()
        assertEquals(1, templates.templates.size)
    }

    @Test
    fun aSectionWithoutEntries_hasNoSaveIcon() {
        start()

        rule.onNodeWithTag("diary_section_dinner_save_template").assertDoesNotExist()
        rule.onNodeWithTag("diary_section_breakfast_save_template").assertIsDisplayed()
    }
}
