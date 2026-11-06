@file:Suppress("MatchingDeclarationName") // The defaults object sits beside the component, like the others.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_timer_running_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_start_cd
import io.github.lopezjavier.appfitness.domain.usecase.DurationFormat
import org.jetbrains.compose.resources.stringResource

object CardioTimeCellDefaults {
    /** Width the time needs at normal font scale; it grows with the font so the cell stacks before the text clips. */
    val MinFieldWidth: Dp = 40.dp
}

/**
 * The TIEMPO cell of a cardio set (R-58): the time to type (the planned [placeholder] while it is empty, mm:ss after)
 * and a trailing 48 dp play button that starts or resumes the run, which then lives in the shared timer bar. While
 * the run is going ([timerInBar]) the time is not editable and the button is disabled and tinted; while paused the
 * time can be typed. Stacks the button under the time when the cell is too narrow for both (large font). Tags (with
 * [testTagPrefix]): `_cell`, `_time`, `_input` and `_error`; the button takes [startTestTag] and the placeholder
 * [placeholderTestTag].
 */
@Composable
@Suppress("LongParameterList") // The cell is stateless: values, three callbacks and its tags.
fun CardioTimeCell(
    elapsedSeconds: Int,
    placeholder: String,
    placeholderDescription: String,
    timerInBar: Boolean,
    readOnly: Boolean,
    onStart: () -> Unit,
    onManualEntry: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
    startTestTag: String? = null,
    placeholderTestTag: String? = null,
) {
    val entry = remember { TimeEntry() }
    val time: @Composable () -> Unit = {
        if (entry.editing) {
            TimeInput(
                value = entry.draft,
                onChange = entry::edit,
                onDone = { entry.commit(onManualEntry) },
                modifier = tagged(testTagPrefix, "input"),
            )
        } else {
            val shown = if (timerInBar) 0 else elapsedSeconds
            val empty = shown == 0
            val text = DurationFormat.formatMmSs(shown)
            val edit = stringResource(Res.string.stopwatch_edit_cd, text)
            TimeButton(
                text = if (empty) placeholder else text,
                dim = empty,
                description = if (empty) placeholderDescription else edit,
                enabled = !readOnly && !timerInBar,
                onClick = entry::begin,
                modifier = if (empty) {
                    placeholderTestTag?.let { Modifier.testTag(it) } ?: Modifier
                } else {
                    tagged(testTagPrefix, "time")
                },
            )
        }
    }
    val start: @Composable () -> Unit = { StartButton(timerInBar, readOnly, onStart, startTestTag) }
    val minField = CardioTimeCellDefaults.MinFieldWidth * LocalDensity.current.fontScale
    Column(modifier.then(tagged(testTagPrefix, "cell")), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= StopwatchFieldDefaults.TouchTarget + minField) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { time() }
                    start()
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    time()
                    start()
                }
            }
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

@Composable
private fun StartButton(timerInBar: Boolean, readOnly: Boolean, onStart: () -> Unit, tag: String?) {
    AppIconButton(
        contentDescription = stringResource(
            if (timerInBar) Res.string.cardio_timer_running_cd else Res.string.stopwatch_start_cd,
        ),
        icon = Res.drawable.ic_play_arrow,
        testTag = tag ?: "cardio_time_start",
        onClick = onStart,
        enabled = !readOnly && !timerInBar,
        tint = if (timerInBar) AppTheme.colors.neonLime else AppTheme.colors.textPrimary,
    )
}

@Composable
private fun TimeButton(
    text: String,
    dim: Boolean,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = StopwatchFieldDefaults.TouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (dim) {
                AppTheme.typography.bodySmall.copy(textAlign = TextAlign.Center)
            } else {
                StopwatchFieldDefaults.timeStyle(AppTheme.typography)
            },
            color = if (dim) AppTheme.colors.ghostData else AppTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}
