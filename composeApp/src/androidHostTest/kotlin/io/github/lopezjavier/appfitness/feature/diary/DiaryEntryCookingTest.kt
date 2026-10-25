package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-50 "Save": the diary row names the cooking method of a cooked entry. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class DiaryEntryCookingTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(method: CookingMethod, portionLabel: String? = null) {
        val entry = FoodLogEntry(
            id = "e1",
            logDate = LocalDate(2026, 10, 19),
            mealSlotId = "lunch",
            foodId = "chicken",
            foodName = "Pechuga de pollo",
            amount = 120.0,
            portionLabel = portionLabel,
            baseUnit = "g",
            kcal = 150.0,
            proteinG = 30.0,
            carbsG = 0.0,
            fatG = 3.0,
            cookingMethod = method,
        )
        rule.setContent {
            AppTheme { DiaryEntryRow(entry, canDelete = true, onEdit = {}, onDelete = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun cookedEntry_showsTheMethodAfterTheAmount() {
        show(CookingMethod.Griddle)

        rule.onNodeWithTag("diary_entry_detail_e1", useUnmergedTree = true).assertTextEquals("120 g · A la plancha")
    }

    @Test
    fun cookedEntryWithAPortion_showsThePortionAndTheMethod() {
        show(CookingMethod.Fried, portionLabel = "2 × filete")

        rule.onNodeWithTag("diary_entry_detail_e1", useUnmergedTree = true).assertTextEquals("2 × filete · Frito")
    }

    @Test
    fun rawEntry_showsOnlyTheAmount() {
        show(CookingMethod.Raw)

        rule.onNodeWithTag("diary_entry_detail_e1", useUnmergedTree = true).assertTextEquals("120 g")
    }
}
