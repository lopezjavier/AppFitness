@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry, colours and pure rules of [AppTextField]. */
object AppTextFieldDefaults {
    /** The field is its own touch target, taller than the 48 dp minimum. */
    val MinHeight: Dp = 52.dp

    val HorizontalPadding: Dp = Spacing.md
    val LabelSpacing: Dp = Spacing.xs
    const val DEFAULT_MAX_LENGTH = 100

    /** [text] cut to [maxLength] characters. */
    fun limit(text: String, maxLength: Int): String = text.take(maxLength.coerceAtLeast(0))

    /** An invalid field shows the error colour even while focused; the text below also says it (not colour-only). */
    fun borderColor(focused: Boolean, hasError: Boolean, appColors: AppColors): Color =
        AppInputStyle.borderColor(focused, hasError, appColors)

    fun borderWidth(focused: Boolean, hasError: Boolean): Dp = AppInputStyle.borderWidth(focused, hasError)

    /** Width of the optional [AppTextField] `leading` / `trailing` slots (a touch target). */
    val SlotSize: Dp = 48.dp
}

/**
 * Single-line text field. The caller owns [value]; the text handed to [onValueChange] is already cut to [maxLength].
 * [label] is drawn above the field and spoken as its content description; [placeholder] is ghost text, not spoken.
 * While [errorText] is not null the border turns to the error colour and the text shows below as a polite live
 * region (tagged `<testTagPrefix>_error`, or [errorTestTag] when given). "Hecho" on the keyboard clears the focus and
 * hides the keyboard, then calls [onDone]. [stateDescription] is spoken after the label (e.g. a counter "n de 200").
 *
 * [value] must be updated synchronously inside [onValueChange] (plain `mutableStateOf`), as for [NumberInput].
 *
 * v2: [trailing] / [leading] are 48 dp slots inside the box (clear button, unit, icon); [imeAction] with
 * [onImeAction] overrides the keyboard action (when null, "Hecho" keeps the behaviour above); [keyboardType],
 * [capitalization] and [autofocus] (focus and keyboard on first composition) configure the input.
 */
@Suppress("LongMethod") // One BasicTextField wiring; splitting would only move the parameters around.
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int = AppTextFieldDefaults.DEFAULT_MAX_LENGTH,
    errorText: String? = null,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
    testTagPrefix: String? = null,
    errorTestTag: String? = null,
    stateDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    autofocus: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = AppTheme.colors
    val hasError = errorText != null
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val textStyle = AppTheme.typography.bodyLarge.copy(color = colors.textPrimary)

    // The cursor lives in a TextFieldValue; an external change of [value] resets it to the end.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    val focusRequester = remember { FocusRequester() }
    if (autofocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val imeHandler: () -> Unit = {
        if (onImeAction != null) {
            onImeAction()
        } else if (imeAction == ImeAction.Done) {
            keyboard?.hide()
            focusManager.clearFocus()
            onDone()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTextFieldDefaults.LabelSpacing)) {
        Text(
            text = label,
            style = AppInputStyle.labelStyle(AppTheme.typography),
            color = AppInputStyle.labelColor(colors),
            modifier = Modifier.clearAndSetSemantics {},
        )
        BasicTextField(
            value = shown,
            onValueChange = { raw ->
                val limited = AppTextFieldDefaults.limit(raw.text, maxLength)
                val edited =
                    if (limited == raw.text) {
                        raw
                    } else {
                        TextFieldValue(limited, TextRange(raw.selection.end.coerceAtMost(limited.length)))
                    }
                fieldValue = edited
                if (edited.text != value) onValueChange(edited.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = label
                    stateDescription?.let { this.stateDescription = it }
                }
                .let { if (testTagPrefix != null) it.testTag(testTagPrefix) else it },
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { imeHandler() },
                onNext = { imeHandler() },
                onSearch = { imeHandler() },
                onGo = { imeHandler() },
                onSend = { imeHandler() },
            ),
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.athleticCyan),
            decorationBox = { innerTextField ->
                TextFieldBox(
                    focused,
                    hasError,
                    placeholder.takeIf { value.isEmpty() },
                    textStyle,
                    leading,
                    trailing,
                    innerTextField,
                )
            },
        )
        if (errorText != null) FieldError(errorText, errorTestTag ?: childTestTag(testTagPrefix, "error"))
    }
}

/** The error line below the field: a polite live region, so it is announced when it appears. */
@Composable
private fun FieldError(text: String, tag: String?) {
    Text(
        text = text,
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.error,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            error(text)
        }
            .let { if (tag != null) it.testTag(tag) else it },
    )
}

/** The filled, bordered box around the text; [placeholder] is non-null only while the field is empty. */
@Suppress("LongParameterList") // Box content and slots.
@Composable
private fun TextFieldBox(
    focused: Boolean,
    hasError: Boolean,
    placeholder: String?,
    textStyle: TextStyle,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    innerTextField: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTextFieldDefaults.MinHeight)
            .clip(AppShapes.base)
            .background(colors.surfaceOverlay)
            .border(
                AppTextFieldDefaults.borderWidth(focused, hasError),
                AppTextFieldDefaults.borderColor(focused, hasError, colors),
                AppShapes.base,
            )
            .padding(
                start = if (leading != null) 0.dp else AppTextFieldDefaults.HorizontalPadding,
                end = if (trailing != null) 0.dp else AppTextFieldDefaults.HorizontalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) FieldSlot(leading)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (placeholder != null) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = colors.ghostData,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            innerTextField()
        }
        if (trailing != null) FieldSlot(trailing)
    }
}

/** A 48 dp square slot for a leading icon or a trailing clear button / unit. */
@Composable
private fun FieldSlot(content: @Composable () -> Unit) {
    Box(
        Modifier.size(AppTextFieldDefaults.SlotSize),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Preview
@Composable
private fun AppTextFieldPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            AppTextField("Javier", {}, "Nombre")
            AppTextField("", {}, "Nombre", placeholder = "¿Cómo te llamas?")
            AppTextField("", {}, "Nombre", errorText = "Escribe tu nombre")
        }
    }
}
