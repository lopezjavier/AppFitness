package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.LocalBackInterceptor
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.feature.food.photo.FakeFoodPhotoRepository
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoEvent
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.foodPhotoModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SENTENCE = "pechuga de pollo con ensalada, 2 cervezas y 4 calamares"

/** US-132: search -> AI icon -> "Describir comida" -> typed text -> estimate -> prefilled form, with fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class MealTextFlowTest : PhotoFlowHost() {
    private fun toInput() {
        start()
        tap("food_search_action_ai")
        tap("photo_mode_row_4")
    }

    private fun typeAndSend(text: String = SENTENCE) {
        rule.onNodeWithTag("food_photo_text_input").performTextInput(text)
        rule.waitForIdle()
        tap("food_photo_text_submit")
    }

    @Test
    fun describeMeal_opensTheInput_withCancelAndEstimar_andNoCameraSteps() {
        toInput()

        exists("food_photo_text_step")
        exists("food_photo_text_input")
        exists("food_photo_text_cancel")
        rule.onNodeWithText("Estimar").assertExists()
        absent("photo_source")
        assertTrue(capture.captured.isEmpty())
    }

    @Test
    fun estimar_isDisabledWhileTheTextIsEmptyOrBlank() {
        toInput()

        rule.onNodeWithTag("food_photo_text_submit").assertIsNotEnabled()
        rule.onNodeWithTag("food_photo_text_input").performTextInput("   ")
        rule.waitForIdle()
        rule.onNodeWithTag("food_photo_text_submit").assertIsNotEnabled()
        rule.onNodeWithTag("food_photo_text_input").performTextInput("arroz")
        rule.waitForIdle()
        rule.onNodeWithTag("food_photo_text_submit").assertIsEnabled()
        rule.onNodeWithTag("food_photo_text_input_counter").assertTextContains("8 de 500")
    }

    @Test
    fun estimate_showsTheMealProgress_withoutActions() {
        photoRepo.gate = CompletableDeferred()
        toInput()

        typeAndSend()

        exists("food_photo_estimating")
        rule.onNodeWithText("Analizando tu comida…").assertExists()
        absent("food_photo_text_submit")
        assertEquals(SENTENCE, photoRepo.textCalls.single().first)
    }

    @Test
    fun drinkDraft_opensTheEditableForm_markedAsEstimate_andSaveCreatesFoodAndOpensTheSheet() {
        photoRepo.result = Result.success(
            FakeFoodPhotoRepository.lentils.copy(name = "Pechuga, ensalada y 2 cervezas", alcohol = 3.0),
        )
        toInput()

        typeAndSend()

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Pechuga, ensalada y 2 cervezas")
        rule.onNodeWithTag("create_food_alcohol_field").assertTextContains("3")
        rule.onNodeWithTag("create_food_portion_label_0").assertTextContains("1 ración")
        rule.onNodeWithTag("create_food_ai_note").assertTextContains("Estimado por IA · revisa los valores")
        tap("create_food_save")
        assertEquals("Pechuga, ensalada y 2 cervezas", foods.created.single().name)
        exists("food_sheet")
    }

    @Test
    fun disagreeingDraft_blocksSave_andTheRetryCorrectsItWithTheSameText() {
        val failing = FakeFoodPhotoRepository.lentils.copy(kcal = 500.0, protein = 10.0, carbs = 30.0, fat = 10.0)
        photoRepo.results += Result.success(failing)
        photoRepo.results += Result.success(failing.copy(kcal = 250.0))
        toInput()
        typeAndSend()

        exists("create_food_energy_warning")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
        tap("create_food_energy_retry")

        absent("create_food_energy_warning")
        rule.onNodeWithTag("create_food_save").assertIsEnabled()
        assertEquals(listOf(SENTENCE, SENTENCE), photoRepo.textCalls.map { it.first })
    }

    @Test
    fun noFoodInTheText_showsTheMealMessage_keepsTheTextOnRetry_andCreatesNothing() {
        photoRepo.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        toInput()
        typeAndSend()

        rule.onNodeWithText("No hemos encontrado ningún alimento en el texto. Descríbelo con más detalle.")
            .assertExists()
        rule.onNodeWithText("Reintentar").assertExists()
        rule.onNodeWithText("Crear manualmente").assertExists()
        pressBack()
        exists("food_photo_text_step")
        rule.onNodeWithTag("food_photo_text_input").assertTextContains(SENTENCE)
        assertTrue(foods.created.isEmpty())
    }

    @Test
    fun noAnswer_retryResendsTheSameText() {
        photoRepo.result = Result.failure(AppException(AppError.ServiceUnavailable))
        toInput()
        typeAndSend()

        photoRepo.result = Result.success(FakeFoodPhotoRepository.lentils)
        tap("food_photo_error_retry")

        assertEquals(listOf(SENTENCE, SENTENCE), photoRepo.textCalls.map { it.first })
        exists("create_food_ai_note")
    }

    @Test
    fun offline_disablesEstimar_andShowsTheHint() {
        toInput()
        connectivity.state.value = false
        rule.waitForIdle()

        exists("food_photo_text_offline")
        rule.onNodeWithText("Sin conexión: no se puede estimar la comida").assertExists()
        rule.onNodeWithTag("food_photo_text_input").performTextInput("arroz")
        rule.waitForIdle()
        rule.onNodeWithTag("food_photo_text_submit").assertIsNotEnabled()
    }

    @Test
    fun cancelar_discardsTheTextWithoutDialog_andBackDoesTheSame() {
        toInput()
        rule.onNodeWithTag("food_photo_text_input").performTextInput("arroz")
        rule.waitForIdle()

        tap("food_photo_text_cancel")

        absent("food_photo_text_step")
        assertTrue(photoRepo.textCalls.isEmpty())
        assertTrue(foods.created.isEmpty())
    }

    @Test
    fun typedText_survivesRecreation() {
        val saved = SavedStateHandle()
        val model = foodPhotoModel(photoRepo, capture, connectivity, saved, retryStore = retryStore)
        val tester = StateRestorationTester(rule)
        tester.setContent { TextHost(model) }
        rule.runOnUiThread { model.onEvent(FoodPhotoEvent.Open) }
        rule.runOnUiThread { model.onEvent(FoodPhotoEvent.ModeChosen(FoodPhotoMode.Text)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_photo_text_input").performTextInput("arroz con pollo")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_photo_text_input").assertTextContains("arroz con pollo")
    }

    @Composable
    private fun TextHost(model: FoodPhotoViewModel) {
        val state by model.uiState.collectAsState()
        CompositionLocalProvider(LocalBackInterceptor provides backInterceptor) {
            AppTheme { FoodPhotoFlow(state, model::onEvent, capture::readJpeg) }
        }
    }
}
