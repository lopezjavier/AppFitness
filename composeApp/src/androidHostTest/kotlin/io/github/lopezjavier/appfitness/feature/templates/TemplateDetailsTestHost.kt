package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate

/** A details view model over the fakes, as the host tests of the details screen build it. */
internal fun detailsModel(
    templates: MealTemplateRepository,
    connectivity: FakeConnectivityObserver,
    saved: SavedStateHandle = SavedStateHandle(),
    templateId: String = "t1",
    foods: FakeFoodRepository = FakeFoodRepository(),
    diary: FakeFoodDiaryRepository = FakeFoodDiaryRepository(),
    slotId: String? = "lunch",
) = TemplateDetailsViewModel(
    templateId = templateId,
    slotId = slotId,
    date = LocalDate(2026, 10, 19),
    mealTemplates = templates,
    scaleNutrition = ScaleNutrition(),
    validateQuantity = ValidateQuantity(),
    validateTemplateName = ValidateTemplateName(),
    foodRepository = foods,
    addTemplateToDiary = AddTemplateToDiary(diary, ScaleNutrition()),
    mealSlots = FakeMealSlotRepository(),
    defaultMealSlot = DefaultMealSlot(),
    clock = MutableClock(),
    connectivity = connectivity,
    savedState = saved,
)

/** What the host saw of the screen's callbacks. */
internal class DetailsCallbacks {
    var saved by mutableStateOf(false)
    var deleted by mutableStateOf(false)
    var pickerOpened by mutableIntStateOf(0)
    var added by mutableIntStateOf(0)
}

/** The editor wired to [model] the way [TemplateDetailsRoot] does it, without Koin (navigation is the caller's). */
@Composable
internal fun DetailsHost(
    model: TemplateDetailsViewModel,
    callbacks: DetailsCallbacks = DetailsCallbacks(),
    pickedFood: List<String>? = null,
    onConsumePickedFood: () -> Unit = {},
    onAddFood: () -> Unit = {},
    onAdd: () -> Unit = {},
) {
    val state by model.uiState.collectAsState()
    var actionFailed by remember { mutableStateOf(false) }
    val currentConsume by rememberUpdatedState(onConsumePickedFood)
    val currentAddFood by rememberUpdatedState(onAddFood)
    val currentAdded by rememberUpdatedState(onAdd)
    LaunchedEffect(pickedFood) {
        pickedFoodOf(pickedFood)?.let {
            model.onEvent(it)
            currentConsume()
        }
    }
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                TemplateDetailsEffect.Saved -> callbacks.saved = true

                TemplateDetailsEffect.Added -> {
                    callbacks.added++
                    currentAdded()
                }

                TemplateDetailsEffect.Deleted -> callbacks.deleted = true

                is TemplateDetailsEffect.ActionFailed -> actionFailed = true

                TemplateDetailsEffect.OpenFoodPicker -> {
                    callbacks.pickerOpened++
                    currentAddFood()
                }
            }
        }
    }
    AppTheme {
        TemplateDetailsScreen(
            state = state,
            onEvent = model::onEvent,
            actionFailed = actionFailed,
        )
    }
}
