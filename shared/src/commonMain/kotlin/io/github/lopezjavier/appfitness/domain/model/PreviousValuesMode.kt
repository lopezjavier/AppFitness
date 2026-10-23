package io.github.lopezjavier.appfitness.domain.model

/** Which earlier performance a live set shows as its "previous" values (training preference). */
enum class PreviousValuesMode {
    /** The last time the exercise was done in any session. */
    Overall,

    /** The last time the exercise was done in the same routine. */
    Routine,
}
