package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of a [MeasurementRow] that has a [label] (two lines, C-165). */
object MeasurementRowDefaults {
    val LabelledHeight: Dp = 72.dp
}

/**
 * A row of one measurement (R-16): the [value] ("84,5 cm") over its [supporting] line (the date), an optional
 * [leading] icon, built on [AppListRow] (at least 48 dp) with a hairline underneath. Read as one node. With
 * [onClick] the row is one `Role.Button` and [clickLabel] is the spoken action ("Editar medida"); without it the
 * row is plain content. Swipe-to-delete is added by the caller.
 *
 * With a [label] (C-165, R-114 sweep) the row has two lines of 72 dp: the label as headline, [supporting] under it and
 * the [value] trailing in `titleMedium` W600.
 */
@Composable
fun MeasurementRow(
    value: String,
    supporting: String,
    onClick: (() -> Unit)?,
    clickLabel: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    label: String? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(modifier.fillMaxWidth().then(clickable).semantics(mergeDescendants = true) {}) {
        if (label == null) {
            AppListRow(headline = value, supporting = supporting, leading = leading)
        } else {
            AppListRow(
                headline = label,
                supporting = supporting,
                leading = leading,
                modifier = Modifier.heightIn(min = MeasurementRowDefaults.LabelledHeight),
                trailing = {
                    Text(
                        text = value,
                        style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                        color = AppTheme.colors.textPrimary,
                    )
                },
            )
        }
        Box(Modifier.fillMaxWidth().height(AppBorder.thin).background(AppTheme.colors.borderSubtle))
    }
}
