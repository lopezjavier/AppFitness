package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.datetime.LocalDate

/*
 * The planned UI model of the progress-first block review (T-318, visual-first). The designer defines it from the
 * `inputs.progress` contract of schema v2 (T-317); the core slice feeds it from `BlockProgress` through a mapper,
 * `BlockReportHeadline` and `weekReportOrder` (R7). It reuses the T-315 types (`StrengthUi`, `LiftUi`, `RecordUi`,
 * `WeightUi`, `WaistUi`), so the shared cards draw both reports. Names resolve through
 * `BlockReviewUiState.Content.exerciseNames` (R-54).
 */

/** Everything the progress-first review shows beyond the kept cards (stalls, phase and calorie proposals). */
data class BlockReportUi(
    val intention: TrainingIntention,
    val coach: BlockCoachUi,
    /** Null for a v1 review (stored before schema v2): only the coach card, the outcomes and the kept cards show. */
    val progress: BlockProgressUi?,
    /** Objectives with their chip already decided by [outcomeChip] (needs today's date, so the core slice sets it). */
    val outcomes: List<OutcomeUi> = emptyList(),
)

/** The coach card: the block sentence (`07` §3 (A0)) and the first action that applies. */
data class BlockCoachUi(val sentence: BlockSentenceUi?, val action: BlockReportAction)

/** The best main-lift change of the block: "press banca +5 kg (+6 %)". */
data class BlockMainLiftUi(val exerciseId: String, val changeKg: Double, val changePct: Double?)

/** Parts of "En este bloque: 5 ejercicios suben, press banca +5 kg (+6 %), peso −1,8 kg." Null parts are left out. */
data class BlockSentenceUi(val ups: Int?, val mainLift: BlockMainLiftUi?, val weightChangeKg: Double?)

/** The first action that applies (`07` §3 (A0)); a stored review reads the decided outcome as text. */
sealed interface BlockReportAction {
    data class PendingPhase(val toPhase: Phase) : BlockReportAction

    data class PendingCalorie(val proposal: CalorieProposal) : BlockReportAction

    data object Stall : BlockReportAction

    /** [past] is true on a stored review: the gap reads as information, not as a call to action. */
    data class MissingData(val kind: MissingKind, val past: Boolean = false) : BlockReportAction

    /** A stored review's phase decision as text ("Fase aceptada: Mantenimiento"), never a button. */
    data class DecidedPhase(val decision: PhaseDecision, val toPhase: Phase) : BlockReportAction

    data object CreateNext : BlockReportAction
}

/**
 * The progress cards. [recordsTotal] counts every record of the block; [records] is capped at 3 by the server.
 * [weight] is a [WeightUi] with block meaning: `days` and `prevDays` are the weigh-in days of the block's last and
 * first plan week, `changeKg` the change between those weeks (null under the 3-day gate in either), `series` the
 * plan-week averages; null when the block has no weigh-in at all (no card; the coach card says what is missing).
 */
data class BlockProgressUi(
    val strength: StrengthUi,
    val lifts: List<LiftUi>,
    val records: List<RecordUi>,
    val recordsTotal: Int,
    val weight: WeightUi?,
    val waist: WaistUi?,
)

/** The chip of an objective (`07` §3 (A6)). */
enum class OutcomeChip { None, Reached, NotReached }

/** One objective: target, where it stands now and the deadline; [key] is the exercise id of an e1RM objective. */
data class OutcomeUi(
    val kind: OutcomeKind,
    val key: String,
    val targetKg: Double,
    val nowKg: Double?,
    val deadline: LocalDate?,
    val chip: OutcomeChip,
)

/**
 * "Conseguido" when reached; "No conseguido" only once the deadline has passed; no chip before it (user Q3, `07` §3
 * (A6)). A missing deadline never counts as passed. Informational: it triggers nothing.
 */
fun outcomeChip(reached: Boolean, deadline: LocalDate?, today: LocalDate): OutcomeChip = when {
    reached -> OutcomeChip.Reached
    deadline != null && today > deadline -> OutcomeChip.NotReached
    else -> OutcomeChip.None
}
