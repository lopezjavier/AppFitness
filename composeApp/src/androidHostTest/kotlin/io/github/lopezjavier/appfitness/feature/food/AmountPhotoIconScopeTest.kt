package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.usecase.RecalculateEntry
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.EditEntrySheet
import io.github.lopezjavier.appfitness.feature.diary.EditEntryViewModel
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.templates.DetailsRow
import io.github.lopezjavier.appfitness.feature.templates.OpenFood
import io.github.lopezjavier.appfitness.feature.templates.TemplateFoodSheet
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-54 scope: the photo icon belongs to "Añadir comida" only; the other users of `QuantityInput` stay unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class AmountPhotoIconScopeTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 25)
    private val chicken = food("chicken", "Pechuga de pollo")

    @Test
    fun editEntrySheet_hasNoPhotoIcon() {
        val foods = FakeFoodRepository().apply { known[chicken.id] = chicken }
        val diary = FakeFoodDiaryRepository().apply {
            server[today] = listOf(
                FoodLogEntry("e1", today, "snack", chicken.id, "Pechuga", 110.0, null, "g", 220.0, 10.0, 20.0, 5.0),
            )
        }
        val model = EditEntryViewModel(
            entryId = "e1",
            date = today,
            foodRepository = foods,
            foodDiary = diary,
            mealSlots = FakeMealSlotRepository(),
            recalculateEntry = RecalculateEntry(ResolvePortion(), ScaleNutrition(), ValidateQuantity()),
            connectivity = FakeConnectivityObserver(),
            savedState = SavedStateHandle(),
        )
        rule.setContent {
            val state by model.uiState.collectAsState()
            AppTheme { EditEntrySheet(state, model::onEvent, {}) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount").assertExists()
        rule.onNodeWithTag("food_sheet_amount_photo").assertDoesNotExist()
    }

    @Test
    fun templateFoodSheet_hasNoPhotoIcon() {
        val row = DetailsRow(0, chicken.id, chicken.name, "g", "100", null, null, 100.0, 10.0, 20.0, 5.0, true)
        rule.setContent {
            AppTheme {
                TemplateFoodSheet(OpenFood(row, chicken, null, true), {}, {}, {}, {}, {})
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount").assertExists()
        rule.onNodeWithTag("food_sheet_amount_photo").assertDoesNotExist()
    }
}
