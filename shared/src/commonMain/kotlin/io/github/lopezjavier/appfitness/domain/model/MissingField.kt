package io.github.lopezjavier.appfitness.domain.model

/** A piece of user data the `coach` function reported as missing in a `coach_missing_data` error. */
enum class MissingField {
    Intention,
    Sex,
    Weight,
    Expenditure,
    Place,

    /** The server named a field this app version does not know. */
    Unknown,
}
