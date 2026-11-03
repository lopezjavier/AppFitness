package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_add
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_remove
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_replace
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_reps
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_rest
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_change_sets
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_detail
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_drop
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h1_volume_high
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h1_volume_low
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h2_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h3_side_delta
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h3b_knee_extension
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h4_rep_range
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h5_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h5_set_type
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h6_equipment
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h6_excluded_discomfort
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h6_rest
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h6_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h6_sets_per_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h6_time
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_h7_order
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_other
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_falta_superset
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_routine_n
import io.github.lopezjavier.appfitness.composeapp.resources.folder_review_routine_prefix
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The Spanish sentence of a Falta code of the routine checker (planning/04 section 9.6); others read generic. */
@Suppress("CyclomaticComplexMethod") // One branch per checker code, on purpose.
internal fun faltaMessage(code: String): StringResource = when (code) {
    "h1_volume_low" -> Res.string.folder_review_falta_h1_volume_low
    "h1_volume_high" -> Res.string.folder_review_falta_h1_volume_high
    "h2_sessions" -> Res.string.folder_review_falta_h2_sessions
    "h3_side_delta" -> Res.string.folder_review_falta_h3_side_delta
    "h3b_knee_extension" -> Res.string.folder_review_falta_h3b_knee_extension
    "h4_rep_range" -> Res.string.folder_review_falta_h4_rep_range
    "h5_rpe" -> Res.string.folder_review_falta_h5_rpe
    "h5_set_type" -> Res.string.folder_review_falta_h5_set_type
    "h6_equipment" -> Res.string.folder_review_falta_h6_equipment
    "h6_excluded_discomfort" -> Res.string.folder_review_falta_h6_excluded_discomfort
    "h6_sessions" -> Res.string.folder_review_falta_h6_sessions
    "h6_time" -> Res.string.folder_review_falta_h6_time
    "h6_sets_per_exercise" -> Res.string.folder_review_falta_h6_sets_per_exercise
    "h6_rest" -> Res.string.folder_review_falta_h6_rest
    "h7_order" -> Res.string.folder_review_falta_h7_order
    "superset_invalid", "superset_not_needed", "superset_sets_mismatch" -> Res.string.folder_review_falta_superset
    "drop_invalid", "drop_not_needed" -> Res.string.folder_review_falta_drop
    else -> Res.string.folder_review_falta_other
}

/** The routine (and exercise) a Falta item points at, or null when it is about the whole folder. */
@Composable
internal fun FaltaItem.detail(): String? {
    val routine = falta.routineIndex?.let { routineName ?: stringResource(Res.string.folder_review_routine_n, it) }
    return when {
        routine != null && exerciseName != null ->
            stringResource(Res.string.folder_review_falta_detail, routine, exerciseName)

        else -> routine ?: exerciseName
    }
}

/** The one-line sentence of a proposed change, prefixed with its routine. */
@Composable
internal fun ChangeLine.text(): String {
    val name = exerciseName
    val sentence = when (kind) {
        ChangeKind.Add -> pluralStringResource(
            Res.plurals.folder_review_change_add,
            extra[0].toInt(),
            name,
            extra[0].toInt(),
        )

        ChangeKind.Replace -> stringResource(Res.string.folder_review_change_replace, name, extra[0])

        ChangeKind.Remove -> stringResource(Res.string.folder_review_change_remove, name)

        ChangeKind.SetSets -> pluralStringResource(
            Res.plurals.folder_review_change_sets,
            extra[0].toInt(),
            name,
            extra[0].toInt(),
        )

        ChangeKind.SetRepRange -> stringResource(Res.string.folder_review_change_reps, name, extra[0], extra[1])

        ChangeKind.SetRpe -> stringResource(Res.string.folder_review_change_rpe, name, extra[0])

        ChangeKind.SetRest -> stringResource(Res.string.folder_review_change_rest, name, extra[0])
    }
    val routine = routineName ?: stringResource(Res.string.folder_review_routine_n, routineIndex)
    return stringResource(Res.string.folder_review_routine_prefix, routine, sentence)
}
