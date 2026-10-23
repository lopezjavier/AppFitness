package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetEntryTextTest {
    @Test
    fun weightReps_isKilosTimesReps() {
        assertEquals("85 kg × 8", SetEntryText.format(SetEntry.WeightReps(85.0, 8)))
        assertEquals("82,5 kg × 5", SetEntryText.format(SetEntry.WeightReps(82.5, 5)))
    }

    @Test
    fun repsOnly_isTheRepCount() {
        assertEquals("20 reps", SetEntryText.format(SetEntry.RepsOnly(20)))
    }

    @Test
    fun duration_isMinutesAndSeconds() {
        assertEquals("01:30", SetEntryText.format(SetEntry.Duration(90)))
    }

    @Test
    fun distanceAndDuration_isKilometresWithACommaAndTheTime() {
        assertEquals("5,0 km · 30:00", SetEntryText.format(SetEntry.DistanceDuration(5000.0, 1800)))
        assertEquals("1,2 km · 10:00", SetEntryText.format(SetEntry.DistanceDuration(1234.0, 600)))
    }

    @Test
    fun bodyweight_showsTheAddedLoadOnlyWhenThereIsOne() {
        assertEquals("+10 kg × 8", SetEntryText.format(SetEntry.Bodyweight(10.0, 8)))
        assertEquals("8 reps", SetEntryText.format(SetEntry.Bodyweight(0.0, 8)))
    }

    @Test
    fun assisted_showsTheAssistanceWithAMinus() {
        assertEquals("-20 kg × 10", SetEntryText.format(SetEntry.Assisted(20.0, 10)))
    }

    @Test
    fun stepsAndFloors_showTheirCountAndTheTime() {
        assertEquals("1200 pasos · 10:00", SetEntryText.format(SetEntry.StepsDuration(1200, 600)))
        assertEquals("12 pisos · 05:00", SetEntryText.format(SetEntry.FloorsDuration(12, 300)))
    }

    @Test
    fun aGhostSet_isReadAccordingToTheMetricType() {
        val ghost = PreviousSet(1, 0.0, 0, durationS = 90)

        assertEquals("01:30", SetEntryText.format(ghost, ExerciseMetricType.Duration))
    }

    @Test
    fun onlyLoadedRepMetrics_carryVolume() {
        assertTrue(SetEntryText.carriesVolume(ExerciseMetricType.Bodyweight))
        assertFalse(SetEntryText.carriesVolume(ExerciseMetricType.Duration))
        assertFalse(SetEntryText.carriesVolume(ExerciseMetricType.DistanceDuration))
    }
}
