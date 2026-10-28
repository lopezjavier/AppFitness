package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.RestSound

/** A short audible alert, for example when the rest timer ends. Silent when the device is muted. */
interface AlertSound {
    /** Plays the currently chosen [RestSound]. */
    fun play()

    /** Plays [sound] once so the user can compare candidates (temporary chooser). */
    fun preview(sound: RestSound)
}
