package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.phase_label_cut
import io.github.lopezjavier.appfitness.composeapp.resources.phase_label_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_cut
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_user_busy
import io.github.lopezjavier.appfitness.domain.model.CutRate
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhaseTextTest {
    @Test
    fun everyPhase_hasADistinctLabelAndHelp() {
        val labels = Phase.entries.map { PhaseText.label(it) }
        val helps = Phase.entries.map { PhaseText.help(it) }

        assertEquals(Phase.entries.size, labels.toSet().size)
        assertEquals(Phase.entries.size, helps.toSet().size)
    }

    @Test
    fun everyPhaseAndReasonPair_hasAReason() {
        for (phase in Phase.entries) {
            for (reason in PhaseReason.entries) {
                PhaseText.reason(phase, reason)
            }
        }
    }

    @Test
    fun labels_followTheGlossary() {
        assertEquals(Res.string.phase_label_cut, PhaseText.label(Phase.Cut))
        assertEquals(Res.string.phase_label_maintenance, PhaseText.label(Phase.Maintenance))
    }

    @Test
    fun intentionReason_isToldFromThePhase() {
        assertEquals(Res.string.phase_reason_cut, PhaseText.reason(Phase.Cut, PhaseReason.Intention))
    }

    @Test
    fun userBusy_winsOverThePhase() {
        assertEquals(Res.string.phase_reason_user_busy, PhaseText.reason(Phase.Cut, PhaseReason.UserBusy))
    }

    @Test
    fun cutRates_areTheBoundsAndTheMiddleValue_andAllValid() {
        assertEquals(listOf(0.5, 0.75, 1.0), PhaseText.cutRates)
        assertTrue(PhaseText.cutRates.all { CutRate.isValid(it) })
    }
}
