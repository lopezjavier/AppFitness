package io.github.lopezjavier.appfitness.domain.port

/** The short audible alert of the end of a rest ("Campana suave"). Silent when the device is muted. */
interface AlertSound {
    fun play()
}
