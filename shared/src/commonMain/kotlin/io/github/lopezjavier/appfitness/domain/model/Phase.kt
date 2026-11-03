package io.github.lopezjavier.appfitness.domain.model

/** Training/calorie phase of a coach plan (`coach_plans.phase`). */
enum class Phase { Cut, Gain, Maintenance, Recomposition }

/** Why the plan is in its phase; the Spanish text belongs to the UI. */
enum class PhaseReason { Intention, UserBusy }

/** User-chosen options of a plan: how fast to cut (percent of body weight per week) and the optional diet break. */
data class PhaseOptions(val cutRatePct: Double = CutRate.DEFAULT, val dietBreak: Boolean = false)

/** Valid weekly cut rates (percent of body weight), shared by the picker and the view models. */
object CutRate {
    const val MIN = 0.5
    const val MAX = 1.0
    const val DEFAULT = 0.5

    fun isValid(pct: Double): Boolean = pct in MIN..MAX
}

/** The phase the first plan of an [intention] starts in (the same table as the backend). */
fun phaseForIntention(intention: String): Phase = when (intention) {
    "fat_loss" -> Phase.Cut
    "muscle" -> Phase.Gain
    "recomposition" -> Phase.Recomposition
    else -> Phase.Maintenance
}

/** Maps a stored phase code; null for an unknown or missing one. */
fun phaseOrNull(code: String?): Phase? = when (code) {
    "cut" -> Phase.Cut
    "gain" -> Phase.Gain
    "maintenance" -> Phase.Maintenance
    "recomposition" -> Phase.Recomposition
    else -> null
}

/**
 * Maps a stored phase code: null when missing, [Phase.Maintenance] when present but unknown (a retired code such as
 * the old `regain` or `minimal` reads as Maintenance, never as Cut).
 */
fun storedPhaseOrNull(code: String?): Phase? = code?.let { phaseOrNull(it) ?: Phase.Maintenance }

/** Maps a stored reason code; an unknown, retired (`returning_*`) or missing one is [PhaseReason.Intention]. */
fun phaseReasonOf(code: String?): PhaseReason = when (code) {
    "user_busy" -> PhaseReason.UserBusy
    else -> PhaseReason.Intention
}
