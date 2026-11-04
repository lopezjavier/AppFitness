package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private typealias Fixtures = BlockBuilderTestData

/** The slot picker's "Añadir ejercicio propio" (R-48) and the favourite star (R-56). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockBuilderCustomExerciseTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockBuilderEvent>()

    private val patterns = listOf(MovementPattern.HorizontalPush, MovementPattern.Isolation)

    private fun show(state: BlockBuilderUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { BlockBuilderScreen(state, flowOf(online), { events += it }, {}) }
        }
        rule.waitForIdle()
    }

    private fun picker(createFailed: Boolean = false, favourites: Set<String> = emptySet()) = Fixtures.builderUi(
        openSlot = SlotRef(0, 0),
        pickerEntries = listOf(PickerEntry(Fixtures.bench), PickerEntry(Fixtures.fly)),
        favouriteIds = favourites,
        validPatterns = patterns,
        createFailed = createFailed,
    )

    private fun openForm() {
        rule.onNodeWithTag("block_builder_pick_add_custom").performClick()
        rule.waitForIdle()
    }

    @Test
    fun thePicker_pinsAddOwnExercise_andOpensTheForm() {
        show(picker())

        openForm()

        rule.onNodeWithText("Nuevo ejercicio").assertExists()
        rule.onNodeWithTag("block_builder_create_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun addIsOff_untilNameEquipmentAndPatternAreChosen() {
        show(picker())
        openForm()

        rule.onNodeWithTag("block_builder_create_name").performTextInput("Press landmine")
        rule.onNodeWithTag("block_builder_create_equipment_barbell").performClick()
        rule.onNodeWithTag("block_builder_create_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("block_builder_create_pattern_horizontal_push").performClick()

        rule.onNodeWithTag("block_builder_create_sheet_commit").assertIsEnabled()
    }

    @Test
    fun theForm_offersOnlyTheValidPatterns() {
        show(picker())
        openForm()

        rule.onNodeWithTag("block_builder_create_pattern_isolation").assertExists()
        rule.onNodeWithTag("block_builder_create_pattern_squat").assertDoesNotExist()
    }

    @Test
    fun adding_sendsTheCreateEventWithTheChosenValues() {
        show(picker())
        openForm()

        rule.onNodeWithTag("block_builder_create_name").performTextInput("Press landmine")
        rule.onNodeWithTag("block_builder_create_equipment_barbell").performClick()
        rule.onNodeWithTag("block_builder_create_pattern_isolation").performClick()
        rule.onNodeWithTag("block_builder_create_per_hand_switch").performClick()
        rule.onNodeWithTag("block_builder_create_sheet_commit").performClick()

        assertEquals(
            listOf<BlockBuilderEvent>(
                BlockBuilderEvent.CreateCustomExercise(
                    name = "Press landmine",
                    equipment = Equipment.Barbell,
                    movementPattern = MovementPattern.Isolation,
                    perHand = true,
                ),
            ),
            events,
        )
    }

    @Test
    fun aFailedCreate_saysSo_andKeepsTheForm() {
        show(picker(createFailed = true))
        openForm()

        rule.onNodeWithText("No se pudo crear el ejercicio. Inténtalo de nuevo.").assertExists()
    }

    @Test
    fun offline_addingIsDisabledWithTheHint() {
        show(picker(), online = false)

        rule.onNodeWithTag("block_builder_pick_add_custom").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede añadir un ejercicio", substring = true).assertExists()
    }

    @Test
    fun aFavouriteRow_showsTheStarAfterItsName_andOthersDoNot() {
        show(picker(favourites = setOf("bench")))

        rule.onNodeWithTag("block_builder_pick_favorite_bench", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("block_builder_pick_favorite_fly", useUnmergedTree = true).assertDoesNotExist()
    }
}
