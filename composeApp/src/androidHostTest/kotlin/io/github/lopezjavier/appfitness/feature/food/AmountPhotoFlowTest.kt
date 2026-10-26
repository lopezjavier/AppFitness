package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.BackInterceptor
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.LocalBackInterceptor
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-54: add-food sheet -> photo icon -> source -> preview -> estimate -> only the amount is prefilled. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class AmountPhotoFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val capture = FakePhotoCapture()
    private val photoRepo = FakeFoodPhotoRepository()
    private val backInterceptor = BackInterceptor()

    private val chicken = food(
        "chicken",
        "Pechuga de pollo",
        kcal = 100.0,
        portions = listOf(FoodPortion("ration", "ración", 150.0)),
    )

    private fun start(food: Food = chicken) {
        foods.known[food.id] = food
        val addModel = AddFoodViewModel(
            foodId = food.id,
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
            savedState = SavedStateHandle(),
        )
        val photoModel = AmountPhotoViewModel(food.id, foods, photoRepo, capture, connectivity, SavedStateHandle())
        rule.setContent { Host(addModel, photoModel) }
        rule.waitForIdle()
    }

    @Composable
    @Suppress("ViewModelForwarding") // Test host: the sheet takes the view models the test built.
    private fun Host(addModel: AddFoodViewModel, photoModel: AmountPhotoViewModel) {
        val state by addModel.uiState.collectAsState()
        val photoState by photoModel.uiState.collectAsState()
        LaunchedEffect(photoModel) {
            photoModel.effects.collect {
                if (it is AmountPhotoEffect.AmountEstimated) {
                    addModel.onEvent(AddFoodEvent.AmountEstimated(it.amount, it.baselineAmountText))
                }
            }
        }
        CompositionLocalProvider(LocalBackInterceptor provides backInterceptor) {
            AppTheme {
                AddFoodSheet(
                    state = state,
                    onEvent = addModel::onEvent,
                    onDismiss = {},
                    amountPhoto = photoState,
                    onAmountPhotoEvent = photoModel::onEvent,
                )
                AmountPhotoFlow(photoState, photoModel::onEvent, capture::readJpeg)
            }
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { backInterceptor.intercept() }
        rule.waitForIdle()
    }

    private fun toPreview(source: String = "food_sheet_amount_photo_source_row_1") {
        tap("food_sheet_amount_photo")
        tap(source)
    }

    private fun estimate() {
        toPreview()
        tap("food_sheet_amount_photo_use")
    }

    @Test
    fun photoIcon_isNextToTheAmountField_withTheDescription_andATouchTarget() {
        start()

        val field = rule.onNodeWithTag("food_sheet_amount").getUnclippedBoundsInRoot()
        val icon = rule.onNodeWithTag("food_sheet_amount_photo").getUnclippedBoundsInRoot()
        assertTrue(field.right <= icon.left)
        assertTrue(icon.bottom - icon.top >= 48.dp)
        assertTrue(field.bottom > icon.top && field.top < icon.bottom)
        rule.onNodeWithTag("food_sheet_amount_photo").assertIsEnabled()
        val description = rule.onNodeWithTag("food_sheet_amount_photo").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
        assertEquals(listOf("Estimar cantidad con foto"), description)
    }

    @Test
    fun icon_opensTheSourceSheet_andBothSourcesReachThePreview() {
        start()

        tap("food_sheet_amount_photo")
        rule.onNodeWithTag("food_sheet_amount_photo_source_row_1").assertExists()
        rule.onNodeWithTag("food_sheet_amount_photo_source_row_2").assertExists()
        tap("food_sheet_amount_photo_source_row_2")

        rule.onNodeWithTag("food_sheet_amount_photo_retake").assertExists()
        rule.onNodeWithTag("food_sheet_amount_photo_use").assertExists()
        tap("food_sheet_amount_photo_retake")
        rule.onNodeWithTag("food_sheet_amount_photo_use").assertExists()
    }

    @Test
    fun estimate_prefillsOnlyTheAmount_andUpdatesThePreview_withoutAddingAnything() {
        photoRepo.amountGate = CompletableDeferred()
        start()
        estimate()

        rule.onNodeWithTag("food_sheet_amount_estimating").assertExists()
        rule.onNodeWithText("Estimando…").assertExists()
        photoRepo.amountGate?.complete(Unit)
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount_estimating").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("180")
        rule.onNodeWithTag("food_sheet_totals").assertTextContains("180 kcal", substring = true)
        rule.onNodeWithTag("food_sheet_title").assertTextContains("Pechuga de pollo")
        rule.onNodeWithTag("food_sheet").assertExists()
        assertEquals(chicken.id, photoRepo.amountCalls.single().first.id)
    }

    @Test
    fun estimatedAmount_isEditable() {
        start()
        estimate()

        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("150")
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount").assertTextContains("150")
        rule.onNodeWithTag("food_sheet_totals").assertTextContains("150 kcal", substring = true)
    }

    @Test
    fun namedPortionSelected_switchesBackToTheBaseUnit() {
        start()
        tap("food_sheet_portion_option_ration")
        rule.onNodeWithTag("food_sheet_portion_option_ration").assertIsSelected()

        estimate()

        rule.onNodeWithTag("food_sheet_portion_option_base").assertIsSelected()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("180")
    }

    @Test
    fun drink_fillsTheAmountInMillilitres() {
        photoRepo.amountResult = Result.success(AmountEstimate(250.0, "ml", null))
        start(food("milk", "Leche", kcal = 45.0, baseUnit = "ml"))

        estimate()

        rule.onNodeWithTag("food_sheet_amount").assertTextContains("250")
        assertEquals("ml", photoRepo.amountCalls.single().first.baseUnit)
    }

    @Test
    fun failure_showsTheMessageWithRetry_andKeepsTheAmount_thenRetrySucceeds() {
        photoRepo.amountResult = Result.failure(AppException(AppError.ServiceUnavailable))
        start()
        estimate()

        rule.onNodeWithTag("food_sheet_amount_error").assertExists()
        rule.onNodeWithText("No hemos podido estimar la cantidad").assertExists()
        rule.onNodeWithText("Reintentar").assertExists()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("100")

        photoRepo.amountResult = Result.success(AmountEstimate(180.0, "g", null))
        tap("food_sheet_amount_retry")

        rule.onNodeWithTag("food_sheet_amount_error").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("180")
        assertEquals(2, photoRepo.amountCalls.size)
        assertEquals(1, capture.captured.size)
    }

    @Test
    fun success_removesThePhotoFile() {
        start()

        estimate()

        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun cancellingWhileEstimating_removesThePhoto_andIgnoresTheLateResult() {
        photoRepo.amountGate = CompletableDeferred()
        start()
        estimate()

        tap("food_sheet_amount_cancel")
        photoRepo.amountGate?.complete(Unit)
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount_estimating").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("100")
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun backFromThePreview_returnsToTheSourceSheet_andDiscardsThePhoto() {
        start()
        toPreview()

        pressBack()

        rule.onNodeWithTag("food_sheet_amount_photo_source").assertExists()
        rule.onNodeWithTag("food_sheet_amount_photo_use").assertDoesNotExist()
        assertEquals(listOf("photo-1"), capture.discarded)
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("100")
    }

    @Test
    fun offline_disablesTheIcon_andShowsWhy() {
        connectivity.state.value = false
        start()

        rule.onNodeWithTag("food_sheet_amount_photo").assertIsNotEnabled()
        rule.onNodeWithTag("food_sheet_amount_offline").assertExists()
        rule.onNodeWithText("Sin conexión: no puedes estimar la cantidad con foto").assertExists()
    }

    @Test
    fun online_showsNoOfflineMessage() {
        start()

        rule.onNodeWithTag("food_sheet_amount_offline").assertDoesNotExist()
    }
}
