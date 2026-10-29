package io.github.lopezjavier.appfitness.feature.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeBodyMeasurementRepository
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNotificationSettingsRepository
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.MyTraining
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.SavePreferences
import io.github.lopezjavier.appfitness.feature.food.FakeFoodPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** US-65 on the "Mi entrenamiento" section of Preferencias. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesMyTrainingViewModelTest {
    private val today = LocalDate(2026, 10, 14)
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val weights = FakeBodyMeasurementRepository(
        BodyMeasurement("w1", Instant.parse("2026-10-10T08:00:00Z"), 80.0, MeasurementSource.Manual),
    )
    private val goals =
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT))
    private val connectivity = FakeConnectivityObserver(initial = true)
    private val training = FakeTrainingPreferencesRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        profile.biometrics = Result.success(
            SavedBiometrics(Sex.Male, LocalDate(2000, 3, 1), 178, ActivityLevel.Moderate),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = PreferencesViewModel(
        profileRepository = profile,
        goalsRepository = goals,
        bodyMeasurementRepository = weights,
        trainingPreferencesRepository = training,
        foodPreferencesRepository = FakeFoodPreferencesRepository(),
        notificationSettings = FakeNotificationSettingsRepository(),
        savePreferences = SavePreferences(profile),
        connectivity = connectivity,
        clock = FakeClock(today, now),
        timeZone = TimeZone.UTC,
        savedState = handle.also { it[KEY_PREFERENCES_SECTION] = PreferencesSection.MyTraining.key },
    )

    private fun TestScope.loaded(
        handle: SavedStateHandle = SavedStateHandle(),
    ): Pair<PreferencesViewModel, () -> PreferencesUiState.Loaded> {
        val viewModel = viewModel(handle)
        advanceUntilIdle()
        return viewModel to { assertIs<PreferencesUiState.Loaded>(viewModel.uiState.value) }
    }

    private fun PreferencesViewModel.send(vararg events: PreferencesEvent) = events.forEach(::onEvent)

    @Test
    fun load_mapsTheSavedFieldsIntoTheForm() = runTest {
        training.result = Result.success(
            TrainingPreferences(
                intention = TrainingIntention.Recomposition,
                place = TrainingPlace.Gym,
                equipment = setOf(Equipment.Barbell),
                daysPerWeek = 5,
                sessionMinutes = 75,
            ),
        )

        val (_, state) = loaded()

        val form = state().form
        assertEquals(TrainingIntention.Recomposition, form.intention)
        assertEquals(TrainingPlace.Gym, form.place)
        assertEquals(setOf(Equipment.Barbell), form.equipment)
        assertEquals(5, form.daysPerWeek)
        assertEquals(75, form.sessionMinutes)
        assertFalse(state().canSave)
    }

    @Test
    fun choosingTheIntention_makesTheFormDirtyAndSavesIt() = runTest {
        val (viewModel, state) = loaded()

        viewModel.send(PreferencesEvent.SetIntention(TrainingIntention.Strength), PreferencesEvent.Save)
        advanceUntilIdle()

        assertEquals(TrainingIntention.Strength, training.savedMyTraining.single().intention)
        assertTrue(state().isSaved)
        assertFalse(state().isDirty)
    }

    @Test
    fun choosingAPlace_replacesTheEquipmentWithTheDefaults_andItStaysEditable() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.SetPlace(TrainingPlace.Home))
        assertEquals(setOf(Equipment.Dumbbell, Equipment.Band, Equipment.Bodyweight), state().form.equipment)

        viewModel.send(
            PreferencesEvent.SetEquipment(Equipment.Band, checked = false),
            PreferencesEvent.SetEquipment(Equipment.Kettlebell, checked = true),
        )
        assertEquals(setOf(Equipment.Dumbbell, Equipment.Bodyweight, Equipment.Kettlebell), state().form.equipment)

        viewModel.onEvent(PreferencesEvent.SetPlace(TrainingPlace.Gym))
        assertEquals(Equipment.entries.toSet(), state().form.equipment)
        viewModel.onEvent(PreferencesEvent.SetPlace(TrainingPlace.Other))
        assertEquals(setOf(Equipment.Bodyweight), state().form.equipment)
    }

    @Test
    fun daysAndSession_areOptional_andNullClearsAPreviousValue() = runTest {
        training.result = Result.success(TrainingPreferences(daysPerWeek = 3, sessionMinutes = 45))
        val (viewModel, state) = loaded()

        viewModel.send(
            PreferencesEvent.SetDaysPerWeek(null),
            PreferencesEvent.SetSessionMinutes(null),
            PreferencesEvent.Save,
        )
        advanceUntilIdle()

        val saved = training.savedMyTraining.single()
        assertNull(saved.daysPerWeek)
        assertNull(saved.sessionMinutes)
        assertTrue(state().isSaved)
    }

    @Test
    fun savingWithNothingSet_isNotPossible_untilSomethingChanges() = runTest {
        val (viewModel, state) = loaded()

        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertFalse(state().canSave)
        assertTrue(training.savedMyTraining.isEmpty())
    }

    @Test
    fun daysOutsideOneToSeven_areRejected() = runTest {
        val (viewModel, state) = loaded()

        viewModel.send(PreferencesEvent.SetDaysPerWeek(0), PreferencesEvent.SetDaysPerWeek(8))

        assertNull(state().form.daysPerWeek)
        assertFalse(state().isDirty)
        viewModel.onEvent(PreferencesEvent.SetDaysPerWeek(7))
        assertEquals(7, state().form.daysPerWeek)
    }

    @Test
    fun sessionOutsideRangeOrNotAMultipleOfFive_isRejected() = runTest {
        val (viewModel, state) = loaded()

        viewModel.send(
            PreferencesEvent.SetSessionMinutes(10),
            PreferencesEvent.SetSessionMinutes(185),
            PreferencesEvent.SetSessionMinutes(47),
        )
        assertNull(state().form.sessionMinutes)

        viewModel.onEvent(PreferencesEvent.SetSessionMinutes(15))
        assertEquals(15, state().form.sessionMinutes)
        viewModel.onEvent(PreferencesEvent.SetSessionMinutes(180))
        assertEquals(180, state().form.sessionMinutes)
    }

    @Test
    fun save_writesOnlyItsOwnFields() = runTest {
        val (viewModel, _) = loaded()

        viewModel.send(
            PreferencesEvent.SetIntention(TrainingIntention.FatLoss),
            PreferencesEvent.SetPlace(TrainingPlace.Home),
            PreferencesEvent.SetDaysPerWeek(4),
            PreferencesEvent.SetSessionMinutes(45),
            PreferencesEvent.Save,
        )
        advanceUntilIdle()

        assertEquals(
            MyTraining(
                TrainingIntention.FatLoss,
                TrainingPlace.Home,
                setOf(Equipment.Dumbbell, Equipment.Band, Equipment.Bodyweight),
                4,
                45,
            ),
            training.savedMyTraining.single(),
        )
        assertTrue(profile.preferenceSaves.isEmpty(), "save_preferences is not involved")
        assertEquals(goals.row, NutritionGoals(GoalStrategy.Deficit, -400, 2000, MacroSplit.DEFAULT))
    }

    @Test
    fun offline_disablesSave() = runTest {
        connectivity.state.value = false
        val (viewModel, state) = loaded()
        viewModel.onEvent(PreferencesEvent.SetIntention(TrainingIntention.Muscle))
        advanceUntilIdle()

        assertTrue(state().isDirty)
        assertFalse(state().canSave)
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()
        assertTrue(training.savedMyTraining.isEmpty())
    }

    @Test
    fun saveError_emitsSaveFailed_andKeepsTheDraft() = runTest {
        training.saveResult = Result.failure(AppException(AppError.Network))
        val (viewModel, state) = loaded()

        viewModel.effects.test {
            viewModel.send(PreferencesEvent.SetIntention(TrainingIntention.Muscle), PreferencesEvent.Save)
            advanceUntilIdle()

            assertEquals(PreferencesEffect.SaveFailed(offline = true), awaitItem())
        }
        assertFalse(state().isSaved)
        assertFalse(state().isSaving)
        assertEquals(TrainingIntention.Muscle, state().form.intention)
        assertTrue(state().canSave)
    }

    @Test
    fun isSaved_isRaisedOnceAfterASuccessfulSave() = runTest {
        val (viewModel, state) = loaded()

        viewModel.send(PreferencesEvent.SetIntention(TrainingIntention.Muscle), PreferencesEvent.Save)
        advanceUntilIdle()
        viewModel.onEvent(PreferencesEvent.Save)
        advanceUntilIdle()

        assertTrue(state().isSaved)
        assertEquals(1, training.savedMyTraining.size)
    }

    @Test
    fun eventsOfOtherSections_areIgnored() = runTest {
        val (viewModel, state) = loaded()

        viewModel.send(PreferencesEvent.SetWarmUpIncluded(true), PreferencesEvent.SetName("Bea"))

        assertFalse(state().isDirty)
    }

    @Test
    fun aDraft_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val (first, _) = loaded(handle)
        first.send(
            PreferencesEvent.SetIntention(TrainingIntention.Maintenance),
            PreferencesEvent.SetPlace(TrainingPlace.Other),
            PreferencesEvent.SetDaysPerWeek(2),
            PreferencesEvent.SetSessionMinutes(30),
        )

        val (_, state) = loaded(handle)

        val form = state().form
        assertEquals(TrainingIntention.Maintenance, form.intention)
        assertEquals(TrainingPlace.Other, form.place)
        assertEquals(setOf(Equipment.Bodyweight), form.equipment)
        assertEquals(2, form.daysPerWeek)
        assertEquals(30, form.sessionMinutes)
        assertTrue(state().isDirty)
    }

    @Test
    fun readFailure_isLoadFailed() = runTest {
        training.result = Result.failure(AppException(AppError.Network))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(PreferencesUiState.LoadFailed, viewModel.uiState.value)
    }
}
