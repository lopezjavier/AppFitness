package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.set_row_field_hint_cd
import org.jetbrains.compose.resources.stringResource

/** One numeric input of a set row. A [SetFieldSpec.hint] is the ghost text of an empty weight field, and is spoken. */
@Composable
internal fun SetFieldInput(
    spec: SetFieldSpec,
    values: SetRowValues,
    number: String,
    onChange: (String) -> Unit,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
) {
    val kind = spec.kind
    val base = stringResource(checkNotNull(kind.rowDescription), number)
    val label = spec.hint?.let { stringResource(Res.string.set_row_field_hint_cd, base, it) } ?: base
    val placeholder = when (kind) {
        SetFieldKind.Weight, SetFieldKind.AddedWeight, SetFieldKind.AssistWeight ->
            values.weightPlaceholder ?: spec.hint

        SetFieldKind.Reps -> values.repsPlaceholder

        else -> null
    }
    NumberInput(
        value = values.textOf(kind),
        onValueChange = onChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        maxDecimals = kind.maxDecimals,
        maxIntegerDigits = kind.maxIntegerDigits,
        showLabel = false,
        readOnly = values.completed,
        imeAction = imeAction,
    )
}
