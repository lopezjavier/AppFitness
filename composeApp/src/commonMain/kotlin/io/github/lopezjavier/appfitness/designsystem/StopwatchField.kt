@file:Suppress("MatchingDeclarationName") // The defaults object sits beside the component, like the others.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.ic_stop
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_input_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_invalid
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_running_state
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_start_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_stop_cd
import io.github.lopezjavier.appfitness.composeapp.resources.stopwatch_stopped_state
import io.github.lopezjavier.appfitness.domain.usecase.DurationFormat
import org.jetbrains.compose.resources.stringResource

object StopwatchFieldDefaults {
    /** The time and the toggle are each their own 48 dp touch target. */
    val TouchTarget: Dp = 48.dp

    /** Below this width the toggle moves under the time instead of beside it. */
    val SideBySideMinWidth: Dp = 100.dp
    const val MAX_INPUT_LENGTH = 8
    private const val TIME_SEPARATOR = ':'

    /** Only digits and the time separator can be typed; null rejects the edit. */
    fun sanitize(input: String): String? =
        input.takeIf { it.length <= MAX_INPUT_LENGTH && it.all { c -> c.isDigit() || c == TIME_SEPARATOR } }

    fun timeStyle(typography: AppTypography): TextStyle =
        typography.labelNumeric.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
}

/**
 * A set stopwatch: the elapsed time as mm:ss (h:mm:ss from one hour), a 48 dp start/stop button and the time itself
 * tappable to type mm:ss inline. The caller owns the clock: it passes the [elapsedSeconds] to show and reacts to
 * [onToggle] and [onManualEntry]. An invalid entry shows an inline error and is not committed. Tags (when
 * [testTagPrefix] is set): `_toggle`, `_time`, `_input` and `_error`.
 */
@Composable
fun StopwatchField(
    elapsedSeconds: Int,
    running: Boolean,
    onToggle: () -> Unit,
    onManualEntry: (Int) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    testTagPrefix: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val timeText = DurationFormat.formatMmSs(elapsedSeconds)
    val commit = {
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
    val time: @Composable () -> Unit = {
        if (editing) {
            TimeInput(draft, {
                draft = it
                invalid = false
            }, commit, Modifier.tag(testTagPrefix, "input"))
        } else {
            TimeText(
                text = timeText,
                running = running,
                enabled = !readOnly && !running,
                onClick = {
                    draft = ""
                    invalid = false
                    editing = true
                },
                modifier = Modifier.tag(testTagPrefix, "time"),
            )
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val toggle: @Composable () -> Unit = { StopwatchToggle(running, readOnly, onToggle, testTagPrefix) }
            if (maxWidth >= StopwatchFieldDefaults.SideBySideMinWidth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { time() }
                    toggle()
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    time()
                    toggle()
                }
            }
        }
        if (invalid) InvalidTimeMessage(Modifier.tag(testTagPrefix, "error"))
    }
}

@Composable
private fun InvalidTimeMessage(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.stopwatch_invalid),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun StopwatchToggle(running: Boolean, readOnly: Boolean, onToggle: () -> Unit, testTagPrefix: String?) {
    AppIconButton(
        contentDescription = stringResource(
            if (running) Res.string.stopwatch_stop_cd else Res.string.stopwatch_start_cd,
        ),
        icon = if (running) Res.drawable.ic_stop else Res.drawable.ic_play_arrow,
        testTag = childTestTag(testTagPrefix, "toggle") ?: "stopwatch_toggle",
        onClick = onToggle,
        enabled = !readOnly,
        tint = if (running) AppTheme.colors.athleticCyan else AppTheme.colors.textPrimary,
    )
}

@Composable
private fun TimeText(
    text: String,
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(
        if (running) Res.string.stopwatch_running_state else Res.string.stopwatch_stopped_state,
        text,
    )
    val edit = stringResource(Res.string.stopwatch_edit_cd, text)
    Box(
        modifier
            .heightIn(min = StopwatchFieldDefaults.TouchTarget)
            .widthIn(min = StopwatchFieldDefaults.TouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = edit, onClick = onClick)
            .semantics { stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = StopwatchFieldDefaults.timeStyle(AppTheme.typography),
            color = if (running) AppTheme.colors.athleticCyan else AppTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimeInput(value: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val colors = NumberInputDefaults.colors(focused, readOnly = false, appColors = AppTheme.colors)
    val description = stringResource(Res.string.stopwatch_input_cd)
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { text -> StopwatchFieldDefaults.sanitize(text)?.let(onChange) },
        modifier = modifier.focusRequester(focus).semantics { contentDescription = description },
        textStyle = StopwatchFieldDefaults.timeStyle(AppTheme.typography).copy(color = colors.text),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.cursor),
        decorationBox = { inner ->
            Box(
                Modifier
                    .widthIn(min = StopwatchFieldDefaults.TouchTarget)
                    .heightIn(min = StopwatchFieldDefaults.TouchTarget)
                    .clip(NumberInputDefaults.Shape)
                    .background(colors.container)
                    .border(NumberInputDefaults.borderWidth(focused, false), colors.border, NumberInputDefaults.Shape)
                    .padding(horizontal = NumberInputDefaults.HorizontalPadding),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

private fun Modifier.tag(prefix: String?, part: String): Modifier =
    childTestTag(prefix, part)?.let { testTag(it) } ?: this

@Preview
@Composable
private fun StopwatchFieldPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StopwatchField(elapsedSeconds = 90, running = false, onToggle = {}, onManualEntry = {})
            StopwatchField(elapsedSeconds = 3_725, running = true, onToggle = {}, onManualEntry = {})
        }
    }
}
