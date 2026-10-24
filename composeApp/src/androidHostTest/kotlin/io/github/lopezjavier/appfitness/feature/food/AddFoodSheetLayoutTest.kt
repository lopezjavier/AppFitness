package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-25: the details sheet built from a real [AddFoodViewModel], and its layout. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class AddFoodSheetLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val portions = listOf(FoodPortion("p100", "100 g", 100.0), FoodPortion("p1", "1 huevo", 55.0))
    private val egg = food("egg", "Huevo", portions = portions)

    private fun nutrients(vararg kinds: Pair<MicronutrientKind, MicronutrientUnit>) =
        Result.success(Micronutrients(kinds.map { (kind, unit) -> Micronutrient(kind, 1.5, unit) }))

    @Composable
    private fun Sheet(model: AddFoodViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { AddFoodSheet(state, model::onEvent, {}) }
    }

    private fun show() {
        foods.known["egg"] = egg
        val model = AddFoodViewModel(
            foodId = "egg",
            initialSlotId = null,
            date = LocalDate(2026, 10, 19),
            foodRepository = foods,
            foodDiary = FakeFoodDiaryRepository(),
            mealSlots = FakeMealSlotRepository(),
            resolvePortion = ResolvePortion(),
            scaleNutrition = ScaleNutrition(),
            validateQuantity = ValidateQuantity(),
            defaultMealSlot = DefaultMealSlot(),
            clock = MutableClock(),
            connectivity = FakeConnectivityObserver(),
            savedState = SavedStateHandle(),
        )
        rule.setContent { Sheet(model) }
        rule.waitForIdle()
    }

    @Test
    fun offFoodWithNutrients_showsItsRows() {
        foods.micronutrientResults["egg"] = nutrients(
            MicronutrientKind.Fiber to MicronutrientUnit.G,
            MicronutrientKind.Sugars to MicronutrientUnit.G,
        )
        show()

        rule.onNodeWithTag("add_food_label_row_fiber").assertTextContains("Fibra", substring = true)
        rule.onNodeWithTag("add_food_label_row_sugars").assertTextContains("Azúcares", substring = true)
    }

    @Test
    fun manualFoodWithTypedValues_showsOnlyTheTypedRows() {
        foods.micronutrientResults["egg"] = nutrients(MicronutrientKind.Fiber to MicronutrientUnit.G)
        show()

        rule.onNodeWithTag("add_food_label_row_fiber").assertExists()
        rule.onNodeWithTag("add_food_label_row_salt").assertDoesNotExist()
    }

    @Test
    fun foodWithoutData_showsOnlyTheMacroRows() {
        show()

        rule.onNodeWithTag("add_food_label_row_energy").assertExists()
        rule.onNodeWithTag("add_food_label_row_fiber").assertDoesNotExist()
        rule.onNodeWithTag("add_food_label_toggle").assertDoesNotExist()
    }

    @Test
    fun portionThatIsJust100g_isNotDuplicated_andNamedPortionKeepsItsAmount() {
        show()

        assertTrue(rule.onAllNodesWithText("100 g (100 g)", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("add_food_portion_option_p100").assertTextContains("100 g")
        rule.onNodeWithTag("add_food_portion_option_p1").assertTextContains("1 huevo (55 g)")
    }

    @Test
    fun quantityBlock_stacksChipsAboveTheAmountFieldWithoutOverlap() {
        foods.micronutrientResults["egg"] = nutrients(MicronutrientKind.Fiber to MicronutrientUnit.G)
        show()

        val card = rule.onNodeWithTag("add_food_label_nutrients").getUnclippedBoundsInRoot()
        val chips = rule.onNodeWithTag("add_food_portion").getUnclippedBoundsInRoot()
        val amount = rule.onNodeWithTag("add_food_amount").getUnclippedBoundsInRoot()
        assertTrue("cards sit above the chips", card.bottom <= chips.top)
        assertTrue("chips sit above the amount field", chips.bottom <= amount.top)
    }
}
