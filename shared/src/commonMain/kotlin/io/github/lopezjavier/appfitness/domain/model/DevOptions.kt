package io.github.lopezjavier.appfitness.domain.model

/** Developer-only switches; every flag is false in release builds (provided from `BuildConfig.DEBUG`). */
data class DevOptions(val showCoachPing: Boolean = false)
