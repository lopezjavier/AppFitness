package io.github.lopezjavier.appfitness.domain.port

/**
 * Device haptics that do not belong to a UI gesture. UI feedback (set completed, saved, refused...)
 * goes through the design system's `AppHaptics`; this port only keeps the end-of-rest alert.
 */
interface Haptics {
    /** The end-of-rest vibration: a long three-pulse pattern (the sound is the separate `AlertSound` port). */
    fun alert()
}
