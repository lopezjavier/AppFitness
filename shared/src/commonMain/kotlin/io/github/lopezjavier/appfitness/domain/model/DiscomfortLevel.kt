package io.github.lopezjavier.appfitness.domain.model

/** Ordered discomfort scale the user picks when logging (ADR-81). The two highest levels are severe. */
@Suppress("MagicNumber")
enum class DiscomfortLevel(val value: Int) {
    Tension(1),
    Mild(2),
    PinchOrSnap(3),
    SwellingOrNumbness(4),
    SharpOrInjury(5),
    ;

    val severe: Boolean get() = value >= SEVERE_FROM

    companion object {
        private const val SEVERE_FROM = 4

        /** The level stored as [value] (1..5), or null when it is not a known level. */
        fun fromValue(value: Int): DiscomfortLevel? = entries.firstOrNull { it.value == value }
    }
}
