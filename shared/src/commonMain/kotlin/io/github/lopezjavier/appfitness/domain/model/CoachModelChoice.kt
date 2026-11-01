package io.github.lopezjavier.appfitness.domain.model

/** Anthropic model the user picks for the `coach` function (test aid); [wireKey] is the value the server whitelists. */
enum class CoachModelChoice(val wireKey: String) {
    Opus("opus"),
    Sonnet("sonnet"),
    Haiku("haiku"),
    ;

    companion object {
        /** The model the plan kinds use on the server; it is never sent so the per-kind server default applies. */
        val Default: CoachModelChoice = Opus
    }
}

/** The `model` request field: null (omitted) for the default choice. */
internal fun CoachModelChoice.requestKey(): String? = if (this == CoachModelChoice.Default) null else wireKey
