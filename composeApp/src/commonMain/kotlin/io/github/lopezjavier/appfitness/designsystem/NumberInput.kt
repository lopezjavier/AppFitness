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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours of [NumberInput]. */
@Immutable
data class NumberInputColors(
    val container: Color,
    val border: Color,
    val text: Color,
    val placeholder: Color,
    val label: Color,
    val cursor: Color,
)

object NumberInputDefaults {
    /** Ghost (planned) text is lighter than typed text; colour alone must not tell them apart. */
    val PlaceholderWeight = FontWeight.W300

    /** The field is its own 48 dp touch target. */
    val MinHeight: Dp = 48.dp

    /** Inputs and rows use the base radius (05 §4). */
    val Shape = AppShapes.base
    val BorderWidth: Dp = AppBorder.thin
    val FocusBorderWidth: Dp = AppBorder.focus

    /** Narrow table cells. */
    val HorizontalPadding: Dp = Spacing.xs
    val LabelSpacing: Dp = Spacing.xs

    /** End space kept free for the unit text so a long value never overlaps it. */
    val UnitReserve: Dp = 28.dp
    const val DECIMAL_SEPARATOR = ','
    const val DEFAULT_MAX_INTEGER_DIGITS = 4
    private const val ALTERNATE_SEPARATOR = '.'
    private const val ZERO = '0'

    /**
     * Returns the normalized text, or null to reject the edit (the field keeps its old value).
     * Only ASCII digits and at most one separator ('.' or ',', shown as ',') are accepted; the number of
     * decimals and integer digits is limited, a leading separator becomes "0," and leading zeros are
     * collapsed. A trailing separator is kept while typing and "" means no value. Negative limits are
     * coerced to 0.
     */
    @Suppress("ReturnCount") // Early returns keep the validation rules flat.
    fun sanitize(input: String, maxDecimals: Int, maxIntegerDigits: Int = DEFAULT_MAX_INTEGER_DIGITS): String? {
        val text = input.replace(ALTERNATE_SEPARATOR, DECIMAL_SEPARATOR)
        if (text.any { it != DECIMAL_SEPARATOR && it !in ZERO..'9' }) return null
        if (text.count { it == DECIMAL_SEPARATOR } > 1) return null
        val hasSeparator = DECIMAL_SEPARATOR in text
        val integerRaw = text.substringBefore(DECIMAL_SEPARATOR)
        val decimals = if (hasSeparator) text.substringAfter(DECIMAL_SEPARATOR) else ""
        if (hasSeparator && (maxDecimals <= 0 || decimals.length > maxDecimals)) return null
        val trimmed = integerRaw.trimStart(ZERO)
        val integer =
            when {
                trimmed.isNotEmpty() -> trimmed
                integerRaw.isNotEmpty() || hasSeparator -> ZERO.toString()
                else -> ""
            }
        if (integer.length > maxIntegerDigits.coerceAtLeast(0)) return null
        return if (hasSeparator) "$integer$DECIMAL_SEPARATOR$decimals" else integer
    }

    /** "87,5" becomes 87.5, "87," becomes 87.0 and ",5" becomes 0.5; "", "," and non-numeric text give null. */
    fun parse(text: String): Double? {
        if (text.none { it in ZERO..'9' }) return null
        val sanitized = sanitize(text, maxDecimals = Int.MAX_VALUE, maxIntegerDigits = Int.MAX_VALUE)
        return sanitized?.replace(DECIMAL_SEPARATOR, ALTERNATE_SEPARATOR)?.toDoubleOrNull()
    }

    /**
     * Sanitizes an edit coming from the text field and keeps the cursor coherent: when normalization changes
     * the length (",": "0,"), the cursor moves by the length delta. Returns null to reject the edit.
     */
    fun applyEdit(raw: TextFieldValue, maxDecimals: Int, maxIntegerDigits: Int): TextFieldValue? =
        sanitize(raw.text, maxDecimals, maxIntegerDigits)?.let { sanitized ->
            if (sanitized == raw.text) {
                raw
            } else {
                val cursor = (raw.selection.end + sanitized.length - raw.text.length).coerceIn(0, sanitized.length)
                TextFieldValue(sanitized, TextRange(cursor))
            }
        }

    fun keyboardType(maxDecimals: Int): KeyboardType =
        if (maxDecimals > 0) KeyboardType.Decimal else KeyboardType.Number

    /** Read-only fields never show the focus border. */
    fun colors(
        focused: Boolean,
        readOnly: Boolean,
        appColors: AppColors,
        hasError: Boolean = false,
    ): NumberInputColors = NumberInputColors(
        container = appColors.surfaceOverlay,
        border = AppInputStyle.borderColor(focused && !readOnly, hasError, appColors),
        text = appColors.textPrimary,
        // Planned values are editable data, so AA contrast (textSecondary) rather than ghostData.
        placeholder = appColors.textSecondary,
        label = AppInputStyle.labelColor(appColors),
        cursor = appColors.athleticCyan,
    )

    fun borderWidth(focused: Boolean, readOnly: Boolean, hasError: Boolean = false): Dp =
        if ((focused && !readOnly) || hasError) FocusBorderWidth else BorderWidth

    /**
     * "Entre 30 y 300 kg" when [value] is outside [min]..[max]; null when it is in range or absent (an empty
     * field is not a range error). [template] is the `number_range_error` string with `%1$s %2$s %3$s`.
     */
    fun rangeError(value: Double?, min: Double, max: Double, unit: String, template: String): String? =
        if (value == null || value in min..max) {
            null
        } else {
            template
                .replace("%1\$s", AppNumberFormat.decimal(min, maxDecimals = 2))
                .replace("%2\$s", AppNumberFormat.decimal(max, maxDecimals = 2))
                .replace("%3\$s", unit)
                .trim()
        }

    fun textStyle(typography: AppTypography): TextStyle = typography.labelNumeric.copy(textAlign = TextAlign.Center)

    fun labelStyle(typography: AppTypography): TextStyle = AppInputStyle.labelStyle(typography)
}

/**
 * Numeric text field with a decimal comma. The caller owns [value]; [onValueChange] only receives text
 * that passed [NumberInputDefaults.sanitize] (an invalid edit is rejected as a whole). [label] is always
 * spoken; it is drawn above the field when [showLabel] is true. The [placeholder] is ghost data shown while
 * the field is empty and is not spoken (the field announces [placeholderDescription], e.g. "Previsto", instead).
 * The caller's [modifier] lands on the text field node.
 *
 * [value] must be updated synchronously inside [onValueChange] (plain `mutableStateOf`/`TextFieldState`-style
 * state). A `StateFlow` + `collectAsState` round-trip reverts for one frame and moves the cursor to the end on
 * mid-text edits (relevant to the E3/E5 ViewModels).
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // One BasicTextField wiring with its decoration.
@Composable
fun NumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxDecimals: Int = 0,
    maxIntegerDigits: Int = NumberInputDefaults.DEFAULT_MAX_INTEGER_DIGITS,
    showLabel: Boolean = true,
    readOnly: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    errorText: String? = null,
    errorTestTag: String? = null,
    unit: String? = null,
    placeholderDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hasError = errorText != null
    val colors = NumberInputDefaults.colors(focused, readOnly, AppTheme.colors, hasError)
    val textStyle = NumberInputDefaults.textStyle(AppTheme.typography).copy(color = colors.text)
    val plannedState = placeholderDescription.takeIf { value.isEmpty() && placeholder != null }
    val fieldModifier = modifier.semantics {
        if (!showLabel) contentDescription = label
        plannedState?.let { stateDescription = it }
    }
    // The cursor lives in a TextFieldValue so normalization ("," -> "0,") can move it; an external change of
    // [value] (e.g. fill from previous) resets it to the end. State is only written in onValueChange.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    // Select-all on focus gain (R-25): typing then replaces the value; a later tap moves the cursor normally.
    LaunchedEffect(focused) {
        if (focused && !readOnly) fieldValue = TextFieldValue(value, TextRange(0, value.length))
    }
    BasicTextField(
        value = shown,
        onValueChange = { raw ->
            NumberInputDefaults.applyEdit(raw, maxDecimals, maxIntegerDigits)?.let { edited ->
                fieldValue = edited
                if (edited.text != value) onValueChange(edited.text)
            }
        },
        modifier = fieldModifier,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions =
        KeyboardOptions(keyboardType = NumberInputDefaults.keyboardType(maxDecimals), imeAction = imeAction),
        keyboardActions = onImeAction?.let { action ->
            KeyboardActions(
                onDone = { action() },
                onNext = { action() },
                onSearch = { action() },
                onGo = { action() },
                onSend = { action() },
            )
        } ?: KeyboardActions.Default,
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.cursor),
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(NumberInputDefaults.LabelSpacing)) {
                if (showLabel) {
                    Text(
                        text = label,
                        style = NumberInputDefaults.labelStyle(AppTheme.typography),
                        color = colors.label,
                    )
                }
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = NumberInputDefaults.MinHeight)
                        .clip(NumberInputDefaults.Shape)
                        .background(colors.container)
                        .border(
                            NumberInputDefaults.borderWidth(focused, readOnly, hasError),
                            colors.border,
                            NumberInputDefaults.Shape,
                        )
                        .padding(
                            start = NumberInputDefaults.HorizontalPadding,
                            end = NumberInputDefaults.HorizontalPadding +
                                if (unit != null) NumberInputDefaults.UnitReserve else 0.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isEmpty() && placeholder != null) NumberInputPlaceholder(placeholder, textStyle, colors)
                    innerTextField()
                    if (unit != null) {
                        Text(
                            text = unit,
                            style = AppTheme.typography.bodySmall,
                            color = colors.label,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.error,
                        modifier = Modifier
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                error(errorText)
                            }
                            .let { if (errorTestTag != null) it.testTag(errorTestTag) else it },
                    )
                }
            }
        },
    )
}

/**
 * Ghost text: never spoken as if it were a typed value, and lighter in weight than typed text so planned values do
 * not rely on colour alone (the field announces [placeholderDescription] as its state).
 */
@Composable
private fun NumberInputPlaceholder(text: String, style: TextStyle, colors: NumberInputColors) {
    Text(
        text = text,
        style = style.copy(fontWeight = NumberInputDefaults.PlaceholderWeight),
        color = colors.placeholder,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

@Preview
@Composable
private fun NumberInputPreview() {
    AppTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NumberInput("", {}, "Peso (kg)", Modifier.width(120.dp), placeholder = "85", maxDecimals = 2)
            NumberInput("8", {}, "Repeticiones", Modifier.width(120.dp))
            NumberInput("60", {}, "Kilos", Modifier.width(80.dp), showLabel = false, readOnly = true)
        }
    }
}
