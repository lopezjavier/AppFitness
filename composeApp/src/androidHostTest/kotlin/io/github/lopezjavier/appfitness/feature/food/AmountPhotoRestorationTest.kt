package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoEffect
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.FakeFoodPhotoRepository
import io.github.lopezjavier.appfitness.feature.food.photo.FakePhotoCapture
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the prefilled amount and the photo step survive recreation; a pending photo never does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class AmountPhotoRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val chicken = food("chicken", "Pechuga de pollo")
    private val foods = FakeFoodRepository().apply { known[chicken.id] = chicken }
    private val capture = FakePhotoCapture()
    private val addSaved = SavedStateHandle()
    private val photoSaved = SavedStateHandle()
    private val connectivity = FakeConnectivityObserver()

    private fun addModel() = AddFoodViewModel(
        foodId = chicken.id,
        initialSlotId = null,
        date = LocalDate(2026, 10, 25),
        foodRepository = foods,
        foodDiary = FakeFoodDiaryRepository(),
        mealSlots = FakeMealSlotRepository(),
        foodPreferencesRepository = FakeFoodPreferencesRepository(),
        resolvePortion = ResolvePortion(),
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = addSaved,
    )

    private fun photoModel() =
        AmountPhotoViewModel(chicken.id, foods, FakeFoodPhotoRepository(), capture, connectivity, photoSaved)

    @Composable
    private fun Host() {
        // New view models per (re)composition over the same saved state: what a new process sees.
        val addModel = remember { addModel() }
        val photoModel = remember { photoModel() }
        val state by addModel.uiState.collectAsState()
        val photoState by photoModel.uiState.collectAsState()
        LaunchedEffect(photoModel) {
            photoModel.effects.collect {
                if (it is AmountPhotoEffect.AmountEstimated) {
                    addModel.onEvent(AddFoodEvent.AmountEstimated(it.amount, it.baselineAmountText))
                }
            }
        }
        AppTheme {
            AddFoodSheet(state, addModel::onEvent, {
            }, amountPhoto = photoState, onAmountPhotoEvent = photoModel::onEvent)
            AmountPhotoFlow(photoState, photoModel::onEvent, capture::readJpeg)
        }
    }

    @Test
    fun prefilledAmount_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Host() }
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_photo").performClick()
        rule.onNodeWithTag("add_food_photo_source_camera").performClick()
        rule.onNodeWithTag("add_food_photo_use").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_amount").assertTextContains("180")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_amount").assertTextContains("180")
    }

    @Test
    fun pendingPhoto_comesBackAsTheSourceSheet_neverAPreviewWithoutAPhoto() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Host() }
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_photo").performClick()
        rule.onNodeWithTag("add_food_photo_source_camera").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_photo_use").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_photo_use").assertDoesNotExist()
        rule.onNodeWithTag("add_food_photo_source_sheet").assertExists()
    }

    @Test
    fun viewModelRecreatedFromTheSameSavedState_returnsToTheSourceSheet() {
        photoSaved["amount_photo_step"] = "SourceSheet"
        rule.setContent { Host() }
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_photo_source_sheet").assertExists()
    }
}
