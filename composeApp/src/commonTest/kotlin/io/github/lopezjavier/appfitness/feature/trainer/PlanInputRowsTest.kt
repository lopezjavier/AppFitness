package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun planInput(
    key: PlanInputKey,
    status: PlanInputStatus = PlanInputStatus.Ok,
    value: PlanInputValue = PlanInputValue.Text("x"),
    source: PlanInputSource = PlanInputSource.Profile,
    blocking: Boolean = false,
    fixTarget: PlanFixTarget? = null,
    date: LocalDate? = null,
) = PlanInput(key, value, source, status, blocking, fixTarget, date)

class PlanInputRowsTest {
    private fun markerOf(status: PlanInputStatus, blocking: Boolean = false) =
        planInput(PlanInputKey.Sex, status, blocking = blocking).toRow().marker

    @Test
    fun missingAndBlocking_isAFlag() {
        assertEquals(PlanInputMarker.Flag, markerOf(PlanInputStatus.Missing, blocking = true))
        assertTrue(planInput(PlanInputKey.Intention, PlanInputStatus.Missing, blocking = true).toRow().isFlag)
    }

    @Test
    fun missingNotBlocking_isANoteNotAFlag() {
        assertEquals(PlanInputMarker.NoData, markerOf(PlanInputStatus.Missing))
        assertFalse(planInput(PlanInputKey.Sex, PlanInputStatus.Missing).toRow().isFlag)
    }

    @Test
    fun staleWeight_isAReviewNote_keepingItsDate() {
        val row = planInput(
            PlanInputKey.Weight,
            PlanInputStatus.Stale,
            PlanInputValue.Number(80.0),
            PlanInputSource.WeighIn,
            date = LocalDate(2026, 10, 1),
        ).toRow()

        assertEquals(PlanInputMarker.Stale, row.marker)
        assertEquals(LocalDate(2026, 10, 1), row.date)
    }

    @Test
    fun unsetOptional_isNotFlagged() {
        val row = planInput(PlanInputKey.Experience, PlanInputStatus.UnsetOptional, PlanInputValue.Absent).toRow()

        assertEquals(PlanInputMarker.Optional, row.marker)
        assertFalse(row.isFlag)
        assertTrue(row.isEmpty)
    }

    @Test
    fun provisional_isMarkedProvisional() {
        assertTrue(planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional).toRow().isProvisional)
    }

    @Test
    fun okAndUnknownStatus_areNeutral() {
        assertEquals(PlanInputMarker.None, markerOf(PlanInputStatus.Ok))
        assertEquals(PlanInputMarker.None, markerOf(PlanInputStatus.Unknown))
    }

    @Test
    fun unknownKey_mapsToANeutralRowWithoutCrashing() {
        val row = planInput(PlanInputKey.Unknown, PlanInputStatus.Unknown, PlanInputValue.Absent).toRow()

        assertEquals(PlanInputMarker.None, row.marker)
        assertEquals(PlanInputKey.Unknown, row.key)
    }

    @Test
    fun everyKeyAndStatus_mapsWithoutCrashing() {
        for (key in PlanInputKey.entries) {
            for (status in PlanInputStatus.entries) {
                planInput(key, status, blocking = true).toRow()
            }
        }
    }

    @Test
    fun sourceDecidesTheOrigin() {
        val expected = mapOf(
            PlanInputSource.Profile to PlanInputOrigin.Profile,
            PlanInputSource.MyTraining to PlanInputOrigin.MyTraining,
            PlanInputSource.WeighIn to PlanInputOrigin.Weighing,
            PlanInputSource.CoachEstimate to PlanInputOrigin.Estimate,
            PlanInputSource.Unknown to PlanInputOrigin.Estimate,
        )
        for ((source, origin) in expected) {
            assertEquals(origin, planInput(PlanInputKey.Sex, source = source).toRow().origin, source.name)
        }
    }

    @Test
    fun emptyItemsAndAbsent_haveNoValue_butTextHasOne() {
        assertTrue(planInput(PlanInputKey.Equipment, value = PlanInputValue.Items(emptyList())).toRow().isEmpty)
        assertTrue(planInput(PlanInputKey.Equipment, value = PlanInputValue.Absent).toRow().isEmpty)
        assertFalse(planInput(PlanInputKey.Equipment, value = PlanInputValue.Items(listOf("barbell"))).toRow().isEmpty)
        assertFalse(planInput(PlanInputKey.Intention, value = PlanInputValue.Text("lose")).toRow().isEmpty)
    }

    @Test
    fun fixTarget_isCarriedToTheRow() {
        val row = planInput(PlanInputKey.Weight, fixTarget = PlanFixTarget.Weight).toRow()

        assertEquals(PlanFixTarget.Weight, row.fixTarget)
    }

    @Test
    fun summaryLines_perKind() {
        assertEquals(
            listOf(
                listOf(PlanInputKey.Intention),
                listOf(PlanInputKey.Sex, PlanInputKey.Age),
                listOf(PlanInputKey.Height, PlanInputKey.Weight),
                listOf(PlanInputKey.ActivityLevel),
            ),
            PlanInputKind.Plan.summaryLines(),
        )
        assertEquals(
            listOf(
                PlanInputKey.Intention,
                PlanInputKey.Place,
                PlanInputKey.Equipment,
                PlanInputKey.DaysPerWeek,
                PlanInputKey.SessionMinutes,
            ),
            PlanInputKind.Block.summaryLines().flatten(),
        )
    }

    @Test
    fun mergeRows_dedupesByKeyKeepingThePlanRow_andKeepsEveryUnknown() {
        val plan = listOf(
            planInput(PlanInputKey.Intention, value = PlanInputValue.Text("plan")).toRow(),
            planInput(PlanInputKey.Unknown).toRow(),
        )
        val block = listOf(
            planInput(PlanInputKey.Intention, value = PlanInputValue.Text("block")).toRow(),
            planInput(PlanInputKey.Place).toRow(),
            planInput(PlanInputKey.Unknown).toRow(),
        )

        val merged = mergeRows(plan, block)

        assertEquals(
            listOf(PlanInputKey.Intention, PlanInputKey.Unknown, PlanInputKey.Place, PlanInputKey.Unknown),
            merged.map { it.key },
        )
        assertEquals(PlanInputValue.Text("plan"), merged.first().value)
    }
}
