package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
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

    private fun TestScope.saveNote(vm: LiveSessionViewModel, position: Int, setIndex: Int, text: String) {
        vm.onEvent(LiveSessionEvent.OpenSetNote(position, setIndex))
        vm.onEvent(LiveSessionEvent.SetNoteDraftChanged(text))
        vm.onEvent(LiveSessionEvent.SaveSetNote)
        runCurrent()
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

        saveNote(vm, 0, 1, "   ")

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
                ),
                RoutineExercise("e2", 1, null, listOf(PlannedSet(100.0, 5)), restSeconds = 120),
            ),
        )
    }
}
