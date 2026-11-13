package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.port.CaptureResult
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import io.github.lopezjavier.appfitness.feature.food.photo.FakeFoodPhotoRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-48 / US-49: search -> AI icon -> sheets -> preview -> estimate -> prefilled form -> sheet, with fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class FoodPhotoFlowTest : PhotoFlowHost() {
    private fun toPreview(modeTag: String, sourceTag: String = "photo_source_row_1") {
        tap("food_search_action_ai")
        tap(modeTag)
        tap(sourceTag)
    }

    @Test
    fun actionIcons_sitInTheSearchFieldRow_withTheSameSize_andADescription() {
        start()

        val field = rule.onNodeWithTag("food_search_field").getUnclippedBoundsInRoot()
        val scan = rule.onNodeWithTag("food_search_action_scan").getUnclippedBoundsInRoot()
        val photo = rule.onNodeWithTag("food_search_action_ai").getUnclippedBoundsInRoot()
        val create = rule.onNodeWithTag("food_search_action_create").getUnclippedBoundsInRoot()
        assertTrue("the scan sits inside the field", scan.left >= field.left && scan.right <= field.right)
        assertTrue("the top-bar actions sit above the field", photo.bottom <= field.top && create.bottom <= field.top)
        assertTrue(photo.right <= create.left)
        assertEquals(photo.top, create.top)
        assertTrue(photo.bottom - photo.top >= 48.dp)
        rule.onNodeWithTag("food_search_action_ai").assertIsEnabled()
        rule.onNodeWithTag("food_search_action_ai").assertContentDescriptionEquals("Añadir con IA")
    }

    @Test
    fun photoAction_offline_isDisabled_withoutAStateDescription() {
        connectivity.state.value = false
        start()

        rule.onNodeWithTag("food_search_action_ai").assertIsNotEnabled()
        val config = rule.onNodeWithTag("food_search_action_ai").fetchSemanticsNode().config
        assertTrue(SemanticsProperties.StateDescription !in config)
    }

    @Test
    fun aiIcon_opensTheModeSheetWithFourRows() {
        start()

        tap("food_search_action_ai")

        exists("photo_mode")
        exists("photo_mode_row_1")
        exists("photo_mode_row_2")
        exists("photo_mode_row_3")
        exists("photo_mode_row_4")
        rule.onNodeWithText("Añadir con IA").assertExists()
        rule.onNodeWithText("Describir comida").assertExists()
        rule.onNodeWithText("Foto de un plato: calorías y macros").assertExists()
    }

    @Test
    fun choosingAMode_opensTheSourceSheetWithTwoRows() {
        start()

        tap("food_search_action_ai")
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
            .assertTextContains("No se pudo abrir la cámara. Inténtalo de nuevo.")
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
        rule.onNodeWithTag("food_photo_estimating_progress", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Analizando tu foto…").assertExists()
        rule.onNode(stateDescriptionIs("Analizando tu foto…")).assertExists()
        absent("food_photo_preview_use")
        pressBack()
        exists("food_photo_estimating")
    }

    @Test
    fun dishEstimateWithAlcohol_prefillsTheField_andTheEnergyCheckStillRuns() {
        photoRepo.result = Result.success(
            FakeFoodPhotoRepository.lentils.copy(
                name = "Vino tinto",
                kcal = 83.0,
                protein = 0.1,
                carbs = 2.6,
                fat = 0.0,
                alcohol = 9.5,
            ),
        )
        start()
        toPreview("photo_mode_row_1")
        tap("food_photo_preview_use")

        tap("create_food_section_alcohol_toggle")
        rule.onNodeWithTag("create_food_alcohol_field").assertTextContains("9,5")
        tap("create_food_alcohol_help")
        tap("create_food_alcohol_help_sheet_close")
        rule.onNodeWithTag("create_food_alcohol_field").performTextReplacement("")
        rule.waitForIdle()

        exists("create_food_energy_warning")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
    }

    @Test
    fun failingEstimate_opensTheFormBlocked_andTheRetryIconCorrectsIt() {
        val failing = FakeFoodPhotoRepository.lentils.copy(kcal = 500.0, protein = 10.0, carbs = 30.0, fat = 10.0)
        photoRepo.results += Result.success(failing)
        photoRepo.results += Result.success(failing.copy(kcal = 250.0))
        start()
        toPreview("photo_mode_row_1")
        tap("food_photo_preview_use")

        exists("create_food_energy_warning")
        rule.onNodeWithTag("create_food_save").assertIsNotEnabled()
        rule.onNodeWithTag("create_food_energy_retry").assertContentDescriptionEquals("Repetir la estimación")
        tap("create_food_energy_retry")

        absent("create_food_energy_warning")
        absent("create_food_energy_retry")
        rule.onNodeWithTag("create_food_kcal_field").assertTextContains("250")
        rule.onNodeWithTag("create_food_save").assertIsEnabled()
        assertEquals(2, photoRepo.calls.size)
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
        rule.onNodeWithText("No hemos podido estimar este alimento.").assertExists()
        rule.onNodeWithText("Reintentar").assertExists()
        rule.onNodeWithText("Crear manualmente").assertExists()
    }

    @Test
    fun notRecognisedBarcode_showsTheProductMessage() {
        photoRepo.result = Result.failure(AppException(AppError.PhotoNotRecognized))
        start()
        toPreview("photo_mode_row_3")

        tap("food_photo_preview_use")

        rule.onNodeWithText("No hemos podido identificar este producto.").assertExists()
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
            "El servicio no está disponible ahora. Inténtalo más tarde o crea el alimento manualmente.",
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
