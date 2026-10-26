package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.BackInterceptor
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.LocalBackInterceptor
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.photo.FakeFoodPhotoRepository
import io.github.lopezjavier.appfitness.feature.food.photo.FakePhotoCapture
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoEffect
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoEvent
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.foodPhotoModel
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

/** US-48 / US-49: search -> photo icon -> sheets -> preview -> estimate -> prefilled form -> sheet, with fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class FoodPhotoFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val scanner = FakeBarcodeScanner()
    private val capture = FakePhotoCapture()
    private val photoRepo = FakeFoodPhotoRepository()
    private val backInterceptor = BackInterceptor()

    private fun start() {
        val searchModel = foodSearchModel(foods, connectivity, scanner, SavedStateHandle())
        val photoModel = foodPhotoModel(photoRepo, capture, connectivity)
        rule.setContent { Flow(searchModel, photoModel) }
        rule.waitForIdle()
    }

    @Composable
    @Suppress("ViewModelForwarding") // Test host: the flow takes the view models the test built.
    private fun Flow(searchModel: FoodSearchViewModel, photoModel: FoodPhotoViewModel) {
        var form by remember { mutableStateOf<NewFoodDraft?>(null) }
        var formOpen by remember { mutableStateOf(false) }
        var openFoodId by remember { mutableStateOf<String?>(null) }
        val searchState by searchModel.uiState.collectAsState()
        val photoState by photoModel.uiState.collectAsState()
        LaunchedEffect(photoModel) {
            photoModel.effects.collect {
                when (it) {
                    is FoodPhotoEffect.OpenPrefilledForm -> {
                        form = it.draft
                        formOpen = true
                    }

                    FoodPhotoEffect.OpenEmptyForm -> {
                        form = null
                        formOpen = true
                    }
                }
            }
        }
        CompositionLocalProvider(LocalBackInterceptor provides backInterceptor) {
            AppTheme {
                if (!formOpen) {
                    FoodSearchScreen(
                        searchState,
                        searchModel::onEvent,
                        onBack = {},
                        onPhotoClick = { photoModel.onEvent(FoodPhotoEvent.Open) },
                    )
                    FoodPhotoFlow(photoState, photoModel::onEvent, capture::readJpeg)
                } else {
                    val model = remember {
                        CreateFoodViewModel(
                            null,
                            null,
                            foods,
                            connectivity,
                            prefill = form?.let(NewFoodDraftCodec::encode),
                        )
                    }
                    val createState by model.uiState.collectAsState()
                    LaunchedEffect(model) {
                        model.effects.collect {
                            if (it is CreateFoodEffect.Saved) {
                                openFoodId = it.foodId
                                formOpen = false
                            }
                        }
                    }
                    CreateFoodScreen(createState, model::onEvent, {})
                }
                openFoodId?.let { id -> Sheet(id) { openFoodId = null } }
            }
        }
    }

    @Composable
    private fun Sheet(foodId: String, onDismiss: () -> Unit) {
        val model = remember(foodId) {
            AddFoodViewModel(
                foodId = foodId,
                initialSlotId = null,
                date = LocalDate(2026, 10, 19),
                foodRepository = foods,
                foodDiary = diary,
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
        }
        val state by model.uiState.collectAsState()
        AddFoodSheet(state, model::onEvent, onDismiss)
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { backInterceptor.intercept() }
        rule.waitForIdle()
    }

    private fun exists(tag: String) = rule.onNodeWithTag(tag).assertExists()

    private fun absent(tag: String) = rule.onNodeWithTag(tag).assertDoesNotExist()

    private fun toPreview(modeTag: String, sourceTag: String = "photo_source_row_1") {
        tap("food_search_action_photo")
        tap(modeTag)
        tap(sourceTag)
    }

    @Test
    fun quickActions_sitInOneRowUnderTheField_withTheSameSize_andALabel() {
        start()

        val field = rule.onNodeWithTag("food_search_field").getUnclippedBoundsInRoot()
        val scan = rule.onNodeWithTag("food_search_action_scan").getUnclippedBoundsInRoot()
        val photo = rule.onNodeWithTag("food_search_action_photo").getUnclippedBoundsInRoot()
        val create = rule.onNodeWithTag("food_search_action_create").getUnclippedBoundsInRoot()
        assertTrue("actions sit under the field", field.bottom <= scan.top)
        assertTrue(scan.right <= photo.left)
        assertTrue(photo.right <= create.left)
        assertEquals(scan.top, photo.top)
        assertEquals(photo.top, create.top)
        assertEquals(scan.right - scan.left, photo.right - photo.left)
        assertTrue(photo.bottom - photo.top >= 48.dp)
        rule.onNodeWithTag("food_search_action_photo").assertIsEnabled()
        rule.onNodeWithTag("food_search_action_photo").assertTextContains("Foto")
    }

    @Test
    fun photoAction_offline_isDisabled_andKeepsItsState() {
        connectivity.state.value = false
        start()

        rule.onNodeWithTag("food_search_action_photo").assertIsNotEnabled()
        val config = rule.onNodeWithTag("food_search_action_photo").fetchSemanticsNode().config
        assertEquals("Sin conexión", config[SemanticsProperties.StateDescription])
    }

    @Test
    fun photoIcon_opensTheModeSheetWithThreeRows() {
        start()

        tap("food_search_action_photo")

        exists("photo_mode")
        exists("photo_mode_row_1")
        exists("photo_mode_row_2")
        exists("photo_mode_row_3")
        rule.onNodeWithText("Calorías y macros de un plato").assertExists()
    }

    @Test
    fun choosingAMode_opensTheSourceSheetWithTwoRows() {
        start()

        tap("food_search_action_photo")
        tap("photo_mode_row_1")

        exists("photo_source")
        exists("photo_source_row_1")
        exists("photo_source_row_2")
        absent("photo_mode")
    }

    @Test
    fun galleryAndCamera_reachThePreview_withRepeatAndUse() {
        start()

        toPreview("photo_mode_row_1", "photo_source_row_2")

        exists("food_photo_preview_image")
        exists("food_photo_preview_retake")
        exists("food_photo_preview_use")
        assertEquals(listOf(PhotoSource.Gallery), capture.captured)
        tap("food_photo_preview_retake")
        assertEquals(listOf(PhotoSource.Gallery, PhotoSource.Gallery), capture.captured)
        exists("food_photo_preview_use")
    }

    @Test
    fun cameraCancelled_returnsToTheSourceSheet_withoutAnError() {
        capture.results += CaptureResult.Cancelled
        start()

        toPreview("photo_mode_row_1")

        exists("photo_source")
        absent("photo_source_capture_error")
        absent("food_photo_preview")
    }

    @Test
    fun captureFailure_showsTheMessageInTheSourceSheet() {
        capture.results += CaptureResult.Error
        start()

        toPreview("photo_mode_row_1")

        rule.onNodeWithTag("photo_source_capture_error")
            .assertTextContains("No se pudo abrir la cámara. Inténtalo de nuevo")
        exists("photo_source_row_1")
    }

    @Test
    fun commentField_existsInDishMode() {
        start()

        toPreview("photo_mode_row_1")

        exists("food_photo_comment_input")
    }

    @Test
    fun commentField_isAbsentInLabelMode() {
        start()

        toPreview("photo_mode_row_2")

        exists("food_photo_preview")
        absent("food_photo_comment_input")
    }

    @Test
    fun backFromThePreview_returnsToTheSourceSheet_andDiscardsThePhoto() {
        start()
        toPreview("photo_mode_row_1")

        pressBack()

        exists("photo_source")
        absent("food_photo_preview")
        assertEquals(listOf("photo-1"), capture.discarded)
    }

    @Test
    fun comment_isSentWithTheEstimate_andCounterIsExposed() {
        start()
        toPreview("photo_mode_row_1")

        rule.onNodeWithTag("food_photo_comment_input").performTextInput("sin chorizo")
        rule.waitForIdle()
        rule.onNodeWithTag("food_photo_comment_input_counter").assertTextContains("11 de 200")
        tap("food_photo_preview_use")

        assertEquals("sin chorizo", photoRepo.calls.single().comment)
    }

    @Test
    fun estimating_isShownWhileTheEstimateIsPending_withoutActions() {
        photoRepo.gate = CompletableDeferred()
        start()
        toPreview("photo_mode_row_1")

        tap("food_photo_preview_use")

        rule.onNodeWithTag("food_photo_estimating").assertExists()
        rule.onNodeWithText("Estimando…").assertExists()
        absent("food_photo_preview_use")
        pressBack()
        exists("food_photo_estimating")
    }

    @Test
    fun success_opensThePrefilledForm_withTheNote_editRemovesIt_andSaveOpensTheSheet() {
        start()
        toPreview("photo_mode_row_1")

        tap("food_photo_preview_use")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Lentejas con pollo")
        rule.onNodeWithTag("create_food_kcal_field").assertTextContains("180")
        rule.onNodeWithTag("create_food_portion_label_0").assertTextContains("1 ración")
        rule.onNodeWithTag("create_food_ai_note").assertTextContains("Estimado por IA · revisa los valores")
        rule.onNodeWithTag("create_food_name_field").performTextInput("!")
        rule.waitForIdle()
        absent("create_food_ai_note")
        assertTrue(foods.created.isEmpty())
        tap("create_food_save")
        assertEquals("Lentejas con pollo!", foods.created.single().name)
        exists("food_sheet")
    }

    @Test
    fun labelPhoto_withPartialData_opensTheFormWithBlankFields() {
        photoRepo.result = Result.success(
            FakeFoodPhotoRepository.lentils.copy(name = "Galletas", kcal = null, protein = null),
        )
        start()
        toPreview("photo_mode_row_2")

        tap("food_photo_preview_use")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Galletas")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun notRecognisedDish_showsTheDishMessage_andBothActions() {
        photoRepo.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        start()
        toPreview("photo_mode_row_1")

        tap("food_photo_preview_use")

        exists("food_photo_error")
        rule.onNodeWithText("No hemos podido estimar este alimento").assertExists()
        rule.onNodeWithText("Reintentar").assertExists()
        rule.onNodeWithText("Crear manualmente").assertExists()
    }

    @Test
    fun notRecognisedBarcode_showsTheProductMessage() {
        photoRepo.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        start()
        toPreview("photo_mode_row_3")

        tap("food_photo_preview_use")

        rule.onNodeWithText("No hemos podido identificar este producto").assertExists()
    }

    @Test
    fun networkFailure_showsTheConnectionMessage() {
        photoRepo.result = Result.failure(AppException(AppError.Network))
        start()
        toPreview("photo_mode_row_1")

        tap("food_photo_preview_use")

        rule.onNodeWithText("No hay conexión. Comprueba tu red e inténtalo de nuevo").assertExists()
    }

    @Test
    fun retry_sendsTheSamePhotoAgain_andSucceeds() {
        photoRepo.result = Result.failure(AppException(AppError.ServiceUnavailable))
        start()
        toPreview("photo_mode_row_1")
        tap("food_photo_preview_use")
        rule.onNodeWithText(
            "El servicio no está disponible ahora. Inténtalo más tarde o crea el alimento manualmente",
        ).assertExists()

        photoRepo.result = Result.success(FakeFoodPhotoRepository.lentils)
        tap("food_photo_error_retry")

        assertEquals(2, photoRepo.calls.size)
        assertEquals(1, capture.captured.size)
        exists("create_food_ai_note")
    }

    @Test
    fun createManually_opensTheEmptyForm_andCreatesNothing() {
        photoRepo.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        start()
        toPreview("photo_mode_row_1")
        tap("food_photo_preview_use")

        tap("food_photo_error_manual")

        exists("create_food_screen")
        absent("create_food_ai_note")
        rule.onNodeWithTag("create_food_name_field").assertTextContains("")
        assertTrue(foods.created.isEmpty())
        assertEquals(capture.discarded.size, 1)
    }

    @Test
    fun backFromTheError_returnsToThePreview() {
        photoRepo.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        start()
        toPreview("photo_mode_row_1")
        tap("food_photo_preview_use")

        pressBack()

        exists("food_photo_preview")
    }

    @Test
    fun scannerIcon_stillStartsTheScanner_andThePhotoFlowNeverTouchesIt() {
        start()
        toPreview("photo_mode_row_1")
        tap("food_photo_preview_use")
        assertEquals(0, scanner.scans)
        assertTrue(foods.barcodeLookups.isEmpty())
    }

    @Test
    fun scannerIcon_doesNotStartThePhotoFlow() {
        start()

        tap("food_search_action_scan")

        assertEquals(1, scanner.scans)
        assertTrue(capture.captured.isEmpty())
        absent("photo_mode")
    }
}
