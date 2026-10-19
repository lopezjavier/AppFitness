package io.github.lopezjavier.appfitness.domain.port

/** A short audible alert, for example when the rest timer ends. Silent when the device is muted. */
interface AlertSound {
    fun play()
}
