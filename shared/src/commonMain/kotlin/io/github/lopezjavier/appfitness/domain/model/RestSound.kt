package io.github.lopezjavier.appfitness.domain.model

/**
 * The end-of-rest sound candidates (US-64). Temporary choice: T-148 keeps one and removes the chooser.
 * [id] is the stable string stored in the preferences.
 */
enum class RestSound(val id: String) {
    SYSTEM("system"),
    CHIME("chime"),
    DOUBLE_BEEP("double_beep"),
    ;

    companion object {
        val Default: RestSound = SYSTEM

        /** The sound stored under [id]; null or an unknown id gives the [Default]. */
        fun fromId(id: String?): RestSound = entries.firstOrNull { it.id == id } ?: Default
    }
}
