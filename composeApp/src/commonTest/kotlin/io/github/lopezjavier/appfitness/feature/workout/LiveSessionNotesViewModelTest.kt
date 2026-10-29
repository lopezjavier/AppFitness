package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.ExerciseNoteKind
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetNoteKind
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-66: notes on a set and on a routine exercise in the live session (ADR-81: never interpreted). */
class LiveSessionNotesViewModelTest {
    private val routineNote = "bajar el peso si duele"
    private val routines = FakeRoutineRepository(listOf(routine()))
    private val repository = FakeWorkoutSessionRepository(freshSession())
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val clock = MutableClock()
    private val bodyMeasurements = FakeWeightRepository()
    private var created: LiveSessionViewModel? = null

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        FakeExerciseLibrary(emptyMap()),
        FakeConnectivityObserver(),
        clock,
        FakeHaptics(),
        routines,
        FakeAlertSound(),
        FakeTrainingPreferencesRepository(),
        bodyMeasurements,
        SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
    ).also { created = it }

    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        created?.onEvent(LiveSessionEvent.RestSkip)
        runCurrent()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.setRow(position: Int, setIndex: Int) =
        active().exercises.first { it.position == position }.sets.first { it.setIndex == setIndex }

    private fun TestScope.saveNote(
        vm: LiveSessionViewModel,
        position: Int,
        setIndex: Int,
        text: String,
        kind: SetNoteKind? = SetNoteKind.Other,
    ) {
        vm.onEvent(LiveSessionEvent.OpenSetNote(position, setIndex))
        vm.onEvent(LiveSessionEvent.SetNoteKindChanged(kind))
        vm.onEvent(LiveSessionEvent.SetNoteDraftChanged(text))
        vm.onEvent(LiveSessionEvent.SaveSetNote)
        runCurrent()
    }

    @Test
    fun savingAKindWithoutText_isValidAndWritesTheKind() = runSessionTest {
        val vm = loaded()

        saveNote(vm, 0, 1, "", SetNoteKind.Technique)

        assertEquals(SetNoteKind.Technique, vm.setRow(0, 1).noteKind)
        assertNull(vm.setRow(0, 1).notes)
        val write = repository.upserts.last { it.setIndex == 1 && it.exercisePosition == 0 }
        assertEquals(SetNoteKind.Technique, write.noteKind)
        assertNull(write.notes)
    }

    @Test
    fun otherWithoutText_cannotBeSaved() = runSessionTest {
        val vm = loaded()
        val writes = repository.upserts.size

        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        vm.onEvent(LiveSessionEvent.SetNoteKindChanged(SetNoteKind.Other))
        runCurrent()
        val sheet = vm.active().noteSheet!!
        vm.onEvent(LiveSessionEvent.SaveSetNote)
        runCurrent()

        assertFalse(sheet.canSave)
        assertNull(vm.setRow(0, 1).noteKind)
        assertEquals(writes, repository.upserts.size)
        assertEquals(SetNoteKind.Other, vm.active().noteSheet?.kind)
    }

    @Test
    fun textWithoutAKind_cannotBeSaved() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        vm.onEvent(LiveSessionEvent.SetNoteDraftChanged("sin opcion"))
        runCurrent()

        assertFalse(vm.active().noteSheet!!.canSave)
    }

    @Test
    fun aNonOtherKind_keepsItsOptionalText() = runSessionTest {
        val vm = loaded()

        saveNote(vm, 0, 1, " codo abierto ", SetNoteKind.Technique)

        assertEquals("codo abierto", vm.setRow(0, 1).notes)
        assertEquals(SetNoteKind.Technique, vm.setRow(0, 1).noteKind)
    }

    @Test
    fun deselectingTheKindAndSaving_removesTheNote() = runSessionTest {
        val vm = loaded()
        saveNote(vm, 0, 1, "", SetNoteKind.Grip)

        saveNote(vm, 0, 1, "", null)

        assertNull(vm.setRow(0, 1).noteKind)
        assertNull(vm.setRow(0, 1).notes)
        assertNull(repository.upserts.last().noteKind)
    }

    @Test
    fun openingASetWithAKind_preselectsItAndNothingElse() = runSessionTest {
        val vm = loaded()
        saveNote(vm, 0, 1, "", SetNoteKind.Assisted)

        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 2))
        runCurrent()

        assertEquals(null, vm.active().noteSheet!!.kind)
        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        runCurrent()
        assertEquals(SetNoteKind.Assisted, vm.active().noteSheet!!.kind)
        assertFalse(vm.active().noteSheet!!.canSave)
    }

    @Test
    fun theNoteKindDraft_survivesRecreationFromTheSameSavedState() = runSessionTest {
        val saved = SavedStateHandle()
        val first = loaded(saved)
        first.onEvent(LiveSessionEvent.OpenSetNote(0, 2))
        first.onEvent(LiveSessionEvent.SetNoteKindChanged(SetNoteKind.Range))
        runCurrent()

        val second = loaded(saved)

        assertEquals(SetNoteKind.Range, second.active().noteSheet!!.kind)
    }

    @Test
    fun editingTheRoutineNote_showsItOnTheExercise() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenRoutineNote(1))
        vm.onEvent(LiveSessionEvent.RoutineNoteKindChanged(ExerciseNoteKind.WeightUp))
        vm.onEvent(LiveSessionEvent.RoutineNoteDraftChanged("  con calma "))
        vm.onEvent(LiveSessionEvent.SaveRoutineNote)
        runCurrent()

        val shown = vm.active().exercises.first { it.position == 1 }
        assertEquals("con calma", shown.routineNote)
        assertEquals(ExerciseNoteKind.WeightUp, shown.routineNoteKind)
        assertNull(vm.active().routineNoteSheet)
    }

    @Test
    fun openingTheRoutineNote_prefillsTheRoutinesNote() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenRoutineNote(0))
        runCurrent()

        val sheet = vm.active().routineNoteSheet!!
        assertEquals(ExerciseNoteKind.Other, sheet.kind)
        assertEquals(routineNote, sheet.draft)
        assertFalse(sheet.canSave)
    }

    @Test
    fun theRoutineNoteSheet_survivesRecreationFromTheSameSavedState() = runSessionTest {
        val saved = SavedStateHandle()
        val first = loaded(saved)
        first.onEvent(LiveSessionEvent.OpenRoutineNote(1))
        first.onEvent(LiveSessionEvent.RoutineNoteKindChanged(ExerciseNoteKind.Setup))
        runCurrent()

        val second = loaded(saved)

        assertEquals(ExerciseNoteKind.Setup, second.active().routineNoteSheet!!.kind)
        assertEquals(1, second.active().routineNoteSheet!!.position)
    }

    @Test
    fun withoutARoutine_theRoutineNoteCannotBeOpened() = runSessionTest {
        repository.session = freshSession().copy(routineId = null)
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenRoutineNote(0))
        runCurrent()

        assertNull(vm.active().routineNoteSheet)
    }

    @Test
    fun savingANote_showsItOnTheSetAndWritesItWithTheSet() = runSessionTest {
        val vm = loaded()

        saveNote(vm, 0, 1, "  pausa larga ")

        assertEquals("pausa larga", vm.setRow(0, 1).notes)
        assertNull(vm.active().noteSheet)
        assertEquals("pausa larga", repository.upserts.last { it.setIndex == 1 && it.exercisePosition == 0 }.notes)
        assertNull(vm.setRow(0, 2).notes)
    }

    @Test
    fun openingASetWithANote_prefillsTheDraftAndTheOriginal() = runSessionTest {
        val vm = loaded()
        saveNote(vm, 0, 1, "pausa larga")

        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        runCurrent()

        val sheet = vm.active().noteSheet!!
        assertEquals("pausa larga", sheet.draft)
        assertFalse(sheet.canSave)
    }

    @Test
    fun editingANote_replacesTheText() = runSessionTest {
        val vm = loaded()
        saveNote(vm, 0, 1, "pausa larga")

        saveNote(vm, 0, 1, "pausa corta")

        assertEquals("pausa corta", vm.setRow(0, 1).notes)
        assertEquals("pausa corta", repository.upserts.last().notes)
    }

    @Test
    fun clearingANote_removesItAndSendsANull() = runSessionTest {
        val vm = loaded()
        saveNote(vm, 0, 1, "pausa larga")

        saveNote(vm, 0, 1, "   ", kind = null)

        assertNull(vm.setRow(0, 1).notes)
        assertNull(repository.upserts.last().notes)
    }

    @Test
    fun aNoteOf201Characters_isFlaggedAndCannotBeSaved() = runSessionTest {
        val vm = loaded()
        val writes = repository.upserts.size

        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        vm.onEvent(LiveSessionEvent.SetNoteDraftChanged("x".repeat(201)))
        runCurrent()
        val sheet = vm.active().noteSheet!!
        vm.onEvent(LiveSessionEvent.SaveSetNote)
        runCurrent()

        assertTrue(sheet.tooLong)
        assertFalse(sheet.canSave)
        assertEquals(writes, repository.upserts.size)
        assertEquals(sheet.draft, vm.active().noteSheet?.draft)
    }

    @Test
    fun aNoteOf200Characters_canBeSaved() = runSessionTest {
        val vm = loaded()

        saveNote(vm, 0, 1, "x".repeat(200))

        assertEquals(200, vm.setRow(0, 1).notes?.length)
    }

    @Test
    fun dismissingTheSheet_discardsTheDraftSilently() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.OpenSetNote(0, 1))
        vm.onEvent(LiveSessionEvent.SetNoteDraftChanged("a medias"))

        vm.onEvent(LiveSessionEvent.DismissSetNote)
        runCurrent()

        assertNull(vm.active().noteSheet)
        assertNull(vm.setRow(0, 1).notes)
    }

    @Test
    fun changingTheSetType_keepsItsNote() = runSessionTest {
        val vm = loaded()
        saveNote(vm, 0, 1, "pausa larga")

        vm.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.WarmUp))
        runCurrent()

        assertEquals("pausa larga", repository.upserts.last().notes)
        assertEquals("pausa larga", vm.setRow(0, 1).notes)
    }

    @Test
    fun theRoutineNote_isShownOnItsExercise_readOnly() = runSessionTest {
        val vm = loaded()

        val shown = vm.active().exercises.associate { it.position to it.routineNote }

        assertEquals(mapOf(0 to routineNote, 1 to null), shown)
    }

    @Test
    fun withoutARoutine_thereIsNoRoutineNote() = runSessionTest {
        repository.session = freshSession().copy(routineId = null)

        val vm = loaded()

        assertTrue(vm.active().exercises.all { it.routineNote == null })
    }

    @Test
    fun aNoteSayingDolorFuerte_isStoredAsIsWithoutAnyDiscomfortOrMessage() = runSessionTest {
        val vm = loaded()

        saveNote(vm, 0, 1, "dolor fuerte")

        assertEquals("dolor fuerte", vm.setRow(0, 1).notes)
        assertTrue(vm.active().exercises.all { it.discomfort == null })
        assertFalse(vm.active().discomfortSaving)
    }

    @Test
    fun theOpenNoteSheetAndItsDraft_surviveRecreationFromTheSameSavedState() = runSessionTest {
        val saved = SavedStateHandle()
        val first = loaded(saved)
        first.onEvent(LiveSessionEvent.OpenSetNote(0, 2))
        first.onEvent(LiveSessionEvent.SetNoteDraftChanged("a medias"))
        runCurrent()

        val second = loaded(saved)

        val sheet = second.active().noteSheet!!
        assertEquals(0, sheet.position)
        assertEquals(2, sheet.setIndex)
        assertEquals("a medias", sheet.draft)
    }

    private companion object {
        fun freshSession() = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        )

        fun routine() = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise(
                    "e1",
                    0,
                    null,
                    List(2) { PlannedSet(60.0, 10) },
                    restSeconds = 90,
                    notes = "bajar el peso si duele",
                    noteKind = ExerciseNoteKind.Other,
                ),
                RoutineExercise("e2", 1, null, listOf(PlannedSet(100.0, 5)), restSeconds = 120),
            ),
        )
    }
}
