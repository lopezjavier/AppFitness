package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_cd
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_intro
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_other
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_setup
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_technique
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_title
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_weight_down
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_exercise_weight_up
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_assisted
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_cd
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_grip
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_intro
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_other
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_range
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_technique
import io.github.lopezjavier.appfitness.composeapp.resources.note_help_set_title
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_exercise_setup
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_exercise_technique
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_exercise_weight_down
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_exercise_weight_up
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_other
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_set_assisted
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_set_grip
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_set_range
import io.github.lopezjavier.appfitness.composeapp.resources.note_kind_set_technique
import io.github.lopezjavier.appfitness.composeapp.resources.note_summary
import io.github.lopezjavier.appfitness.domain.model.ExerciseNoteKind
import io.github.lopezjavier.appfitness.domain.model.SetNoteKind
import org.jetbrains.compose.resources.stringResource

/** The Spanish label of a set note option. */
@Composable
fun SetNoteKind.label(): String = stringResource(
    when (this) {
        SetNoteKind.Technique -> Res.string.note_kind_set_technique
        SetNoteKind.Range -> Res.string.note_kind_set_range
        SetNoteKind.Grip -> Res.string.note_kind_set_grip
        SetNoteKind.Assisted -> Res.string.note_kind_set_assisted
        SetNoteKind.Other -> Res.string.note_kind_other
    },
)

/** The Spanish label of a routine exercise note option. */
@Composable
fun ExerciseNoteKind.label(): String = stringResource(
    when (this) {
        ExerciseNoteKind.WeightUp -> Res.string.note_kind_exercise_weight_up
        ExerciseNoteKind.WeightDown -> Res.string.note_kind_exercise_weight_down
        ExerciseNoteKind.Technique -> Res.string.note_kind_exercise_technique
        ExerciseNoteKind.Setup -> Res.string.note_kind_exercise_setup
        ExerciseNoteKind.Other -> Res.string.note_kind_other
    },
)

/** All options of a set note, in display order. */
@Composable
fun setNoteOptions(): List<NoteSheetOption<SetNoteKind>> = SetNoteKind.entries.map { NoteSheetOption(it, it.label()) }

/** All options of a routine exercise note, in display order. */
@Composable
fun exerciseNoteOptions(): List<NoteSheetOption<ExerciseNoteKind>> =
    ExerciseNoteKind.entries.map { NoteSheetOption(it, it.label()) }

/** The "?" of the set note sheet: it is for this workout only. */
@Composable
fun setNoteHelp(): NoteSheetHelp = NoteSheetHelp(
    contentDescription = stringResource(Res.string.note_help_set_cd),
    title = stringResource(Res.string.note_help_set_title),
    content = HelpContent(
        paragraphs = listOf(stringResource(Res.string.note_help_set_intro)),
        bullets = listOf(
            stringResource(Res.string.note_help_set_technique),
            stringResource(Res.string.note_help_set_range),
            stringResource(Res.string.note_help_set_grip),
            stringResource(Res.string.note_help_set_assisted),
            stringResource(Res.string.note_help_set_other),
        ),
    ),
)

/** The "?" of the routine exercise note sheet: it is saved in the routine. */
@Composable
fun exerciseNoteHelp(): NoteSheetHelp = NoteSheetHelp(
    contentDescription = stringResource(Res.string.note_help_exercise_cd),
    title = stringResource(Res.string.note_help_exercise_title),
    content = HelpContent(
        paragraphs = listOf(stringResource(Res.string.note_help_exercise_intro)),
        bullets = listOf(
            stringResource(Res.string.note_help_exercise_weight_up),
            stringResource(Res.string.note_help_exercise_weight_down),
            stringResource(Res.string.note_help_exercise_technique),
            stringResource(Res.string.note_help_exercise_setup),
            stringResource(Res.string.note_help_exercise_other),
        ),
    ),
)

/** What a shown note says: "<option>" or "<option> · <text>". */
@Composable
fun noteSummary(label: String, text: String?): String =
    if (text.isNullOrBlank()) label else stringResource(Res.string.note_summary, label, text)

/** The shown text of a set note, null when there is none; a note without a kind (not expected) shows its text. */
@Composable
fun setNoteSummary(kind: SetNoteKind?, text: String?): String? =
    kind?.let { noteSummary(it.label(), text) } ?: text?.takeIf { it.isNotBlank() }

/** The shown text of a routine exercise note, null when there is none. */
@Composable
fun exerciseNoteSummary(kind: ExerciseNoteKind?, text: String?): String? =
    kind?.let { noteSummary(it.label(), text) } ?: text?.takeIf { it.isNotBlank() }
