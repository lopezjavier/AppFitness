package io.github.lopezjavier.appfitness.feature.trainer

/** Ranges that mirror the server validation of `save_coach_intake`. */
object IntakeLimits {
    const val MIN_YEARS = 0.0
    const val MAX_YEARS = 60.0
    const val YEARS_STEP = 0.5
    const val MIN_KCAL = 1200
    const val MAX_KCAL = 6000
}

/** An inline message under the action that failed (R-31); the draft stays as it is. */
enum class IntakeNotice { SaveFailed, SaveOffline }
