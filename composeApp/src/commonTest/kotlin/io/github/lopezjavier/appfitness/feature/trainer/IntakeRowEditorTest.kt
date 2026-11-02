package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IntakeRowEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachIntakeRepository()
    private val saved = mutableListOf<CoachIntake>()

    private fun TestScope.editor(handle: SavedStateHandle = SavedStateHandle()) =
        IntakeRowEditor(repository, this, handle) { saved += it }

    private fun IntakeRowEditor.sheet() = state.value.sheet

    @Test
    fun nothingIsOpenUntilARowIsTapped() = runTest(dispatcher) {
        assertNull(editor().sheet())
    }

    @Test
    fun yearsStepper_startsAtSinIndicar_andGoesUpInHalfYears() = runTest(dispatcher) {
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenYears(null))

        editor.onEvent(IntakeRowEvent.YearsUp)
        assertEquals(IntakeRowSheet.Years(0.0), editor.sheet())
        editor.onEvent(IntakeRowEvent.YearsUp)
        assertEquals(IntakeRowSheet.Years(0.5), editor.sheet())
    }

    @Test
    fun yearsStepper_downFromZeroReturnsToSinIndicar_andStopsThere() = runTest(dispatcher) {
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenYears(0.5))

        editor.onEvent(IntakeRowEvent.YearsDown)
        assertEquals(IntakeRowSheet.Years(0.0), editor.sheet())
        editor.onEvent(IntakeRowEvent.YearsDown)
        assertEquals(IntakeRowSheet.Years(null), editor.sheet())
        editor.onEvent(IntakeRowEvent.YearsDown)
        assertEquals(IntakeRowSheet.Years(null), editor.sheet())
    }

    @Test
    fun yearsStepper_stopsAtTheServerMaximum() = runTest(dispatcher) {
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenYears(60.0))

        editor.onEvent(IntakeRowEvent.YearsUp)

        assertEquals(IntakeRowSheet.Years(60.0), editor.sheet())
    }

    @Test
    fun savingYears_sendsOnlyYears_andClosesAndReportsTheStoredRow() = runTest(dispatcher) {
        repository.stored = CoachIntake(knownMaintenanceKcal = 2400, freeText = "hola")
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenYears(null))
        editor.onEvent(IntakeRowEvent.YearsUp)
        editor.onEvent(IntakeRowEvent.YearsUp)
        editor.onEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.TrainingYears(0.5)), repository.saveCalls)
        assertEquals(CoachIntake(0.5, 2400, "hola"), saved.single())
        assertNull(editor.sheet())
    }

    @Test
    fun clearingYears_savesAnExplicitNull() = runTest(dispatcher) {
        repository.stored = CoachIntake(trainingYears = 0.0)
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenYears(0.0))
        editor.onEvent(IntakeRowEvent.YearsDown)
        editor.onEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.TrainingYears(null)), repository.saveCalls)
    }

    @Test
    fun maintenance_acceptsTheBoundaries() = runTest(dispatcher) {
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenMaintenance(null))

        for (text in listOf("1200", "6000")) {
            editor.onEvent(IntakeRowEvent.EditMaintenance(text))
            assertFalse((editor.sheet() as IntakeRowSheet.Maintenance).invalid, text)
            assertTrue(editor.state.value.canSave, text)
        }
    }

    @Test
    fun maintenance_rejectsOutOfRangeAndDecimals_andSavesNothing() = runTest(dispatcher) {
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenMaintenance(null))

        for (text in listOf("1199", "6001", "2400.5", "2400,5", "abc")) {
            editor.onEvent(IntakeRowEvent.EditMaintenance(text))
            assertTrue((editor.sheet() as IntakeRowSheet.Maintenance).invalid, text)
            assertFalse(editor.state.value.canSave, text)
            editor.onEvent(IntakeRowEvent.Save)
        }
        advanceUntilIdle()

        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun maintenance_emptyField_clearsTheValue() = runTest(dispatcher) {
        repository.stored = CoachIntake(knownMaintenanceKcal = 2400)
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenMaintenance(2400))
        editor.onEvent(IntakeRowEvent.EditMaintenance(""))
        editor.onEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.KnownMaintenance(null)), repository.saveCalls)
    }

    @Test
    fun saveFailure_keepsTheSheetAndTheDraft() = runTest(dispatcher) {
        repository.saveFailure = AppException(AppError.Unknown)
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenMaintenance(null))
        editor.onEvent(IntakeRowEvent.EditMaintenance("2500"))
        editor.onEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(IntakeRowSheet.Maintenance("2500"), editor.sheet())
        assertEquals(IntakeNotice.SaveFailed, editor.state.value.notice)
        assertTrue(editor.state.value.canSave)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun saveOffline_isTheOfflineNotice() = runTest(dispatcher) {
        repository.saveFailure = AppException(AppError.Network)
        val editor = editor()
        editor.onEvent(IntakeRowEvent.OpenYears(1.0))
        editor.onEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(IntakeNotice.SaveOffline, editor.state.value.notice)
    }

    @Test
    fun close_dropsTheDraftSilently_andSavesNothing() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val editor = editor(handle)
        editor.onEvent(IntakeRowEvent.OpenMaintenance(null))
        editor.onEvent(IntakeRowEvent.EditMaintenance("2500"))

        editor.onEvent(IntakeRowEvent.Close)

        assertNull(editor.sheet())
        assertTrue(repository.saveCalls.isEmpty())
        assertNull(editor(handle).sheet())
    }

    @Test
    fun openSheetWithATypedValue_isRestoredFromTheSameSavedState() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = editor(handle)
        first.onEvent(IntakeRowEvent.OpenMaintenance(null))
        first.onEvent(IntakeRowEvent.EditMaintenance("2450"))

        assertEquals(IntakeRowSheet.Maintenance("2450"), editor(handle).sheet())
    }

    @Test
    fun openYearsSheet_isRestoredFromTheSameSavedState() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = editor(handle)
        first.onEvent(IntakeRowEvent.OpenYears(3.0))
        first.onEvent(IntakeRowEvent.YearsUp)

        assertEquals(IntakeRowSheet.Years(3.5), editor(handle).sheet())
    }
}
