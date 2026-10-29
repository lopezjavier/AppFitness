package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An option of a note. Only the `Other` kind requires text; kinds are never interpreted (ADR-81). */
sealed interface NoteKind {
    /** DB / JSON value. */
    val wire: String

    /** Whether the note must carry text (the "Otro" option). */
    val requiresText: Boolean
}

/** DB values of `public.set_note_kind`: the options of a set note. */
@Serializable
enum class SetNoteKind(override val wire: String) : NoteKind {
    @SerialName("technique")
    Technique("technique"),

    @SerialName("range")
    Range("range"),

    @SerialName("grip")
    Grip("grip"),

    @SerialName("assisted")
    Assisted("assisted"),

    @SerialName("other")
    Other("other"),
    ;

    override val requiresText: Boolean get() = this == Other

    companion object {
        fun fromWire(value: String?): SetNoteKind? = entries.firstOrNull { it.wire == value }
    }
}

/** DB values of `public.exercise_note_kind`: the options of a routine exercise note. */
@Serializable
enum class ExerciseNoteKind(override val wire: String) : NoteKind {
    @SerialName("weight_up")
    WeightUp("weight_up"),

    @SerialName("weight_down")
    WeightDown("weight_down"),

    @SerialName("technique")
    Technique("technique"),

    @SerialName("setup")
    Setup("setup"),

    @SerialName("other")
    Other("other"),
    ;

    override val requiresText: Boolean get() = this == Other

    companion object {
        fun fromWire(value: String?): ExerciseNoteKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * A note: an option plus optional text (1-200 chars once trimmed). The text is required iff the kind is `Other`.
 * Validation lives here so every caller agrees with the DB checks (`notes is null or note_kind is not null`,
 * `note_kind <> 'other' or notes is not null`).
 */
data class Note<K : NoteKind>(val kind: K, val text: String?) {
    companion object {
        /** Whether [kind] and [text] form a valid note, or no note at all (no kind and no text). */
        fun <K : NoteKind> isValid(kind: K?, text: String?): Boolean {
            if (!NoteText.isValid(text)) return false
            val clean = NoteText.normalise(text)
            return if (kind == null) clean == null else !kind.requiresText || clean != null
        }

        /** The normalised note, or null when there is none or the pair is invalid. */
        fun <K : NoteKind> of(kind: K?, text: String?): Note<K>? =
            if (kind != null && isValid(kind, text)) Note(kind, NoteText.normalise(text)) else null
    }
}

/** The kind to store: [kind], or [other] for an old note that has text but no kind; null when there is no note. */
fun <K : NoteKind> resolveNoteKind(kind: K?, text: String?, other: K): K? =
    kind ?: other.takeIf { NoteText.normalise(text) != null }
