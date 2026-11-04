package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.Pick
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerDisabledReason
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private typealias Data = BlockBuilderTestData

/** US-72 builder scenarios that have a UI: sections, slots, picker, score and save footer, cardio, extras, discard. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One scenario per test.
class BlockBuilderScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockBuilderEvent>()
    private val opened = mutableListOf<String>()

    private fun show(state: BlockBuilderUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { BlockBuilderScreen(state, flowOf(online), { events += it }, { opened += it }) }
        }
        rule.waitForIdle()
    }

    @Test
    fun theSectionHeader_showsMuscleSetsRepsRpeAndRest() {
        show(Data.builderUi())

        rule.onNodeWithTag("block_builder_section_0_chest").assertTextContains("Pecho · ", substring = true)
        rule.onNodeWithTag("block_builder_section_0_chest").assertTextContains("series · ", substring = true)
        rule.onNodeWithTag("block_builder_section_0_chest").assertTextContains("repeticiones · RPE 8 · descanso", true)
    }

    @Test
    fun strengthStartsWithTheMainLift_namedBasico() {
        show(Data.builderUi(Data.start(intention = TrainingIntention.Strength)))

        rule.onNodeWithTag(
            "block_builder_section_0_chest_main",
        ).assertTextContains("Básico · 3 series · 4-6 repeticiones", substring = true)
    }

    @Test
    fun theQuestionMark_explainsTheSection() {
        show(Data.builderUi())

        rule.onNodeWithTag("block_builder_section_help_0_chest").performClick()

        rule.onNodeWithText("Cada músculo necesita al menos 10 series", substring = true).assertExists()
    }

    @Test
    fun anEmptySlot_saysChooseAnExercise_andOpensThePicker() {
        show(Data.builderUi())

        rule.onNodeWithTag("block_builder_slot_0_0").assertTextContains("Elige un ejercicio", substring = true)
        rule.onNodeWithTag("block_builder_slot_0_0").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.OpenSlot(SlotRef(0, 0))), events)
    }

    @Test
    fun aFilledSlot_showsTheRealName_swapsAndClears() {
        show(Data.builderUi(Data.filled(Data.start())))

        rule.onNodeWithTag("block_builder_slot_0_0").assertTextContains("Press banca", substring = true)
        rule.onNodeWithTag("block_builder_slot_swap_0_0").performClick()
        rule.onNodeWithTag("block_builder_slot_clear_0_0").performClick()
        rule.onNodeWithTag("block_builder_slot_0_0").performClick()

        assertEquals(
            listOf(
                BlockBuilderEvent.OpenSlot(SlotRef(0, 0)),
                BlockBuilderEvent.ClearSlot(0, 0),
            ),
            events,
        )
        assertEquals(listOf("bench"), opened.distinct())
    }

    @Test
    fun theDayTabs_switchTheDay() {
        show(Data.builderUi())

        rule.onNodeWithTag("block_builder_day_tab_1").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.SelectDay(1)), events)
    }

    @Test
    fun theSecondDayShowsItsOwnRoutine() {
        show(Data.builderUi(selectedDay = 1))

        rule.onNodeWithTag("block_builder_day_title_1").assertTextContains("Pierna", substring = true)
        rule.onNodeWithTag("block_builder_section_1_quads").assertExists()
    }

    @Test
    fun theScoreIsABarWithText_andSaveIsOffUntil100() {
        show(Data.builderUi())

        rule.onNodeWithTag("block_builder_score").assertTextContains("Rutina completa al 0 %", substring = true)
        rule.onNodeWithTag("block_builder_save").assertIsNotEnabled()
        rule.onNodeWithText("Elige un ejercicio en cada hueco para guardar.").assertExists()
    }

    @Test
    fun at100_saveIsEnabledAndSends() {
        show(Data.builderUi(Data.filled(Data.start())))

        rule.onNodeWithTag("block_builder_score").assertTextContains("Rutina completa al 100 %", substring = true)
        rule.onNodeWithTag("block_builder_save").assertIsEnabled().performClick()
        rule.onAllNodesWithText("Elige un ejercicio en cada hueco para guardar.").assertCountEquals(0)

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.Save), events)
    }

    @Test
    fun offline_saveIsDisabledWithTheHint() {
        show(Data.builderUi(Data.filled(Data.start())), online = false)

        rule.onNodeWithTag("block_builder_save").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede guardar el bloque").assertExists()
    }

    @Test
    fun aFailedSave_saysSo_andTheBlockIsStillSavable() {
        show(Data.builderUi(Data.filled(Data.start()), saveError = BuilderSaveError.Generic))

        rule.onNodeWithText("No se pudo guardar el bloque. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("block_builder_save").assertIsEnabled()
    }

    @Test
    fun aClosedPlan_saysSo_andDisablesSave() {
        show(Data.builderUi(Data.filled(Data.start()), saveError = BuilderSaveError.PlanClosed))

        rule.onNodeWithText("El plan ya no está abierto. Vuelve a Trainer y empieza de nuevo.").assertExists()
        rule.onNodeWithTag("block_builder_save").assertIsNotEnabled()
    }

    @Test
    fun belowRecommended_showsTheNoteWithItsHelp_onlyWhenSetsWereCut() {
        val cut = Data.start().let { it.copy(days = it.days.map { d -> d.copy(cutSets = true) }) }
        show(Data.builderUi(cut))

        rule.onNodeWithText("Por debajo de lo recomendado").assertExists()
        rule.onNodeWithTag("block_builder_below_note_help").assertExists()
    }

    @Test
    fun noNote_whenNoSetsWereCut() {
        show(Data.builderUi())

        rule.onAllNodesWithText("Por debajo de lo recomendado").assertCountEquals(0)
    }

    @Test
    fun extras_addAndRemove_andNeverMentionTheScore() {
        val withExtra = Data.start().addExtra(
            0,
            Pick("fly", Data.fly.exercise.muscleGroup, Data.fly.exercise.movementPattern, null),
        )
        show(Data.builderUi(withExtra))

        rule.onNodeWithText("Extra (opcional)").assertExists()
        rule.onNodeWithTag("block_builder_extra_add_0").performClick()
        rule.onNodeWithTag("block_builder_extra_remove_0_0").performClick()
        rule.onNodeWithTag("block_builder_score").assertTextContains("Rutina completa al 0 %", substring = true)

        assertEquals(
            listOf(BlockBuilderEvent.AddExtra(0), BlockBuilderEvent.RemoveExtra(0, 0)),
            events,
        )
    }

    @Test
    fun cardio_isTheLastRow_withMinutes_andTheHonestNote() {
        show(Data.builderUi(Data.start(cardioTarget = 150)))

        rule.onNodeWithText("Bicicleta estática · 60 min · ritmo suave").assertExists()
        rule.onNodeWithText("Te faltan 30 min de cardio a la semana: hazlos por tu cuenta.").assertExists()
    }

    @Test
    fun cardio_explanationSitsBehindTheQuestionMark() {
        show(Data.builderUi())

        rule.onAllNodesWithText("Cuentan los mismos minutos con cualquier cardio, a ritmo suave.").assertCountEquals(0)
        rule.onNodeWithTag("block_builder_cardio_help_0").performClick()

        rule.onNodeWithText("Cuentan los mismos minutos con cualquier cardio, a ritmo suave.").assertExists()
    }

    @Test
    fun swappingTheCardio_picksFromTheAllowedOnes() {
        show(Data.builderUi())

        rule.onNodeWithTag("block_builder_cardio_swap_0").performClick()
        rule.onNodeWithTag("block_builder_cardio_option_rower").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.SwapCardio("rower")), events)
    }

    @Test
    fun thePicker_listsRows_disabledOnesKeepTheirReason_andOnlyEnabledOnesPick() {
        val entries = listOf(
            PickerEntry(Data.bench),
            PickerEntry(Data.fly, PickerDisabledReason.AlreadyInRoutine),
        )
        show(Data.builderUi(openSlot = SlotRef(0, 0), pickerEntries = entries))

        rule.onNodeWithText("Elegir ejercicio").assertExists()
        rule.onNodeWithText("Ya está en esta rutina").assertExists()
        rule.onNodeWithTag("block_builder_pick_fly").performClick()
        rule.onNodeWithTag("block_builder_pick_bench").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.PickExercise("bench")), events)
    }

    @Test
    fun anExerciseWithoutAMovementPattern_isListedDisabledWithTheReason() {
        val entries = listOf(PickerEntry(Data.fly, PickerDisabledReason.NoMovementPattern))
        show(Data.builderUi(openSlot = SlotRef(0, 0), pickerEntries = entries))

        rule.onNodeWithText("Sin patrón de movimiento: defínelo en el ejercicio").assertExists()
        rule.onNodeWithTag("block_builder_pick_fly").performClick()

        assertEquals(emptyList<BlockBuilderEvent>(), events)
    }

    @Test
    fun theSearch_filtersThePickerByName() {
        val entries = listOf(PickerEntry(Data.bench), PickerEntry(Data.fly))
        show(Data.builderUi(openSlot = SlotRef(0, 0), pickerEntries = entries))

        rule.onNodeWithTag("block_builder_picker_search").performTextInput("aper")

        rule.onNodeWithTag("block_builder_pick_bench").assertDoesNotExist()
        rule.onNodeWithTag("block_builder_pick_fly").assertExists()
    }

    @Test
    fun aFilledSlotsPicker_saysChangeExercise() {
        show(
            Data.builderUi(
                Data.filled(Data.start()),
                openSlot = SlotRef(0, 0),
                pickerEntries = listOf(PickerEntry(Data.bench)),
            ),
        )

        rule.onNodeWithText("Cambiar ejercicio").assertExists()
    }

    @Test
    fun discardDialog_cancelKeepsEditing() {
        show(Data.builderUi(discardDialog = true))

        rule.onNodeWithText("¿Descartar los cambios?").assertExists()
        rule.onNodeWithTag("block_builder_discard_cancel").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.CancelDiscard), events)
    }

    @Test
    fun discardDialog_confirmDiscards() {
        show(Data.builderUi(discardDialog = true))

        rule.onNodeWithTag("block_builder_discard_confirm").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.ConfirmDiscard), events)
    }

    @Test
    fun noDiscomfortInputAnywhere() {
        show(Data.builderUi())

        listOf("molestia", "dolor", "lesión").forEach {
            rule.onAllNodesWithText(it, substring = true, ignoreCase = true).assertCountEquals(0)
        }
    }

    @Test
    fun loading_showsASkeleton() {
        show(BlockBuilderUiState.Loading)

        rule.onNodeWithTag("block_builder_loading").assertExists()
    }

    @Test
    fun failed_offersRetry() {
        show(BlockBuilderUiState.Failed(offline = false))

        rule.onNodeWithTag("block_builder_retry").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.Retry), events)
    }

    @Test
    fun typesRoutine_namesRepeatWithALetter() {
        show(
            Data.builderUi(
                Data.start(listOf(RoutineType.Upper, RoutineType.Lower, RoutineType.Upper, RoutineType.Lower)),
            ),
        )

        rule.onNodeWithTag("block_builder_day_title_0").assertTextContains("Torso A", substring = true)
    }
}
