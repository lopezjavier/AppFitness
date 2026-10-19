package io.github.lopezjavier.appfitness.domain.port

/** Short tactile feedback of the device. Implemented per platform. */
interface Haptics {
    /** One short tick, for example when a set is checked. */
    fun tick()

    /** A longer buzz than [tick], for example when the rest timer ends. */
    fun alert()
}
