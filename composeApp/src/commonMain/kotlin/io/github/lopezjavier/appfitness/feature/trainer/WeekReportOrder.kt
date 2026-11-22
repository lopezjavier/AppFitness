package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.TrainingIntention

private val STRENGTH_BLOCK = listOf(ReportCardKind.Strength, ReportCardKind.Lifts, ReportCardKind.Records)

/**
 * Card order by goal (`07` §3 "Card order by goal", R7); the coach card is always first. Fat loss reads the weight
 * first; strength and muscle read the strength block first; maintenance and recomposition put the waist before the
 * weight. The waist card only exists in a week with a new measurement ([hasWaist]).
 */
fun weekReportOrder(intention: TrainingIntention, hasWaist: Boolean): List<ReportCardKind> {
    val waist = if (hasWaist) listOf(ReportCardKind.Waist) else emptyList()
    return when (intention) {
        TrainingIntention.FatLoss -> listOf(ReportCardKind.Weight) + waist + STRENGTH_BLOCK

        TrainingIntention.Strength, TrainingIntention.Muscle -> STRENGTH_BLOCK + ReportCardKind.Weight + waist

        TrainingIntention.Maintenance, TrainingIntention.Recomposition ->
            STRENGTH_BLOCK + waist + ReportCardKind.Weight
    }
}
