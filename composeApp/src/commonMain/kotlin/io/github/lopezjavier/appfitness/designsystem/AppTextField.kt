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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
    fun borderColor(focused: Boolean, hasError: Boolean, appColors: AppColors): Color = when {
        hasError -> appColors.error
        focused -> appColors.athleticCyan
        else -> appColors.borderStrong
    }

    fun borderWidth(focused: Boolean, hasError: Boolean): Dp = if (focused ||
        hasError
    ) {
        AppBorder.focus
    } else {
        AppBorder.thin
    }
}

/**
 * Single-line text field. The caller owns [value]; the text handed to [onValueChange] is already cut to [maxLength].
 * [label] is drawn above the field and spoken as its content description; [placeholder] is ghost text, not spoken.
 * While [errorText] is not null the border turns to the error colour and the text shows below as a polite live
 * region (tagged `<testTagPrefix>_error`, or [errorTestTag] when given). "Hecho" on the keyboard clears the focus and
 * hides the keyboard, then calls [onDone].
 *
 * [value] must be updated synchronously inside [onValueChange] (plain `mutableStateOf`), as for [NumberInput].
 */
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppTextFieldDefaults.LabelSpacing)) {
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = colors.textSecondary,
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
                .semantics { contentDescription = label }
                .let { if (testTagPrefix != null) it.testTag(testTagPrefix) else it },
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onDone()
                },
            ),
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.athleticCyan),
            decorationBox = { innerTextField ->
                TextFieldBox(focused, hasError, placeholder.takeIf { value.isEmpty() }, textStyle, innerTextField)
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
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            .let { if (tag != null) it.testTag(tag) else it },
    )
}

/** The filled, bordered box around the text; [placeholder] is non-null only while the field is empty. */
@Composable
private fun TextFieldBox(
    focused: Boolean,
    hasError: Boolean,
    placeholder: String?,
    textStyle: TextStyle,
    innerTextField: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
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
            .padding(horizontal = AppTextFieldDefaults.HorizontalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
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
