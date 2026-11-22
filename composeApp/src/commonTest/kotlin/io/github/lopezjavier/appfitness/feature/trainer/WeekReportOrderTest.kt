package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekReportOrderTest {
    private val block = listOf(ReportCardKind.Strength, ReportCardKind.Lifts, ReportCardKind.Records)

    @Test
    fun fatLoss_weightThenWaistThenTheStrengthBlock() {
        assertEquals(
            listOf(ReportCardKind.Weight, ReportCardKind.Waist) + block,
            weekReportOrder(TrainingIntention.FatLoss, hasWaist = true),
        )
        assertEquals(
            listOf(ReportCardKind.Weight) + block,
            weekReportOrder(TrainingIntention.FatLoss, hasWaist = false),
        )
    }

    @Test
    fun strengthAndMuscle_strengthBlockThenWeightThenWaist() {
        listOf(TrainingIntention.Strength, TrainingIntention.Muscle).forEach {
            assertEquals(
                block + listOf(ReportCardKind.Weight, ReportCardKind.Waist),
                weekReportOrder(it, hasWaist = true),
            )
            assertEquals(block + ReportCardKind.Weight, weekReportOrder(it, hasWaist = false))
        }
    }

    @Test
    fun maintenanceAndRecomposition_strengthBlockThenWaistThenWeight() {
        listOf(TrainingIntention.Maintenance, TrainingIntention.Recomposition).forEach {
            assertEquals(
                block + listOf(ReportCardKind.Waist, ReportCardKind.Weight),
                weekReportOrder(it, hasWaist = true),
            )
            assertEquals(block + ReportCardKind.Weight, weekReportOrder(it, hasWaist = false))
        }
    }
}
