package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SetEntryMappingTest {
    private val expectedColumns = mapOf(
        ExerciseMetricType.WeightReps to setOf(SetColumn.WeightKg, SetColumn.Reps),
        ExerciseMetricType.RepsOnly to setOf(SetColumn.Reps),
        ExerciseMetricType.Duration to setOf(SetColumn.DurationS),
        ExerciseMetricType.WeightDuration to setOf(SetColumn.WeightKg, SetColumn.DurationS),
        ExerciseMetricType.DistanceDuration to setOf(SetColumn.DistanceM, SetColumn.DurationS),
        ExerciseMetricType.Bodyweight to setOf(SetColumn.WeightKg, SetColumn.Reps),
        ExerciseMetricType.Assisted to setOf(SetColumn.WeightKg, SetColumn.Reps),
        ExerciseMetricType.Weighted to setOf(SetColumn.WeightKg, SetColumn.Reps),
        ExerciseMetricType.StepsDuration to setOf(SetColumn.Steps, SetColumn.DurationS),
        ExerciseMetricType.FloorsDuration to setOf(SetColumn.Floors, SetColumn.DurationS),
    )

    @Test
    fun everyMetricType_declaresItsColumns() {
        assertEquals(ExerciseMetricType.entries.toSet(), expectedColumns.keys)
        expectedColumns.forEach { (type, columns) -> assertEquals(columns, type.columns, "columns of $type") }
    }

    @Test
    fun emptyEntry_hasTheMetricTypeItWasBuiltFor() {
        ExerciseMetricType.entries.forEach { type ->
            assertEquals(type, SetEntry.empty(type).metricType, "metric type of the empty $type entry")
        }
    }

    @Test
    fun emptyEntry_writesOnlyZeroValuesForItsColumns() {
        ExerciseMetricType.entries.forEach { type ->
            val written = SetEntry.empty(type).columns()
            assertEquals(type.columns, written.written(), "written columns of $type")
        }
    }

    @Test
    fun columns_ofADistanceSet_leaveTheOtherColumnsNull() {
        val columns = SetEntry.DistanceDuration(5000.0, 1800).columns()

        assertEquals(SetColumns(distanceM = 5000.0, durationS = 1800), columns)
    }

    @Test
    fun columns_ofABodyweightSet_storeTheAddedLoadInWeight() {
        assertEquals(SetColumns(weightKg = 10.0, reps = 8), SetEntry.Bodyweight(10.0, 8).columns())
    }

    @Test
    fun toEntry_roundTripsEveryVariant() {
        val entries = listOf(
            SetEntry.WeightReps(80.5, 8),
            SetEntry.RepsOnly(12),
            SetEntry.Duration(90),
            SetEntry.WeightDuration(32.0, 45),
            SetEntry.DistanceDuration(5000.0, 1800),
            SetEntry.Bodyweight(10.0, 8),
            SetEntry.Assisted(20.0, 10),
            SetEntry.Weighted(15.0, 6),
            SetEntry.StepsDuration(4000, 1800),
            SetEntry.FloorsDuration(30, 600),
        )

        entries.forEach { entry ->
            assertEquals(entry, entry.columns().toEntry(entry.metricType), "round trip of $entry")
        }
    }

    @Test
    fun toEntry_treatsMissingColumnsAsZero() {
        assertEquals(SetEntry.Duration(0), SetColumns().toEntry(ExerciseMetricType.Duration))
        assertEquals(SetEntry.WeightReps(0.0, 0), SetColumns().toEntry(ExerciseMetricType.WeightReps))
    }
}
