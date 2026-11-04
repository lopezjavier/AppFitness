package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.phase_help_cut
import io.github.lopezjavier.appfitness.composeapp.resources.phase_help_gain
import io.github.lopezjavier.appfitness.composeapp.resources.phase_help_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.phase_help_recomposition
import io.github.lopezjavier.appfitness.composeapp.resources.phase_label_cut
import io.github.lopezjavier.appfitness.composeapp.resources.phase_label_gain
import io.github.lopezjavier.appfitness.composeapp.resources.phase_label_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.phase_label_recomposition
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_cut
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_gain
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_recomposition
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_underweight
import io.github.lopezjavier.appfitness.composeapp.resources.phase_reason_user_busy
import io.github.lopezjavier.appfitness.domain.model.CutRate
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import org.jetbrains.compose.resources.StringResource

/** Pure mapping from the plan's phase to its Spanish copy; shared by the proposal, "Tu plan" and (T-191) the review. */
internal object PhaseText {
    /** The glossary label (05 section 10): Definicion, Volumen, Mantenimiento, Recomposicion, Minimo. */
    fun label(phase: Phase): StringResource = when (phase) {
        Phase.Cut -> Res.string.phase_label_cut
        Phase.Gain -> Res.string.phase_label_gain
        Phase.Maintenance -> Res.string.phase_label_maintenance
        Phase.Recomposition -> Res.string.phase_label_recomposition
    }

    /**
     * The one-sentence reason. A busy period wins over the intention; an [PhaseReason.Intention] reason is told from
     * the [phase].
     */
    fun reason(phase: Phase, reason: PhaseReason): StringResource = when (reason) {
        PhaseReason.UserBusy -> Res.string.phase_reason_user_busy

        PhaseReason.Underweight -> Res.string.phase_reason_underweight

        PhaseReason.Intention -> when (phase) {
            Phase.Cut -> Res.string.phase_reason_cut
            Phase.Gain -> Res.string.phase_reason_gain
            Phase.Maintenance -> Res.string.phase_reason_maintenance
            Phase.Recomposition -> Res.string.phase_reason_recomposition
        }
    }

    /** The body of the "?" sheet next to the phase. */
    fun help(phase: Phase): StringResource = when (phase) {
        Phase.Cut -> Res.string.phase_help_cut
        Phase.Gain -> Res.string.phase_help_gain
        Phase.Maintenance -> Res.string.phase_help_maintenance
        Phase.Recomposition -> Res.string.phase_help_recomposition
    }

    /** The weekly cut rates the selector offers (percent of body weight): the bounds and the middle value. */
    val cutRates: List<Double> = listOf(CutRate.MIN, 0.75, CutRate.MAX)
}
