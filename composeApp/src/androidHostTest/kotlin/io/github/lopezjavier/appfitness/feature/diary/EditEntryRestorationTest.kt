package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.usecase.RecalculateEntry
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.food
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)

/** ADR-40: the open edit sheet keeps its draft (amount, portion, meal) after the screen is recreated. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class EditEntryRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository().apply {
        known["egg"] = food("egg", "Huevo", kcal = 200.0, portions = listOf(FoodPortion("p1", "huevo", 55.0)))
    }
    private val diary = FakeFoodDiaryRepository().apply {
        server[Today] = listOf(
            FoodLogEntry("e1", Today, "snack", "egg", "Huevo", 110.0, null, "g", 220.0, 10.0, 20.0, 5.0),
        )
    }

    private fun model(state: SavedStateHandle) = EditEntryViewModel(
        entryId = "e1",
        date = Today,
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = FakeMealSlotRepository(),
        recalculateEntry = RecalculateEntry(ResolvePortion(), ScaleNutrition(), ValidateQuantity()),
        connectivity = FakeConnectivityObserver(),
        savedState = state,
    )

    @Composable
    private fun SheetHost(model: EditEntryViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { EditEntrySheet(state, model::onEvent, {}) }
    }

    @Test
    fun openSheet_keepsAmountPortionAndMeal_afterRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(model(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_portion_option_p1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("3")
        rule.onNodeWithTag("food_sheet_slot_dinner").performClick()
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet").assertExists()
        rule.onNodeWithTag("food_sheet_portion_option_p1").assertIsSelected()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("3")
        rule.onNodeWithTag("food_sheet_slot_dinner").assertIsSelected()
        rule.onNodeWithTag("food_sheet_details").assertExists()
    }

    @Test
    fun aViewModelRecreatedFromTheSameSavedState_showsTheDraftNotTheStoredEntry() {
        val saved = SavedStateHandle(
            mapOf("edit_entry_amount" to "75", "edit_entry_portion" to "", "edit_entry_slot" to "lunch"),
        )
        rule.setContent { SheetHost(model(saved)) }
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount").assertTextContains("75")
        rule.onNodeWithTag("food_sheet_slot_lunch").assertIsSelected()
    }
}
