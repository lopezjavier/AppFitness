@file:Suppress("MatchingDeclarationName") // The defaults object sits beside the component, like the others.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_start_cd
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_start_label
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_stop_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.ic_stop
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_edit_cd
import io.github.lopezjavier.appfitness.domain.usecase.DurationFormat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object CardioTimeCellDefaults {
    /** Width of the pill that starts the run when the circle does not fit on the row's first line. */
    val PillHeight: Dp = 40.dp
}

/**
 * The TIEMPO field of a cardio set (R-58): drawn as a field like KG and REPS (C-91), holding the time to type (the
 * planned [placeholder] while it is empty, mm:ss after). The start control is a separate cell of the row
 * ([CardioStart]); the run lives in the shared timer bar. While the run is going ([timerInBar]) the time counts up
 * here and is not editable; while stopped (and not done) it can be typed. Tags (with [testTagPrefix]): `_cell`,
 * `_time`, `_input` and `_error`; the placeholder takes [placeholderTestTag].
 */
@Composable
@Suppress("LongParameterList") // The cell's state, handler and tags.
fun CardioTimeCell(
    elapsedSeconds: Int,
    placeholder: String,
    placeholderDescription: String,
    timerInBar: Boolean,
    readOnly: Boolean,
    onManualEntry: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    placeholderTestTag: String? = null,
) {
    val entry = remember { TimeEntry() }
    Column(modifier.then(tagged(testTagPrefix, "cell")), horizontalAlignment = Alignment.CenterHorizontally) {
        if (entry.editing) {
            TimeInput(
                value = entry.draft,
                onChange = entry::edit,
                onDone = { entry.commit(onManualEntry) },
                modifier = tagged(testTagPrefix, "input"),
            )
        } else {
            val empty = elapsedSeconds == 0 && !timerInBar
            val text = DurationFormat.formatMmSs(elapsedSeconds)
            val typable = !readOnly && !timerInBar
            val edit = if (typable) stringResource(Res.string.stopwatch_edit_cd, text) else text
            TimeButton(
                text = if (empty) placeholder else text,
                dim = empty,
                done = readOnly,
                description = if (empty) placeholderDescription else edit,
                enabled = typable,
                onClick = entry::begin,
                modifier = if (empty) {
                    placeholderTestTag?.let { Modifier.testTag(it) } ?: Modifier
                } else {
                    tagged(testTagPrefix, "time")
                },
            )
        }
        if (entry.invalid) InvalidTimeMessage(tagged(testTagPrefix, "error"))
    }
}

/** The typed-time draft of the cell: opens empty, flags an unparsable entry and commits a valid mm:ss. */
private class TimeEntry {
    var editing by mutableStateOf(false)
        private set
    var draft by mutableStateOf("")
        private set
    var invalid by mutableStateOf(false)
        private set

    fun begin() {
        draft = ""
        invalid = false
        editing = true
    }

    fun edit(text: String) {
        draft = text
        invalid = false
    }

    fun commit(onManualEntry: (Int) -> Unit) {
        val seconds = DurationFormat.parseMmSs(draft)
        when {
            draft.isBlank() -> editing = false

            seconds == null -> invalid = true

            else -> {
                onManualEntry(seconds)
                editing = false
            }
        }
    }
}

private fun tagged(prefix: String?, part: String): Modifier =
    childTestTag(prefix, part)?.let { Modifier.testTag(it) } ?: Modifier

/**
 * The start control of a cardio set (C-92): a tonal circle on the row's first line, or an "Iniciar" [pill] on the
 * second line when the first has no room. A run in the bar disables it with a lime glyph; a done set with a dim one.
 */
@Composable
internal fun CardioStart(
    timerInBar: Boolean,
    done: Boolean,
    onStart: () -> Unit,
    tag: String?,
    pill: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (timerInBar) Res.string.cardio_stop_cd else Res.string.cardio_start_cd)
    val enabled = !done
    val disabledGlyph = AppTheme.colors.ghostData
    val icon = if (timerInBar) Res.drawable.ic_stop else Res.drawable.ic_play_arrow
    val testTag = tag ?: "cardio_time_start"
    if (!pill) {
        CircleIconButton(
            contentDescription = description,
            icon = icon,
            testTag = testTag,
            onClick = onStart,
            modifier = modifier,
            enabled = enabled,
            size = SetCompletionCheckboxDefaults.VisualSize,
            disabledGlyph = disabledGlyph,
        )
        return
    }
    val content = if (enabled) AppTheme.colors.textPrimary else disabledGlyph
    Box(
        modifier
            .heightIn(min = SetCompletionCheckboxDefaults.TouchTarget)
            .clip(AppShapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onStart)
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(CardioTimeCellDefaults.PillHeight).clip(AppShapes.full)
                .background(AppTheme.colors.surfaceControl).padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(AppButtonDefaults.LeadingIconSize),
            )
            // Icon only while running (stop glyph); the "Iniciar" label stays as in main when stopped.
            if (!timerInBar) {
                Text(
                    text = stringResource(Res.string.cardio_start_label),
                    style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList") // One field: text, state, description and click.
private fun TimeButton(
    text: String,
    dim: Boolean,
    done: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var compact by remember(text) { mutableStateOf(false) }
    val style = when {
        !dim -> StopwatchFieldDefaults.timeStyle(AppTheme.typography)

        compact -> AppTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)

        else -> StopwatchFieldDefaults.timeStyle(AppTheme.typography)
            .copy(fontWeight = NumberInputDefaults.PlaceholderWeight)
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = StopwatchFieldDefaults.TouchTarget)
            .timeFieldContainer(done)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = if (dim) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (dim && it.hasVisualOverflow) compact = true },
        )
    }
}
