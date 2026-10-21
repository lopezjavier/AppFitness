package io.github.lopezjavier.appfitness.domain.usecase

/** Outcome of [ValidateTemplateName]. */
sealed interface TemplateNameCheck {
    data object Valid : TemplateNameCheck

    data object Blank : TemplateNameCheck

    /** More than [ValidateTemplateName.MAX_LENGTH] characters. */
    data object TooLong : TemplateNameCheck
}

/** A comida name is valid with 1 to 60 characters once trimmed. */
class ValidateTemplateName {
    operator fun invoke(name: String): TemplateNameCheck {
        val length = name.trim().length
        return when {
            length == 0 -> TemplateNameCheck.Blank
            length > MAX_LENGTH -> TemplateNameCheck.TooLong
            else -> TemplateNameCheck.Valid
        }
    }

    companion object {
        const val MAX_LENGTH = 60
    }
}
