package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.set_checkbox_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_done
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_index
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_index_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_previous
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_reps
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_reps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_weight
import io.github.lopezjavier.appfitness.composeapp.resources.set_header_weight_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_reps_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_use_previous_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_weight_cd
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
)

object SetRowDefaults {
    const val NO_PREVIOUS = "—"
    const val WEIGHT_MAX_DECIMALS = 2
    const val WEIGHT_MAX_INTEGER_DIGITS = 4
    const val REPS_MAX_DECIMALS = 0
    const val REPS_MAX_INTEGER_DIGITS = 3
    const val PREVIOUS_MAX_LINES = 2
    val IndexBadgeSize: Dp = 24.dp
    val MinHeight: Dp = 48.dp

    /** Compact gap: at 360 dp the three weighted columns need every dp (UX: "102,25" at 130 % font). */
    val ColumnGap: Dp = Spacing.xs
    val RowGap: Dp = Spacing.sm

    // Weighted columns (sum 8): previous / KG / reps.
    const val PREVIOUS_WEIGHT = 2.5f
    const val KG_WEIGHT = 3.5f
    const val REPS_WEIGHT = 2f

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
 * `_weight`, `_reps`, `_check` and `_type` (the badge). [onTypeClick] (optional) makes the badge a 48 dp button
 * described by [typeClickLabel]. Pair it with a [SetTableHeader] above, which names the columns.
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
) {
    val number = AppNumberFormat.integer(values.index)
    val previousText = SetRowDefaults.previousText(values.previous)
    val clickable = SetRowDefaults.isPreviousClickable(onUsePrevious != null, values.previous, values.completed)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = SetRowDefaults.MinHeight),
        horizontalArrangement = Arrangement.spacedBy(SetRowDefaults.ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetTypeBadge(
            type = values.type,
            number = number,
            modifier = Modifier.tag(testTagPrefix, "type"),
            onClick = onTypeClick,
            clickLabel = typeClickLabel,
        )
        PreviousCell(
            text = previousText,
            contentDescription = stringResource(Res.string.set_row_use_previous_cd, previousText),
            onClick = onUsePrevious.takeIf { clickable },
            modifier = Modifier.weight(SetRowDefaults.PREVIOUS_WEIGHT).tag(testTagPrefix, "previous"),
        )
        NumberInput(
            value = values.weight,
            onValueChange = onWeightChange,
            label = stringResource(Res.string.set_row_weight_cd, number),
            modifier = Modifier.weight(SetRowDefaults.KG_WEIGHT).tag(testTagPrefix, "weight"),
            placeholder = values.weightPlaceholder,
            maxDecimals = SetRowDefaults.WEIGHT_MAX_DECIMALS,
            maxIntegerDigits = SetRowDefaults.WEIGHT_MAX_INTEGER_DIGITS,
            showLabel = false,
            readOnly = values.completed,
            imeAction = ImeAction.Next,
        )
        NumberInput(
            value = values.reps,
            onValueChange = onRepsChange,
            label = stringResource(Res.string.set_row_reps_cd, number),
            modifier = Modifier.weight(SetRowDefaults.REPS_WEIGHT).tag(testTagPrefix, "reps"),
            placeholder = values.repsPlaceholder,
            maxDecimals = SetRowDefaults.REPS_MAX_DECIMALS,
            maxIntegerDigits = SetRowDefaults.REPS_MAX_INTEGER_DIGITS,
            showLabel = false,
            readOnly = values.completed,
            imeAction = ImeAction.Done,
        )
        SetCompletionCheckbox(
            checked = values.completed,
            onCheckedChange = onCompletedChange,
            contentDescription = stringResource(Res.string.set_checkbox_cd, number),
            modifier = Modifier.tag(testTagPrefix, "check"),
        )
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
) {
    val label: @Composable (Modifier) -> Unit = { textModifier ->
        Text(
            text = text,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.ghostData,
            maxLines = SetRowDefaults.PREVIOUS_MAX_LINES,
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

/**
 * Column headers of a set table ("SERIE · ANTERIOR · KG · REPS · LISTO") on the same column widths as
 * [SetRow]. Not interactive: each field speaks its own label.
 */
@Composable
fun SetTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SetRowDefaults.ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderText(
            text = stringResource(Res.string.set_header_index),
            modifier = Modifier.width(SetRowDefaults.IndexBadgeSize),
            spoken = stringResource(Res.string.set_header_index_cd),
            centered = true,
        )
        HeaderText(stringResource(Res.string.set_header_previous), Modifier.weight(SetRowDefaults.PREVIOUS_WEIGHT))
        HeaderText(
            text = stringResource(Res.string.set_header_weight),
            modifier = Modifier.weight(SetRowDefaults.KG_WEIGHT),
            spoken = stringResource(Res.string.set_header_weight_cd),
            centered = true,
        )
        HeaderText(
            text = stringResource(Res.string.set_header_reps),
            modifier = Modifier.weight(SetRowDefaults.REPS_WEIGHT),
            spoken = stringResource(Res.string.set_header_reps_cd),
            centered = true,
        )
        HeaderText(
            text = stringResource(Res.string.set_header_done),
            modifier = Modifier.width(SetCompletionCheckboxDefaults.TouchTarget),
            centered = true,
        )
    }
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
