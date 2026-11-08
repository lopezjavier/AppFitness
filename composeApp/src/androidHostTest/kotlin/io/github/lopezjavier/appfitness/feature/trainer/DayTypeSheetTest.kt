package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 "A routine type per day", "A type that leaves a muscle without a day is disabled", strength advice. */
@Suppress("TooManyFunctions") // One method per scenario.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DayTypeSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockBuilderEvent>()

    private fun typesState(
        chosen: List<RoutineType> = listOf(RoutineType.Upper, RoutineType.Lower, RoutineType.Upper, RoutineType.Lower),
        openDay: Int? = null,
        intention: TrainingIntention? = TrainingIntention.Muscle,
        noFit: NoFitInfo? = null,
        splitIndex: Int = 0,
        daysChanged: Boolean = false,
    ): BlockBuilderUiState.Types {
        val split = SplitCatalog.splitsFor(chosen.size)[splitIndex]
        val options = chosen.indices.map { SplitCatalog.dayOptions(split, chosen, it, intention) }
        return BlockBuilderUiState.Types(split, chosen, options, openDay, noFit, daysChanged)
    }

    private fun show(state: BlockBuilderUiState.Types) {
        rule.setContent { AppTheme { BlockTypesScreen(state, { events += it }) } }
        rule.waitForIdle()
    }

    @Test
    fun eachDayShowsItsType_andTheRecommendedOneSaysSo() {
        show(typesState())

        rule.onNodeWithText("Rutina de cada día").assertExists()
        rule.onNodeWithTag("block_types_day_0").assertExists()
        rule.onNodeWithTag("block_types_day_0").assertTextContains("Torso · Recomendado", substring = true)
        rule.onNodeWithTag("block_types_day_1").assertTextContains("Pierna · Recomendado", substring = true)
    }

    @Test
    fun tappingADay_opensItsTypeSheet() {
        show(typesState())

        rule.onNodeWithTag("block_types_day_1").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.OpenDay(1)), events)
    }

    @Test
    fun theSheet_listsOnlyTheTypesOfTheSplitFamily_andATapAppliesIt() {
        show(typesState(openDay = 0))

        listOf("Upper", "Lower").forEach { rule.onNodeWithTag("block_type_option_$it").assertExists() }
        listOf("Push", "Pull", "FullBody").forEach { rule.onNodeWithTag("block_type_option_$it").assertDoesNotExist() }
        rule.onNodeWithText("Empuje").assertDoesNotExist()
        rule.onNodeWithText("Tirón").assertDoesNotExist()
        rule.onNodeWithTag("block_type_option_Lower").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.PickType(RoutineType.Lower)), events)
    }

    @Test
    fun eachTypeShowsItsMuscleGroupsUnderItsName() {
        show(typesState(openDay = 0))

        rule.onNodeWithText("Pecho, espalda y hombros").assertExists()
        rule.onNodeWithText("Cuádriceps, isquiotibiales y glúteos").assertExists()
    }

    @Test
    fun anEmpujeTironWeek_showsEmpujeWithItsGroups() {
        show(
            typesState(
                chosen = listOf(RoutineType.Push, RoutineType.Pull, RoutineType.Push, RoutineType.Pull),
                openDay = 0,
                splitIndex = 1,
            ),
        )

        rule.onNodeWithText("Pecho, hombros y cuádriceps").assertExists()
        rule.onNodeWithText("Espalda, isquiotibiales y glúteos").assertExists()
        rule.onNodeWithTag("block_type_option_Upper").assertDoesNotExist()
    }

    @Test
    fun theTypesStep_showsTheSplitNameAsAHeader() {
        show(typesState())

        rule.onNodeWithTag("block_types_split_name").assertTextContains("Torso-Pierna")
    }

    @Test
    fun theLoneCuerpoCompletoDayOfAThreeDayWeek_isAPlainRow_andTheOthersAreTappable() {
        val chosen = SplitCatalog.splitsFor(3)[1].types
        show(typesState(chosen = chosen, splitIndex = 1))

        rule.onNodeWithTag("block_types_split_name").assertTextContains("Torso-Pierna + Cuerpo completo")
        rule.onNodeWithTag("block_types_day_0").assertHasClickAction()
        val fbDay = chosen.indexOf(RoutineType.FullBody)
        rule.onNodeWithTag("block_types_day_$fbDay").assertHasNoClickAction()
        rule.onNodeWithText("Cuerpo completo").assertExists()
    }

    @Test
    fun aCuerpoCompletoSplit_hasNoTappableDay() {
        val chosen = SplitCatalog.splitsFor(3)[0].types
        show(typesState(chosen = chosen))

        rule.onNodeWithTag("block_types_split_name").assertTextContains("Cuerpo completo")
        chosen.indices.forEach { rule.onNodeWithTag("block_types_day_$it").assertHasNoClickAction() }
    }

    @Test
    fun aTypeThatLeavesAMuscleWithoutADay_isDisabledWithItsReason() {
        show(typesState(chosen = listOf(RoutineType.Upper, RoutineType.Lower), splitIndex = 1, openDay = 1))

        rule.onNodeWithTag("block_type_option_Upper").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("block_type_option_Lower").assertIsEnabled()

        assertEquals(emptyList<BlockBuilderEvent>(), events)
        rule.onAllNodesWithText("Dejaría los cuádriceps sin entrenar")[0].assertExists()
    }

    @Test
    fun whenARoutineDoesNotFit_continuarIsDisabledWithTheLine() {
        show(typesState(noFit = NoFitInfo(day = 1, neededMinutes = 95, limitMinutes = 45)))

        rule.onNodeWithTag("block_types_continue").assertIsNotEnabled()
        rule.onNodeWithText("No cabe en 45 min: sube la duración, los días o cambia la división.", substring = true)
            .assertExists()
    }

    @Test
    fun theNoFitNote_isATapToMiEntrenamientoWithAVisibleAction() {
        show(typesState(noFit = NoFitInfo(day = 1, neededMinutes = 95, limitMinutes = 45)))

        rule.onNodeWithText("Cambiar en Mi entrenamiento", substring = true).assertExists()
        rule.onNodeWithTag("block_types_no_fit").assertHasClickAction().performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.OpenMyTraining), events)
    }

    @Test
    fun afterTheDaysChanged_theScreenSaysToReviewTheTypes() {
        show(typesState(daysChanged = true))

        rule.onNodeWithTag("block_builder_days_changed_note").assertExists()
        rule.onNodeWithText("Has cambiado los días: revisa el tipo de cada día.").assertExists()
    }

    @Test
    fun withoutAChangeOfDays_thereIsNoReviewNote() {
        show(typesState())

        rule.onNodeWithTag("block_builder_days_changed_note").assertDoesNotExist()
    }

    @Test
    fun whenEverythingFits_continuarIsEnabledAndHasNoNote() {
        show(typesState())

        rule.onNodeWithTag("block_types_continue").assertIsEnabled().performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.Continue), events)
        rule.onNodeWithTag("block_types_no_fit").assertDoesNotExist()
    }

    @Test
    fun strengthWithAMuscleOnOneDay_showsTheHelpBehindAQuestionMark() {
        val state = typesState(
            chosen = listOf(RoutineType.Upper, RoutineType.Lower),
            intention = TrainingIntention.Strength,
            splitIndex = 1,
        )
        show(state)

        check(state.options.flatten().any { it.strengthNote }) { "The fixture needs a strength note" }
        rule.onNodeWithTag("block_type_help_0").performClick()

        rule.onNodeWithText("Para ganar fuerza conviene entrenar cada músculo al menos 2 días.").assertExists()
    }
}
