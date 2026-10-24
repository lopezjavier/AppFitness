package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.AddTemplateToDiary
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** A comida-details view model over the fakes, as the host tests of the preview screen build it. */
internal fun previewModel(
    templates: MealTemplateRepository,
    diary: FakeFoodDiaryRepository,
    connectivity: FakeConnectivityObserver,
    saved: SavedStateHandle = SavedStateHandle(),
    templateId: String = "t1",
    slotId: String? = "lunch",
) = TemplatePreviewViewModel(
    templateId = templateId,
    slotId = slotId,
    date = LocalDate(2026, 10, 19),
    mealTemplates = templates,
    scaleNutrition = ScaleNutrition(),
    addTemplateToDiary = AddTemplateToDiary(diary, ScaleNutrition()),
    mealSlots = FakeMealSlotRepository(),
    defaultMealSlot = DefaultMealSlot(),
    clock = MutableClock(),
    connectivity = connectivity,
    savedState = saved,
    timeZone = TimeZone.UTC,
)

/** What the host saw of the screen's callbacks. */
internal class PreviewCallbacks {
    var added by mutableIntStateOf(0)
    var edited by mutableIntStateOf(0)
}

/** The details wired to [model] the way [TemplatePreviewRoot] does it, without Koin (navigation is the caller's). */
@Composable
internal fun PreviewHost(
    model: TemplatePreviewViewModel,
    callbacks: PreviewCallbacks = PreviewCallbacks(),
    onAdd: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val state by model.uiState.collectAsState()
    val currentAdded by rememberUpdatedState(onAdd)
    val currentEdit by rememberUpdatedState(onEdit)
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                TemplatePreviewEffect.Added -> {
                    callbacks.added++
                    currentAdded()
                }

                is TemplatePreviewEffect.Failed -> Unit

                TemplatePreviewEffect.OpenEditor -> {
                    callbacks.edited++
                    currentEdit()
                }
            }
        }
    }
    AppTheme { TemplatePreviewScreen(state, model::onEvent) }
}
