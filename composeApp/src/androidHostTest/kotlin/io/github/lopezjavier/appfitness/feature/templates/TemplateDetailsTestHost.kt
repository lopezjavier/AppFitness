package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository

/** A details view model over the fakes, as the host tests of the details screen build it. */
internal fun detailsModel(
    templates: MealTemplateRepository,
    connectivity: FakeConnectivityObserver,
    saved: SavedStateHandle = SavedStateHandle(),
    templateId: String = "t1",
    foods: FakeFoodRepository = FakeFoodRepository(),
) = TemplateDetailsViewModel(
    templateId = templateId,
    mealTemplates = templates,
    scaleNutrition = ScaleNutrition(),
    validateQuantity = ValidateQuantity(),
    validateTemplateName = ValidateTemplateName(),
    foodRepository = foods,
    connectivity = connectivity,
    savedState = saved,
)

/** What the host saw of the screen's callbacks. */
internal class DetailsCallbacks {
    var saved by mutableStateOf(false)
    var deleted by mutableStateOf(false)
}

/** The details screen wired to [model] the way [TemplateDetailsRoot] does it, without Koin or navigation. */
@Composable
internal fun DetailsHost(model: TemplateDetailsViewModel, callbacks: DetailsCallbacks = DetailsCallbacks()) {
    val state by model.uiState.collectAsState()
    var actionFailed by remember { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                TemplateDetailsEffect.Saved -> callbacks.saved = true
                TemplateDetailsEffect.Deleted -> callbacks.deleted = true
                is TemplateDetailsEffect.ActionFailed -> actionFailed = true
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
