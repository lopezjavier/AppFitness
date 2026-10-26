package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics

/**
 * A row of one measurement (R-16): the [value] ("84,5 cm") over its [supporting] line (the date), an optional
 * [leading] icon, built on [AppListRow] (at least 48 dp) with a hairline underneath. Read as one node. With
 * [onClick] the row is one `Role.Button` and [clickLabel] is the spoken action ("Editar medida"); without it the
 * row is plain content. Swipe-to-delete is added by the caller.
 */
@Composable
fun MeasurementRow(
    value: String,
    supporting: String,
    onClick: (() -> Unit)?,
    clickLabel: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Column(modifier.fillMaxWidth().then(clickable).semantics(mergeDescendants = true) {}) {
        AppListRow(headline = value, supporting = supporting, leading = leading)
        Box(Modifier.fillMaxWidth().height(AppBorder.thin).background(AppTheme.colors.borderSubtle))
    }
}
