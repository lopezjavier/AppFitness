package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_bullet_1
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_bullet_2
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_bullet_3
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_bullet_4
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_bullet_5
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_cd
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_intro
import io.github.lopezjavier.appfitness.composeapp.resources.help_discomfort_level_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_block_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_1
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_2
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_3
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_4
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_5
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_level_option_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_note_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_resolve
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_resolve_offline
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_discomfort_offline
import io.github.lopezjavier.appfitness.composeapp.resources.note_too_long
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseStatusRow
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.ScaleOptionRow
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.model.NoteText
import org.jetbrains.compose.resources.stringResource

/** The Spanish label of a discomfort level (also the level text of the open row). */
@Composable
internal fun DiscomfortLevel.label(): String = stringResource(
    when (this) {
        DiscomfortLevel.Tension -> Res.string.live_discomfort_level_1
        DiscomfortLevel.Mild -> Res.string.live_discomfort_level_2
        DiscomfortLevel.PinchOrSnap -> Res.string.live_discomfort_level_3
        DiscomfortLevel.SwellingOrNumbness -> Res.string.live_discomfort_level_4
        DiscomfortLevel.SharpOrInjury -> Res.string.live_discomfort_level_5
    },
)

/** "?" beside the level heading: what each of the five levels means, ascending (R-45). */
@Composable
internal fun DiscomfortLevelHelpButton(testTag: String, modifier: Modifier = Modifier) {
    HelpButton(
        contentDescription = stringResource(Res.string.help_discomfort_level_cd),
        title = stringResource(Res.string.help_discomfort_level_title),
        content = HelpContent(
            paragraphs = listOf(stringResource(Res.string.help_discomfort_level_intro)),
            bullets = listOf(
                stringResource(Res.string.help_discomfort_level_bullet_1),
                stringResource(Res.string.help_discomfort_level_bullet_2),
                stringResource(Res.string.help_discomfort_level_bullet_3),
                stringResource(Res.string.help_discomfort_level_bullet_4),
                stringResource(Res.string.help_discomfort_level_bullet_5),
            ),
        ),
        testTag = testTag,
        modifier = modifier,
    )
}

/** Room for a paste: the field never cuts the text, it flags more than [NoteText.MAX] characters. */
private const val FIELD_LIMIT = 1000
private const val NOTE_LINES = 3

/**
 * "Registrar molestia" (edit archetype): an optional multi-line note and "Guardar", disabled over 200 characters or
 * offline. The draft survives recreation; closing without saving discards it.
 */
@Composable
internal fun DiscomfortSheet(
    position: Int,
    online: Boolean,
    saving: Boolean,
    onSave: (DiscomfortLevel, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    // Saved by `value` (ADR-40); 0 = nothing picked yet, there is no preselection.
    var pickedValue by rememberSaveable { mutableIntStateOf(0) }
    val level = DiscomfortLevel.fromValue(pickedValue)
    val tag = "live_exercise_${position}_discomfort"
    val tooLong = !NoteText.isValid(draft)
    AppEditSheet(
        title = stringResource(Res.string.live_discomfort_sheet_title),
        commit = SheetCommit.Save,
        onCommit = { level?.let { onSave(it, draft.trim().ifEmpty { null }) } },
        onDismiss = onDismiss,
        commitEnabled = online && level != null && !tooLong,
        saving = saving,
        commitHint = stringResource(Res.string.live_exercise_menu_discomfort_offline).takeIf { !online },
        testTagPrefix = "${tag}_sheet",
    ) {
        DiscomfortLevelPicker(tag, level) { pickedValue = it.value }
        AppTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(Res.string.live_discomfort_note_label),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            maxLength = FIELD_LIMIT,
            errorText = stringResource(Res.string.note_too_long).takeIf { tooLong },
            testTagPrefix = "${tag}_note",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = NOTE_LINES,
        )
    }
}

/** The "Nivel" heading with its help button and the five coloured options; nothing is preselected. */
@Composable
private fun DiscomfortLevelPicker(tag: String, selected: DiscomfortLevel?, onPick: (DiscomfortLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.live_discomfort_level_label),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            DiscomfortLevelHelpButton("${tag}_level_help")
        }
        Column(
            Modifier.selectableGroup().testTag("${tag}_levels"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DiscomfortLevel.entries.forEach { option ->
                val label = option.label()
                ScaleOptionRow(
                    label = label,
                    color = AppTheme.colors.discomfortScale(option),
                    selected = option == selected,
                    onClick = { onPick(option) },
                    testTag = "${tag}_level_${option.value}",
                    contentDescription = stringResource(
                        Res.string.live_discomfort_level_option_cd,
                        label,
                        option.value,
                    ),
                )
            }
        }
    }
}

/**
 * The open discomfort of an exercise under its heading, as one status row (R-51): level dot, level text and the
 * "Ya no me molesta" action. Resolving needs the server (the hint sits inside the row). The row speaks
 * "Molestia: <level>".
 */
@Composable
internal fun DiscomfortBlock(
    position: Int,
    discomfort: LiveDiscomfort,
    online: Boolean,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = "live_exercise_${position}_discomfort"
    val levelLabel = discomfort.level.label()
    ExerciseStatusRow(
        text = levelLabel,
        marker = {
            Box(Modifier.size(Spacing.md).background(AppTheme.colors.discomfortScale(discomfort.level), CircleShape))
        },
        modifier = modifier,
        testTag = "${tag}_block",
        trailing = {
            GhostButton(
                text = stringResource(Res.string.live_discomfort_resolve),
                onClick = onResolve,
                enabled = online && !discomfort.resolving,
                loading = discomfort.resolving,
                tone = GhostTone.Neutral,
                modifier = Modifier.testTag("${tag}_resolve"),
            )
        },
        supporting = stringResource(Res.string.live_discomfort_resolve_offline).takeIf { !online },
        contentDescription = stringResource(Res.string.live_discomfort_block_cd, levelLabel),
        primaryText = true,
        textTestTag = "${tag}_level",
        supportingTestTag = "${tag}_resolve_hint",
    )
}
