package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpenditureSourceTest {
    private fun content(source: DerivedSource, kcal: Double? = 2400.0, reask: Boolean = false): TrainerUiState.Content {
        val base = coachValues(provisional = false)
        return TrainerUiState.Content(
            base.copy(totalExpenditure = base.totalExpenditure.copy(kcal = kcal, source = source, reask = reask)),
        )
    }

    @Test
    fun adaptiveExpenditure_isTaggedAdaptive() {
        assertEquals(ExpenditureSource.Adaptive, content(DerivedSource.Adaptive).expenditureSource)
    }

    @Test
    fun formulaExpenditure_isTaggedEstimated() {
        assertEquals(ExpenditureSource.Estimated, content(DerivedSource.Formula).expenditureSource)
    }

    @Test
    fun unknownKcal_hasNoSource() {
        assertNull(content(DerivedSource.Adaptive, kcal = null).expenditureSource)
    }

    @Test
    fun reaskedValue_hasNoSource() {
        assertNull(content(DerivedSource.Formula, reask = true).expenditureSource)
    }

    @Test
    fun activityOnlySources_haveNoExpenditureSource() {
        assertNull(content(DerivedSource.Profile).expenditureSource)
        assertNull(content(DerivedSource.Data).expenditureSource)
    }
}
