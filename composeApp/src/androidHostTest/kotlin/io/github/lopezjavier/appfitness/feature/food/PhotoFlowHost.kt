package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.BackInterceptor
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.LocalBackInterceptor
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraft
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
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
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoRetryStore
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.foodPhotoModel
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule

/** Host shared by the "add with AI" host tests: search + AI flow + prefilled form + add sheet, all over fakes. */
abstract class PhotoFlowHost {
    @get:Rule
    val rule = createComposeRule()

    internal val foods = FakeFoodRepository()
    internal val diary = FakeFoodDiaryRepository()
    internal val connectivity = FakeConnectivityObserver()
    internal val scanner = FakeBarcodeScanner()
    internal val capture = FakePhotoCapture()
    internal val photoRepo = FakeFoodPhotoRepository()
    internal val retryStore = FoodPhotoRetryStore(capture)
    internal val backInterceptor = BackInterceptor()

    internal fun start() {
        val searchModel = foodSearchModel(foods, connectivity, scanner, SavedStateHandle())
        val photoModel = foodPhotoModel(photoRepo, capture, connectivity, retryStore = retryStore)
        rule.setContent { Flow(searchModel, photoModel) }
        rule.waitForIdle()
    }

    @Composable
    @Suppress("ViewModelForwarding") // Test host: the flow takes the view models the test built.
    internal fun Flow(searchModel: FoodSearchViewModel, photoModel: FoodPhotoViewModel) {
        var form by remember { mutableStateOf<NewFoodDraft?>(null) }
        var retryToken by remember { mutableStateOf<String?>(null) }
        var formOpen by remember { mutableStateOf(false) }
        var openFoodId by remember { mutableStateOf<String?>(null) }
        val searchState by searchModel.uiState.collectAsState()
        val photoState by photoModel.uiState.collectAsState()
        LaunchedEffect(photoModel) {
            photoModel.effects.collect {
                when (it) {
                    is FoodPhotoEffect.OpenPrefilledForm -> {
                        form = it.draft
                        retryToken = it.retryToken
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
                    Form(form, retryToken) {
                        openFoodId = it
                        formOpen = false
                    }
                }
                openFoodId?.let { id -> Sheet(id) { openFoodId = null } }
            }
        }
    }

    @Composable
    internal fun Form(draft: NewFoodDraft?, retryToken: String?, onSave: (String) -> Unit) {
        val currentOnSave by rememberUpdatedState(onSave)
        val model = remember {
            CreateFoodViewModel(
                null,
                null,
                foods,
                connectivity,
                prefill = draft?.let(NewFoodDraftCodec::encode),
                retryToken = retryToken,
                photoRepository = photoRepo,
                photoCapture = capture,
                retryStore = retryStore,
            )
        }
        val createState by model.uiState.collectAsState()
        LaunchedEffect(model) {
            model.effects.collect { if (it is CreateFoodEffect.Saved) currentOnSave(it.foodId) }
        }
        CreateFoodScreen(createState, model::onEvent, {})
    }

    @Composable
    internal fun Sheet(foodId: String, onDismiss: () -> Unit) {
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

    internal fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    internal fun pressBack() {
        rule.runOnUiThread { backInterceptor.intercept() }
        rule.waitForIdle()
    }

    internal fun exists(tag: String) = rule.onNodeWithTag(tag).assertExists()

    internal fun absent(tag: String) = rule.onNodeWithTag(tag).assertDoesNotExist()
}
