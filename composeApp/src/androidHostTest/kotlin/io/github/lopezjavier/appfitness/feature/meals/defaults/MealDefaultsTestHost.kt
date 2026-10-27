package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.usecase.CookedItemNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.meals.RecordingMessages
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import kotlinx.coroutines.runBlocking

/** Real [MealDefaultsViewModel] over fakes plus the screen, shared by the flow and restoration tests. */
internal class MealDefaultsTestHost(private val rule: ComposeContentTestRule) {
    val slots = FakeMealSlotRepository()
    val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to food("eggs", kcal = 100.0)))
    val defaults = FakeMealDefaultsRepository()
    val connectivity = FakeConnectivityObserver()
    var closed = 0
    val messages = RecordingMessages()

    fun comida(name: String): String = runBlocking {
        templates.save(null, name, null, listOf(NewTemplateItem("eggs", 100.0, null))).getOrThrow()
    }

    fun viewModel(state: SavedStateHandle = SavedStateHandle()) = MealDefaultsViewModel(
        slots,
        templates,
        defaults,
        FakeFoodPreferencesRepository(),
        CookedItemNutrition(ScaleNutrition()),
        connectivity,
        state,
    )

    @Composable
    fun Content(model: MealDefaultsViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme {
            CompositionLocalProvider(LocalAppMessages provides messages) {
                MealDefaultsScreen(state, model::onEvent, onClose = { closed++ })
            }
        }
    }

    fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }
}
