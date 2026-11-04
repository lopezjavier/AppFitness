package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-93: "Crear bloque" asks how the block starts; "Desde una carpeta" lists the folders and opens the review. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockStartChoiceScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val routines = FakeRoutineRepository().apply {
        folders += RoutineFolder("f1", "Torso-pierna")
        folders += RoutineFolder("f2", "Fuerza")
    }
    private val created = mutableListOf<BeforeStartEffect.Created>()
    private val reviews = mutableListOf<BeforeStartEffect.OpenFolderBlock>()

    private fun show(mode: String = "block", saved: Map<String, Any?> = emptyMap()) {
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(
                PlanInputKind.Block,
                null,
                listOf(planInput(PlanInputKey.Place, value = PlanInputValue.Text("home"))),
            ),
        )
        val model = BeforeStartViewModel(
            repository,
            FakeCoachIntakeRepository(),
            MutableClock(),
            routines,
            SavedStateHandle(mapOf<String, Any?>("mode" to mode, "planId" to "plan-9") + saved),
        )
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = { created += it },
                    onOpenYourData = {},
                    onFix = {},
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(),
                    onOpenFolderBlock = { reviews += it },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun crearBloque_asksHowToStart_beforeAnyNavigation() {
        show()

        tap("before_start_create")

        rule.onNodeWithText("¿Cómo quieres empezar?").assertExists()
        rule.onNodeWithText("Desde cero").assertExists()
        rule.onNodeWithText("El entrenador propone los días").assertExists()
        rule.onNodeWithText("Desde una carpeta").assertExists()
        rule.onNodeWithText("Usa tus rutinas como base").assertExists()
        assertTrue(created.isEmpty())
    }

    @Test
    fun desdeCero_continuesToTheSplitsAsBefore() {
        show()
        tap("before_start_create")

        tap("block_start_option_scratch")

        assertEquals(1, created.size)
        assertEquals(BeforeStartMode.Block, created.single().mode)
        assertEquals("plan-9", created.single().planId)
        assertTrue(reviews.isEmpty())
    }

    @Test
    fun desdeUnaCarpeta_listsTheFolders_andATapOpensTheReviewForTheBlock() {
        show()
        tap("before_start_create")

        tap("block_start_option_folder")
        rule.onNodeWithText("Elige una carpeta").assertExists()
        rule.onNodeWithText("Torso-pierna").assertExists()
        tap("block_start_folder_f2")

        assertEquals(listOf(BeforeStartEffect.OpenFolderBlock("plan-9", "f2")), reviews)
        assertTrue(created.isEmpty())
    }

    @Test
    fun withNoFolders_theSheetSaysSoAndWhy() {
        routines.folders.clear()
        show()
        tap("before_start_create")

        tap("block_start_option_folder")

        rule.onNodeWithText("Aún no tienes carpetas").assertExists()
        rule.onNodeWithText("Crea una en Entreno y añade tus rutinas.").assertExists()
        rule.onAllNodesWithTag("block_start_folder_f1").assertCountEquals(0)
    }

    @Test
    fun offline_theFolderListSaysSo_andReintentarLoadsThem() {
        routines.failure = AppError.Network
        show()
        tap("before_start_create")
        tap("block_start_option_folder")
        rule.onNodeWithText("Sin conexión: no se pueden cargar las carpetas.").assertExists()

        routines.failure = null
        tap("block_start_folders_retry")

        rule.onNodeWithTag("block_start_folder_f1").assertExists()
    }

    @Test
    fun closingTheChoice_createsAndOpensNothing() {
        show()
        tap("before_start_create")

        tap("block_start_sheet_close")

        rule.onAllNodesWithTag("block_start_option_scratch").assertCountEquals(0)
        assertTrue(created.isEmpty())
        assertTrue(reviews.isEmpty())
    }

    @Test
    fun aPlan_goesStraightOnWithoutAsking() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        show(mode = "plan")

        tap("before_start_create")

        rule.onAllNodesWithTag("block_start_option_scratch").assertCountEquals(0)
        assertEquals(1, created.size)
    }
}
