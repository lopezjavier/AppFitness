package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.set_checkbox_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_previous
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_rpe_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_type
import io.github.lopezjavier.appfitness.composeapp.resources.set_current_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_type_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_previous_line
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_use_previous_cd
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.jetbrains.compose.resources.stringResource

/** Values shown by one [SetRow]; the caller owns them. [previous] is caller-formatted ("85 kg × 8"). */
@Immutable
data class SetRowValues(
    val index: Int,
    val previous: String?,
    val weight: String,
    val reps: String,
    val completed: Boolean,
    val weightPlaceholder: String? = null,
    val repsPlaceholder: String? = null,
    val type: SetType = SetType.Working,
    /** The logged RPE as "7,5"; null shows the empty marker. Only rendered when the row has an RPE handler. */
    val rpe: String? = null,
    /** Kilometres as typed, for a [SetFieldKind.Distance] field. */
    val distance: String = "",
    /** Steps as typed, for a [SetFieldKind.Steps] field. */
    val steps: String = "",
    /** Floors as typed, for a [SetFieldKind.Floors] field. */
    val floors: String = "",
    /** Elapsed time shown by a [SetFieldKind.Duration] field (the stopwatch). */
    val durationSeconds: Int = 0,
    val stopwatchRunning: Boolean = false,
    /** Marks the next set to do: its check circle gets the cyan ring (C-67). */
    val current: Boolean = false,
    /**
     * Non-null makes the duration field the cardio TIEMPO cell (R-58): the planned time as its dim placeholder and a
     * start button; the running time lives in the shared timer bar, not in the row. Null keeps the inline stopwatch.
     */
    val cardioPlaceholder: String? = null,
    val cardioPlaceholderDescription: String = "",
    val cardioStartTag: String? = null,
    val cardioPlaceholderTag: String? = null,
) {
    internal fun textOf(kind: SetFieldKind): String = when (kind) {
        SetFieldKind.Weight, SetFieldKind.AddedWeight, SetFieldKind.AssistWeight -> weight
        SetFieldKind.Reps -> reps
        SetFieldKind.Distance -> distance
        SetFieldKind.Steps -> steps
        SetFieldKind.Floors -> floors
        SetFieldKind.Duration -> ""
    }
}

object SetRowDefaults {
    const val NO_PREVIOUS = "—"
    const val WEIGHT_MAX_DECIMALS = 2
    const val WEIGHT_MAX_INTEGER_DIGITS = 4
    const val REPS_MAX_DECIMALS = 0
    const val REPS_MAX_INTEGER_DIGITS = 3
    const val DISTANCE_MAX_DECIMALS = 2
    const val DISTANCE_MAX_INTEGER_DIGITS = 3
    const val STEPS_MAX_INTEGER_DIGITS = 6
    const val PREVIOUS_MAX_LINES = 2
    val IndexBadgeSize: Dp = 24.dp

    /** Width of the [SetOrdinal] left of the type badge (read-only summaries and editors). */
    val OrdinalWidth: Dp = 16.dp

    /** Column of the type badge, shared by [SetRow] and [SetTableHeader]: 40 x 48 dp, 48 dp tall (V-21). */
    val TypeColumnWidth: Dp = 40.dp

    /** Ordinal + gap + 48 dp type badge of the editors that keep the set number; their headers use it as a spacer. */
    val OrdinalTypeWidth: Dp get() = OrdinalWidth + ColumnGap + SetCompletionCheckboxDefaults.TouchTarget

    /** Fixed width of the LISTO and note cells, shared by rows and headers. */
    val FixedCellWidth: Dp = SetCompletionCheckboxDefaults.TouchTarget

    /** Width of the RPE cell (40 x 48 dp, V-21). */
    val RpeCellWidth: Dp = 40.dp

    /** Minimum width of the KM and TIEMPO fields of a cardio set (V-22). */
    val CardioFieldMinWidth: Dp = 72.dp

    /** Cell of the cardio start circle on the first line of the row (C-92): a 48 dp target. */
    val StartCellWidth: Dp = SetCompletionCheckboxDefaults.TouchTarget

    /**
     * Whether the cardio start circle fits on the first line (C-92): every field keeps [CardioFieldMinWidth] scaled by
     * [fontScale] next to TIPO, START, LISTO and the note cell. Shared by the row and its header so both agree.
     */
    fun cardioStartFits(available: Dp, fieldCount: Int, hasNote: Boolean, fontScale: Float): Boolean {
        val cells = fieldCount + CARDIO_FIXED_CELLS + if (hasNote) 1 else 0
        val fixed = TypeColumnWidth + StartCellWidth + FixedCellWidth + if (hasNote) FixedCellWidth else 0.dp
        return available >= CardioFieldMinWidth * fontScale * fieldCount + fixed + ColumnGap * (cells - 1)
    }

    /** TIPO, START and LISTO: the cells of a cardio first line besides the fields and the note. */
    private const val CARDIO_FIXED_CELLS = 3
    val MinHeight: Dp = 48.dp

    /** Compact gap: at 360 dp the three weighted columns need every dp (UX: "102,25" at 130 % font). */
    val ColumnGap: Dp = Spacing.xs
    val RowGap: Dp = Spacing.sm

    // Weighted columns (sum 8): previous / KG / reps.
    const val PREVIOUS_WEIGHT = 2.5f
    const val KG_WEIGHT = 3.25f
    const val REPS_WEIGHT = 2.25f

    // Weighted columns when the fixed 48 dp RPE cell is present (sum 7.4; REPS keeps a 48 dp touch target, V-2).
    const val PREVIOUS_WEIGHT_RPE = 2.1f
    const val KG_WEIGHT_RPE = 2.8f
    const val REPS_WEIGHT_RPE = 2.5f

    /** Start inset of a row and its header when the RPE cell is present: none since V-21 (the cells are narrower). */
    val RpeInset: Dp = 0.dp
    const val PREVIOUS_MAX_LINES_RPE = 3

    fun rpeInset(withRpe: Boolean): Dp = if (withRpe) RpeInset else 0.dp

    fun previousWeight(withRpe: Boolean): Float = if (withRpe) PREVIOUS_WEIGHT_RPE else PREVIOUS_WEIGHT

    fun kgWeight(withRpe: Boolean): Float = if (withRpe) KG_WEIGHT_RPE else KG_WEIGHT

    fun repsWeight(withRpe: Boolean): Float = if (withRpe) REPS_WEIGHT_RPE else REPS_WEIGHT

    fun previousMaxLines(withRpe: Boolean): Int = if (withRpe) PREVIOUS_MAX_LINES_RPE else PREVIOUS_MAX_LINES

    fun previousText(previous: String?): String = if (previous.isNullOrBlank()) NO_PREVIOUS else previous

    /** "catalog_set_row_2" and "weight" give "catalog_set_row_2_weight"; no prefix gives no tag. */
    fun childTag(prefix: String?, part: String): String? = childTestTag(prefix, part)

    /** Container and content colour of the index badge. */
    fun indexColors(appColors: AppColors): Pair<Color, Color> = appColors.surfaceOverlay to appColors.textSecondary

    /** The previous performance can be tapped to fill the fields only on a pending row that has one. */
    fun isPreviousClickable(hasHandler: Boolean, previous: String?, completed: Boolean): Boolean =
        hasHandler && !previous.isNullOrBlank() && !completed
}

/**
 * One set of a workout table: index, previous performance, KG and reps inputs and the completion
 * checkbox. A completed row makes both inputs read-only. [onUsePrevious] (optional) makes the previous
 * cell tappable on pending rows. When [testTagPrefix] is set the children are tagged `_previous`,
 * `_weight`, `_reps`, `_check`, `_type` (the badge) and `_rpe`. [onTypeClick]
 * (optional) makes the badge a 48 dp button described by [typeClickLabel]. [onRpeClick] (optional) adds a fixed
 * 48 dp RPE cell between reps and the checkbox, spoken as [rpeClickLabel]; it stays tappable on completed rows.
 * Without [showPrevious] the "Antes" column is gone and the inputs take its width (the live row, which has the
 * note button).
 * [trailing] is an optional 48 dp action after the checkbox (the set note, R-49); pass `showNote` to the header too.
 * Pair it with a [SetTableHeader] above, which names the columns.
 */
@Composable
fun SetRow(
    values: SetRowValues,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUsePrevious: (() -> Unit)? = null,
    testTagPrefix: String? = null,
    onTypeClick: (() -> Unit)? = null,
    typeClickLabel: String? = null,
    onRpeClick: (() -> Unit)? = null,
    rpeClickLabel: String? = null,
    fields: List<SetFieldSpec> = SetFieldSpec.WeightReps,
    actions: SetRowFieldActions = SetRowFieldActions(),
    trailing: (@Composable () -> Unit)? = null,
    showPrevious: Boolean = true,
) {
    val withRpe = onRpeClick != null
    val number = AppNumberFormat.integer(values.index)
    val previousText = SetRowDefaults.previousText(values.previous)
    val clickable = SetRowDefaults.isPreviousClickable(onUsePrevious != null, values.previous, values.completed)
    val cardio = values.cardioPlaceholder != null
    val line: @Composable (Modifier, Boolean) -> Unit = { lineModifier, startInline ->
        SetRowLine(
            lineModifier = lineModifier,
            values = values,
            number = number,
            previousText = previousText,
            clickable = clickable,
            withRpe = withRpe,
            showPrevious = showPrevious,
            callbacks = SetRowCallbacks(
                onWeightChange,
                onRepsChange,
                onCompletedChange,
                onUsePrevious,
                onTypeClick,
                typeClickLabel,
                onRpeClick,
                rpeClickLabel,
            ),
            fields = fields,
            actions = actions,
            testTagPrefix = testTagPrefix,
            trailing = trailing,
            startInline = startInline,
        )
    }
    if (cardio) {
        // A cardio row (R-58) shares the strength grid (C-90): the start circle joins line 1 when it fits (C-92),
        // otherwise it moves to line 2 as a pill; line 2 also holds "Antes: ..." when a value exists.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val startInline = SetRowDefaults.cardioStartFits(
                available = maxWidth,
                fieldCount = fields.size,
                hasNote = trailing != null,
                fontScale = LocalDensity.current.fontScale,
            )
            Column(Modifier.fillMaxWidth()) {
                line(Modifier, startInline)
                CardioSecondLine(values, actions.onStopwatchToggle, startInline)
            }
        }
    } else {
        line(modifier, false)
    }
}

/** The change and click handlers of a [SetRow], grouped for [SetRowLine]. */
private class SetRowCallbacks(
    val onWeightChange: (String) -> Unit,
    val onRepsChange: (String) -> Unit,
    val onCompletedChange: (Boolean) -> Unit,
    val onUsePrevious: (() -> Unit)?,
    val onTypeClick: (() -> Unit)?,
    val typeClickLabel: String?,
    val onRpeClick: (() -> Unit)?,
    val rpeClickLabel: String?,
)

/**
 * Second line of a cardio set: the "Iniciar" pill when the start circle did not fit on line 1, and "Antes: 5 km ·
 * 30:00" (only when a value exists). Nothing to show draws nothing.
 */
@Composable
private fun CardioSecondLine(values: SetRowValues, onStart: () -> Unit, startInline: Boolean) {
    val previous = values.previous?.takeUnless { it.isBlank() }
    if (previous == null && startInline) return
    val start = Modifier.padding(start = SetRowDefaults.TypeColumnWidth + SetRowDefaults.ColumnGap)
    val previousText: @Composable (Modifier) -> Unit = { textModifier ->
        if (previous != null) {
            Text(
                text = stringResource(Res.string.set_row_previous_line, previous),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textTertiary,
                maxLines = SetRowDefaults.PREVIOUS_MAX_LINES,
                modifier = textModifier,
            )
        }
    }
    if (startInline) {
        previousText(Modifier.fillMaxWidth().heightIn(min = Spacing.xl - Spacing.xs).then(start))
        return
    }
    Row(
        Modifier.fillMaxWidth().then(start),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardioStart(
            timerInBar = values.stopwatchRunning,
            done = values.completed,
            onStart = onStart,
            tag = values.cardioStartTag,
            pill = true,
        )
        previousText(Modifier.weight(1f))
    }
}

@Composable
@Suppress("LongParameterList") // One row line: values, handlers and its tags.
private fun SetRowLine(
    lineModifier: Modifier,
    values: SetRowValues,
    number: String,
    previousText: String,
    clickable: Boolean,
    withRpe: Boolean,
    showPrevious: Boolean,
    callbacks: SetRowCallbacks,
    fields: List<SetFieldSpec>,
    actions: SetRowFieldActions,
    testTagPrefix: String?,
    trailing: (@Composable () -> Unit)?,
    startInline: Boolean,
) {
    val onWeightChange = callbacks.onWeightChange
    val onRepsChange = callbacks.onRepsChange
    val onCompletedChange = callbacks.onCompletedChange
    val onUsePrevious = callbacks.onUsePrevious
    val onTypeClick = callbacks.onTypeClick
    val typeClickLabel = callbacks.typeClickLabel
    val onRpeClick = callbacks.onRpeClick
    val rpeClickLabel = callbacks.rpeClickLabel
    Row(
        modifier = lineModifier.fillMaxWidth().padding(
            start = SetRowDefaults.rpeInset(withRpe),
        ).heightIn(min = SetRowDefaults.MinHeight),
        horizontalArrangement = Arrangement.spacedBy(SetRowDefaults.ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SetRowDefaults.TypeColumnWidth), contentAlignment = Alignment.Center) {
            SetTypeBadge(
                type = values.type,
                modifier = Modifier.tag(testTagPrefix, "type"),
                onClick = onTypeClick,
                clickLabel = typeClickLabel,
            )
        }
        if (showPrevious) {
            PreviousCell(
                text = previousText,
                contentDescription = stringResource(Res.string.set_row_use_previous_cd, previousText),
                onClick = onUsePrevious.takeIf { clickable },
                maxLines = SetRowDefaults.previousMaxLines(withRpe),
                modifier = Modifier.weight(SetRowDefaults.previousWeight(withRpe)).tag(testTagPrefix, "previous"),
            )
        }
        SetFieldCells(
            fields = fields,
            values = values,
            withRpe = withRpe,
            handlers = SetFieldHandlers(onWeightChange, onRepsChange, actions),
            testTagPrefix = testTagPrefix,
        )
        if (startInline) {
            CardioStart(
                timerInBar = values.stopwatchRunning,
                done = values.completed,
                onStart = actions.onStopwatchToggle,
                tag = values.cardioStartTag,
                pill = false,
            )
        }
        if (onRpeClick != null) {
            RpeCell(
                value = values.rpe,
                description = rpeClickLabel,
                onClick = onRpeClick,
                modifier = Modifier.tag(testTagPrefix, "rpe"),
            )
        }
        SetCompletionCheckbox(
            checked = values.completed,
            onCheckedChange = onCompletedChange,
            contentDescription = stringResource(Res.string.set_checkbox_cd, number),
            modifier = Modifier.tag(testTagPrefix, "check"),
            currentStateDescription = if (values.current) stringResource(Res.string.set_current_cd) else null,
        )
        trailing?.invoke()
    }
}

/** The change handlers [SetFieldCells] routes each field's edits to. */
private class SetFieldHandlers(
    val onWeightChange: (String) -> Unit,
    val onRepsChange: (String) -> Unit,
    val actions: SetRowFieldActions,
)

@Composable
private fun RowScope.SetFieldCells(
    fields: List<SetFieldSpec>,
    values: SetRowValues,
    withRpe: Boolean,
    handlers: SetFieldHandlers,
    testTagPrefix: String?,
) {
    val number = AppNumberFormat.integer(values.index)
    val weights = SetRowFieldLayout.weights(fields, withRpe, cardioTime = values.cardioPlaceholder != null)
    fields.forEachIndexed { position, spec ->
        val cardioRow = values.cardioPlaceholder != null
        val cellModifier = Modifier.weight(weights[position]).tag(testTagPrefix, spec.kind.tagPart).let {
            when {
                cardioRow -> it.widthIn(min = SetRowDefaults.CardioFieldMinWidth)
                spec.kind == SetFieldKind.Reps -> it.widthIn(min = SetCompletionCheckboxDefaults.TouchTarget)
                else -> it
            }
        }
        if (spec.kind == SetFieldKind.Duration && values.cardioPlaceholder != null) {
            CardioTimeCell(
                elapsedSeconds = values.durationSeconds,
                placeholder = values.cardioPlaceholder,
                placeholderDescription = values.cardioPlaceholderDescription,
                timerInBar = values.stopwatchRunning,
                readOnly = values.completed,
                onManualEntry = handlers.actions.onDurationEdit,
                modifier = Modifier.weight(weights[position]).widthIn(min = SetRowDefaults.CardioFieldMinWidth),
                testTagPrefix = SetRowDefaults.childTag(testTagPrefix, "stopwatch"),
                placeholderTestTag = values.cardioPlaceholderTag,
            )
        } else if (spec.kind == SetFieldKind.Duration) {
            StopwatchField(
                elapsedSeconds = values.durationSeconds,
                running = values.stopwatchRunning,
                onToggle = handlers.actions.onStopwatchToggle,
                onManualEntry = handlers.actions.onDurationEdit,
                modifier = Modifier.weight(weights[position]),
                readOnly = values.completed,
                testTagPrefix = SetRowDefaults.childTag(testTagPrefix, "stopwatch"),
            )
        } else {
            SetFieldInput(
                spec = spec,
                values = values,
                number = number,
                onChange = when (spec.kind) {
                    SetFieldKind.Weight, SetFieldKind.AddedWeight, SetFieldKind.AssistWeight -> handlers.onWeightChange
                    SetFieldKind.Reps -> handlers.onRepsChange
                    else -> { text -> handlers.actions.onFieldChange(spec.kind, text) }
                },
                imeAction = if (position == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                modifier = cellModifier,
            )
        }
    }
}

private fun Modifier.tag(prefix: String?, part: String): Modifier =
    SetRowDefaults.childTag(prefix, part)?.let { testTag(it) } ?: this

@Composable
private fun PreviousCell(
    text: String,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    maxLines: Int = SetRowDefaults.PREVIOUS_MAX_LINES,
) {
    val label: @Composable (Modifier) -> Unit = { textModifier ->
        Text(
            text = text,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textTertiary,
            maxLines = maxLines,
            modifier = textModifier,
        )
    }
    if (onClick == null) {
        label(modifier)
    } else {
        Box(
            modifier =
            modifier
                .heightIn(min = SetRowDefaults.MinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.CenterStart,
        ) {
            label(Modifier)
        }
    }
}

/** Fixed 48 dp button; the RPE sits in the [SetTypeBadge] container (C-71): value in the accent W700, or "—". */
@Composable
private fun RpeCell(value: String?, description: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(SetRowDefaults.RpeCellWidth, SetCompletionCheckboxDefaults.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .defaultMinSize(SetRowDefaults.IndexBadgeSize, SetRowDefaults.IndexBadgeSize)
                .clip(AppShapes.sm)
                .background(AppTheme.colors.surfaceOverlay)
                .padding(horizontal = Spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value ?: SetRowDefaults.NO_PREVIOUS,
                style = AppTheme.typography.labelTechnical.copy(fontWeight = FontWeight.W700),
                color = if (value == null) AppTheme.colors.textSecondary else AppTheme.colors.athleticCyan,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Column headers of a set table ("TIPO · ANTES · KG · REPS · LISTO") on the same column widths as
 * [SetRow]. Not interactive: each field speaks its own label.
 */
@Composable
fun SetTableHeader(
    modifier: Modifier = Modifier,
    showRpe: Boolean = false,
    fields: List<SetFieldSpec> = SetFieldSpec.WeightReps,
    showNote: Boolean = false,
    showPrevious: Boolean = true,
    testTagPrefix: String? = null,
    cardioTime: Boolean = false,
) {
    if (!cardioTime) {
        SetTableHeaderRow(modifier, showRpe, fields, showNote, showPrevious, testTagPrefix, false, startSpacer = false)
        return
    }
    // A cardio header keeps a START spacer exactly when its rows keep the start circle on line 1 (C-90, C-92).
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val startInline = SetRowDefaults.cardioStartFits(maxWidth, fields.size, showNote, LocalDensity.current.fontScale)
        SetTableHeaderRow(Modifier, showRpe, fields, showNote, showPrevious, testTagPrefix, true, startInline)
    }
}

@Composable
@Suppress("LongParameterList") // The header's flags, forwarded from [SetTableHeader].
private fun SetTableHeaderRow(
    modifier: Modifier,
    showRpe: Boolean,
    fields: List<SetFieldSpec>,
    showNote: Boolean,
    showPrevious: Boolean,
    testTagPrefix: String?,
    cardioTime: Boolean,
    startSpacer: Boolean,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = SetRowDefaults.rpeInset(showRpe)),
        horizontalArrangement = Arrangement.spacedBy(SetRowDefaults.ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderText(
            text = stringResource(Res.string.set_header_type),
            modifier = Modifier.width(SetRowDefaults.TypeColumnWidth).headerTag(testTagPrefix, "type", cardioTime),
            spoken = stringResource(Res.string.set_header_type_cd),
            centered = true,
        )
        if (showPrevious) {
            HeaderText(
                stringResource(Res.string.set_header_previous),
                Modifier.weight(SetRowDefaults.previousWeight(showRpe)).tag(testTagPrefix, "previous"),
            )
        }
        val weights = SetRowFieldLayout.weights(fields, showRpe, cardioTime)
        fields.forEachIndexed { position, spec ->
            HeaderText(
                text = stringResource(spec.kind.header),
                modifier = Modifier.weight(weights[position]).headerTag(testTagPrefix, spec.kind.tagPart, cardioTime)
                    .let { if (cardioTime) it.widthIn(min = SetRowDefaults.CardioFieldMinWidth) else it },
                spoken = stringResource(spec.kind.headerDescription),
                centered = true,
            )
        }
        if (startSpacer) Spacer(Modifier.width(SetRowDefaults.StartCellWidth))
        if (showRpe) {
            HeaderText(
                text = stringResource(Res.string.set_header_rpe),
                modifier = Modifier.width(SetRowDefaults.RpeCellWidth).tag(testTagPrefix, "rpe"),
                spoken = stringResource(Res.string.set_header_rpe_cd),
                centered = true,
            )
        }
        HeaderText(
            text = stringResource(Res.string.action_done),
            modifier = Modifier.width(SetRowDefaults.FixedCellWidth).headerTag(testTagPrefix, "check", cardioTime),
            centered = true,
        )
        if (showNote) Spacer(Modifier.width(SetRowDefaults.FixedCellWidth))
    }
}

/** Header tags: a cardio table names its columns (`session_cardio_header_tipo|km|tiempo|listo`) for layout tests. */
private fun Modifier.headerTag(prefix: String?, part: String, cardio: Boolean): Modifier {
    if (!cardio) return tag(prefix, part)
    val name = when (part) {
        "type" -> "tipo"
        "distance" -> "km"
        "duration" -> "tiempo"
        "check" -> "listo"
        else -> part
    }
    return testTag("session_cardio_header_$name")
}

@Composable
private fun HeaderText(text: String, modifier: Modifier = Modifier, spoken: String? = null, centered: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = AppTheme.typography.labelTechnical,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier.then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier),
    )
}

@Preview
@Composable
private fun SetRowPreview() {
    AppTheme {
        AppCard(Modifier.padding(Spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(SetRowDefaults.RowGap)) {
                SetTableHeader()
                SetRow(SetRowValues(1, "80 kg × 10", "60", "12", completed = true), {}, {}, {})
                SetRow(
                    SetRowValues(
                        2,
                        "85 kg × 8",
                        "",
                        "",
                        completed = false,
                        weightPlaceholder = "85",
                        repsPlaceholder = "8",
                    ),
                    {},
                    {},
                    {},
                    onUsePrevious = {},
                )
                SetRow(SetRowValues(3, null, "102,25", "", completed = false), {}, {}, {})
            }
        }
    }
}
