package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun planKeys(vararg extra: PlanInput, current: CurrentPlan? = null) = PlanInputs(
    PlanInputKind.Plan,
    current,
    listOf(
        planInput(
            PlanInputKey.Intention,
            value = PlanInputValue.Text("fat_loss"),
            source = PlanInputSource.MyTraining,
            fixTarget = PlanFixTarget.MyTraining,
        ),
        planInput(PlanInputKey.Sex, value = PlanInputValue.Text("male"), fixTarget = PlanFixTarget.PersonalData),
        planInput(PlanInputKey.Age, value = PlanInputValue.Number(31.0), fixTarget = PlanFixTarget.PersonalData),
        planInput(PlanInputKey.Height, value = PlanInputValue.Number(178.0), fixTarget = PlanFixTarget.PersonalData),
        planInput(
            PlanInputKey.Weight,
            value = PlanInputValue.Number(80.0),
            source = PlanInputSource.WeighIn,
            fixTarget = PlanFixTarget.Weight,
            date = LocalDate(2026, 11, 1),
        ),
        planInput(
            PlanInputKey.ActivityLevel,
            value = PlanInputValue.Text("light"),
            source = PlanInputSource.CoachEstimate,
            fixTarget = PlanFixTarget.ActivitySheet,
        ),
    ) + extra,
)

/** US-78: "Antes de empezar", one test per Gherkin scenario, over the real view model and a fake repository. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class BeforeStartScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val connectivity = FakeConnectivityObserver()
    private val created = mutableListOf<BeforeStartEffect.Created>()
    private val fixed = mutableListOf<PlanFixTarget>()
    private val intake = FakeCoachIntakeRepository()
    private var yourDataOpened = 0
    private var backs = 0
    private lateinit var model: BeforeStartViewModel

    private fun show(mode: String = "plan", planId: String? = null, replacing: String? = null) {
        val args = mutableMapOf<String, Any?>("mode" to mode, "planId" to planId, "replacingPlanId" to replacing)
        model =
            BeforeStartViewModel(
                repository,
                intake,
                MutableClock(),
                FakeRoutineRepository(),
                SavedStateHandle(args),
            )
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = { backs++ },
                    onCreate = { created += it },
                    onOpenYourData = { yourDataOpened++ },
                    onFix = { fixed += it },
                    viewModel = model,
                    connectivity = connectivity,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun planStatus(key: PlanInputKey, status: PlanInputStatus, blocking: Boolean = false) =
        planInput(key, status, PlanInputValue.Absent, blocking = blocking, fixTarget = PlanFixTarget.MyTraining)

    @Test
    fun aCompleteProfile_showsTheSummaryWithoutFlags_andWritesNothing() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())

        show()

        rule.onNodeWithTag("before_start_screen").assertExists()
        rule.onNodeWithTag(
            "before_start_row_intention",
            useUnmergedTree = true,
        ).assertTextEquals("Objetivo · Perder grasa")
        rule.onNodeWithTag(
            "before_start_row_sex",
            useUnmergedTree = true,
        ).assertTextEquals("Sexo y edad · Hombre · 31 años")
        rule.onNodeWithTag(
            "before_start_row_height",
            useUnmergedTree = true,
        ).assertTextEquals("Altura y peso · 178 cm · 80,0 kg")
        rule.onNodeWithTag(
            "before_start_row_activity_level",
            useUnmergedTree = true,
        ).assertTextEquals("Nivel de actividad · Ligero")
        rule.onNodeWithTag("before_start_flag_intention").assertDoesNotExist()
        rule.onNodeWithTag("before_start_create").assertIsEnabled()
        assertTrue(repository.confirmCalls.isEmpty())
    }

    @Test
    fun theIntakeRows_showWhatWasSaid_andNeverBlockCreating() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        intake.stored = CoachIntake(trainingYears = 4.0)

        show()

        rule.onNodeWithTag("before_start_intake_block").assertExists()
        rule.onNodeWithTag("before_start_row_training_years").assertTextEquals("Tiempo entrenando", "4 años")
        rule.onNodeWithTag("before_start_row_maintenance")
            .assertTextEquals("Calorías de mantenimiento", "Sin indicar")
        rule.onNodeWithTag("before_start_create").assertIsEnabled()
    }

    @Test
    fun aBlock_hasOnlyTheYearsRow() {
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(PlanInputKind.Block, null, planKeys().inputs.take(1)),
        )

        show(mode = "block", planId = "plan-2")

        rule.onNodeWithTag("before_start_row_training_years").assertExists()
        rule.onNodeWithTag("before_start_row_free_text").assertDoesNotExist()
        rule.onNodeWithTag("before_start_row_maintenance").assertDoesNotExist()
    }

    @Test
    fun aBlock_doesNotOfferALowTimeShortcut() {
        show(mode = "block", planId = "plan-2")

        rule.onNodeWithText("Tengo poco tiempo").assertDoesNotExist()
    }

    @Test
    fun theFirstPlan_opensNothingOnItsOwn() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())

        show()

        assertTrue(fixed.isEmpty())
        rule.onNodeWithTag("before_start_screen").assertExists()
    }

    @Test
    fun tappingTheSummary_opensTusDatos() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        show()

        tap("before_start_summary")

        assertEquals(1, yourDataOpened)
        rule.onNodeWithTag("before_start_summary_chevron", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aMissingGoal_isFlagged_footerDisabledWithItsReason_thenEnabledAfterFixing() {
        val missingGoal = planStatus(PlanInputKey.Intention, PlanInputStatus.Missing, blocking = true)
        repository.inputs[PlanInputKind.Plan] =
            Result.success(PlanInputs(PlanInputKind.Plan, null, listOf(missingGoal)))
        show()

        rule.onNodeWithTag("before_start_flag_intention").assertExists()
        rule.onNodeWithText("Falta: Objetivo").assertExists()
        rule.onNodeWithText("Preferencias").assertExists()
        rule.onNodeWithTag("before_start_create").assertIsNotEnabled()
        rule.onNodeWithTag("before_start_footer_reason")
            .assertTextEquals("Completa los datos marcados con «Falta:» para crear el plan.")

        tap("before_start_flag_intention")
        assertEquals(listOf(PlanFixTarget.MyTraining), fixed)

        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        rule.runOnUiThread { model.onEvent(BeforeStartEvent.Refresh) }
        rule.waitForIdle()

        rule.onNodeWithTag("before_start_flag_intention").assertDoesNotExist()
        rule.onNodeWithTag("before_start_footer_reason").assertDoesNotExist()
        rule.onNodeWithTag("before_start_create").assertIsEnabled()
    }

    @Test
    fun eachFlagOpensItsOwnEditor() {
        repository.inputs[PlanInputKind.Plan] = Result.success(
            PlanInputs(
                PlanInputKind.Plan,
                null,
                listOf(
                    planInput(
                        PlanInputKey.Sex,
                        PlanInputStatus.Missing,
                        PlanInputValue.Absent,
                        blocking = true,
                        fixTarget = PlanFixTarget.PersonalData,
                    ),
                    planInput(
                        PlanInputKey.Weight,
                        PlanInputStatus.Missing,
                        PlanInputValue.Absent,
                        PlanInputSource.WeighIn,
                        blocking = true,
                        fixTarget = PlanFixTarget.Weight,
                    ),
                ),
            ),
        )
        show()

        tap("before_start_flag_sex")
        tap("before_start_flag_weight")

        assertEquals(listOf(PlanFixTarget.PersonalData, PlanFixTarget.Weight), fixed)
    }

    @Test
    fun aFlaggedActivityLevel_opensTheSheet_andTappingACardConfirmsAtOnce() {
        repository.inputs[PlanInputKind.Plan] = Result.success(
            PlanInputs(
                PlanInputKind.Plan,
                null,
                listOf(
                    planInput(
                        PlanInputKey.ActivityLevel,
                        PlanInputStatus.Missing,
                        PlanInputValue.Absent,
                        blocking = true,
                        fixTarget = PlanFixTarget.ActivitySheet,
                    ),
                ),
            ),
        )
        show()

        tap("before_start_flag_activity_level")
        rule.onNodeWithTag("before_start_activity_sheet_activity_high").assertExists()
        tap("before_start_activity_sheet_activity_high")

        assertEquals(listOf<ActivityLevel?>(ActivityLevel.High), repository.confirmCalls.map { it.second })
        rule.onNodeWithTag("before_start_activity_sheet_activity_high").assertDoesNotExist()
    }

    @Test
    fun anOldWeighIn_showsACautionWithItsDate_andKeepsTheFooterEnabled() {
        val old = planKeys().inputs.map {
            if (it.key == PlanInputKey.Weight) {
                it.copy(status = PlanInputStatus.Stale, date = LocalDate(2026, 10, 24))
            } else {
                it
            }
        }
        repository.inputs[PlanInputKind.Plan] = Result.success(PlanInputs(PlanInputKind.Plan, null, old))

        show()

        rule.onNodeWithTag("before_start_note_stale_weight")
            .assertTextEquals("Tu último pesaje es del 24 de octubre. Pesarte de nuevo mejora el plan.")
        rule.onNodeWithTag(
            "before_start_row_height",
            useUnmergedTree = true,
        ).assertTextContains("24 de octubre", substring = true)
        rule.onNodeWithTag("before_start_create").assertIsEnabled()
    }

    @Test
    fun aNonBlockingGap_isANote_notAFlag() {
        repository.inputs[PlanInputKind.Plan] =
            Result.success(planKeys(planStatus(PlanInputKey.WeightTrend, PlanInputStatus.Missing)))

        show()

        rule.onNodeWithTag("before_start_note_missing_weight_trend")
            .assertTextEquals("Sin dato: Tendencia de peso. El entrenador usará una estimación.")
        rule.onNodeWithTag("before_start_flag_weight_trend").assertDoesNotExist()
        rule.onNodeWithTag("before_start_create").assertIsEnabled()
    }

    @Test
    fun withAnAcceptedPlan_theReplaceNoteSaysTheCurrentOneStaysUntilAccepted() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys(current = CurrentPlan.Accepted))

        show(replacing = "plan-1")

        rule.onNodeWithTag("before_start_note_replace").assertTextEquals(
            "Sustituirá tu plan actual. Hasta que aceptes el nuevo, sigue valiendo el de ahora.",
        )
    }

    @Test
    fun withAProposedPlan_theReplaceNoteMentionsThePendingProposal() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys(current = CurrentPlan.Proposed))

        show(replacing = "plan-2")

        rule.onNodeWithTag("before_start_note_replace")
            .assertTextEquals("Sustituirá la propuesta pendiente de tu plan.")
    }

    @Test
    fun withoutAPlanToReplace_thereIsNoReplaceNote() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())

        show()

        rule.onNodeWithTag("before_start_note_replace").assertDoesNotExist()
    }

    @Test
    fun theBlock_hasItsOwnLines_andLeavesOptionalDataToTheCoach() {
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(
                PlanInputKind.Block,
                null,
                listOf(
                    planInput(PlanInputKey.Intention, value = PlanInputValue.Text("muscle")),
                    planInput(PlanInputKey.Place, value = PlanInputValue.Text("gym")),
                    planInput(
                        PlanInputKey.Equipment,
                        PlanInputStatus.UnsetOptional,
                        PlanInputValue.Items(emptyList()),
                        PlanInputSource.MyTraining,
                    ),
                    planInput(
                        PlanInputKey.DaysPerWeek,
                        PlanInputStatus.UnsetOptional,
                        PlanInputValue.Absent,
                        PlanInputSource.MyTraining,
                    ),
                    planInput(
                        PlanInputKey.SessionMinutes,
                        value = PlanInputValue.Number(90.0),
                        source = PlanInputSource.MyTraining,
                    ),
                ),
            ),
        )

        show(mode = "block", planId = "plan-3")

        rule.onNodeWithTag(
            "before_start_row_intention",
            useUnmergedTree = true,
        ).assertTextEquals("Objetivo · Ganar musculatura")
        rule.onNodeWithTag("before_start_row_place", useUnmergedTree = true).assertTextEquals("Lugar · Gimnasio")
        rule.onNodeWithTag("before_start_row_equipment", useUnmergedTree = true)
            .assertTextEquals("Material · Sin definir · Lo elige el entrenador")
        rule.onNodeWithTag("before_start_row_days_per_week", useUnmergedTree = true)
            .assertTextEquals("Días por semana · Sin definir · Lo elige el entrenador")
        rule.onNodeWithTag(
            "before_start_row_session_minutes",
            useUnmergedTree = true,
        ).assertTextEquals("Duración del entreno · 1 h 30 min")
        rule.onNodeWithTag("before_start_flag_equipment").assertDoesNotExist()
        rule.onNodeWithText("Crear bloque").assertExists()
        rule.onNodeWithTag("before_start_note_replace").assertDoesNotExist()
    }

    @Test
    fun theCardioRow_listsThePickedMachines() {
        blockWithCardio(listOf("cardio_machine"))

        show(mode = "block", planId = "plan-3")

        rule.onNodeWithTag("before_start_row_cardio", useUnmergedTree = true)
            .assertTextEquals("Cardio · Máquina de cardio")
    }

    @Test
    fun theCardioRow_withNoMachine_readsWalking_withoutSinDefinir() {
        blockWithCardio(emptyList())

        show(mode = "block", planId = "plan-3")

        rule.onNodeWithTag("before_start_row_cardio", useUnmergedTree = true)
            .assertTextEquals("Cardio · Sin máquinas · caminar")
    }

    private fun blockWithCardio(items: List<String>) {
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(
                PlanInputKind.Block,
                null,
                listOf(
                    planInput(
                        PlanInputKey.Cardio,
                        value = PlanInputValue.Items(items),
                        source = PlanInputSource.MyTraining,
                    ),
                ),
            ),
        )
    }

    @Test
    fun creating_confirmsTheProvisionalValuesOnce_thenLeavesWithTheArguments() {
        val provisional = planKeys().inputs.map {
            if (it.key == PlanInputKey.ActivityLevel) it.copy(status = PlanInputStatus.Provisional) else it
        }
        repository.inputs[PlanInputKind.Plan] = Result.success(PlanInputs(PlanInputKind.Plan, null, provisional))
        show(replacing = "plan-9")
        rule.onNodeWithTag("before_start_confirm_line")
            .assertTextEquals("Al crear el plan se confirman los valores provisionales.")

        tap("before_start_create")

        assertEquals(1, repository.confirmCalls.size)
        assertEquals(listOf(BeforeStartEffect.Created(BeforeStartMode.Plan, null, "plan-9")), created)
    }

    @Test
    fun creating_withoutProvisionalValues_confirmsNothing() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        show()
        rule.onNodeWithTag("before_start_confirm_line").assertDoesNotExist()

        tap("before_start_create")

        assertTrue(repository.confirmCalls.isEmpty())
        assertEquals(1, created.size)
    }

    @Test
    fun aFailedConfirm_staysOnTheScreen_withTheSameValues() {
        val provisional = planKeys().inputs.map {
            if (it.key == PlanInputKey.ActivityLevel) it.copy(status = PlanInputStatus.Provisional) else it
        }
        repository.inputs[PlanInputKind.Plan] = Result.success(PlanInputs(PlanInputKind.Plan, null, provisional))
        repository.confirmResult = { Result.failure(AppException(AppError.Unknown)) }
        show()

        tap("before_start_create")

        assertTrue(created.isEmpty())
        rule.onNodeWithTag("before_start_create").assertIsEnabled()
        rule.onNodeWithTag("before_start_confirm_line").assertExists()
    }

    @Test
    fun leavingTheScreen_requestsNothing() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        show()

        tap("before_start_back")

        assertEquals(1, backs)
        assertTrue(repository.confirmCalls.isEmpty())
        assertTrue(created.isEmpty())
    }

    @Test
    fun offline_theFooterIsDisabledWithTheHint() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        connectivity.state.value = false

        show()

        rule.onNodeWithTag("before_start_create").assertIsNotEnabled()
        rule.onNodeWithTag("before_start_offline_hint").assertExists()
        rule.onNodeWithText("Sin conexión: no se puede crear el plan").assertExists()
    }

    @Test
    fun aLoadFailure_offersReintentar_thatRecovers() {
        repository.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Unknown))
        show()
        rule.onNodeWithText("No se pudieron cargar tus datos.").assertExists()
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())

        tap("before_start_error_retry")

        rule.onNodeWithTag("before_start_summary").assertExists()
    }

    @Test
    fun withoutConnection_showsTheOfflineState() {
        repository.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))

        show()

        rule.onNodeWithText("Sin conexión").assertExists()
        rule.onNodeWithTag("before_start_summary").assertDoesNotExist()
    }
}
