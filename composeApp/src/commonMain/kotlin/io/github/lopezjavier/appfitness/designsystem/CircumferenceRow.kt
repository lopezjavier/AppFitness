package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A compact body-circumference row: the [value] ("84,5 cm", tabular) and when it was taken ([whenText]) under a
 * hairline divider. The site is the label of the group the row sits in. Tapping calls [onClick] (edit); [clickLabel] is
 * the spoken action. Swipe-to-delete is added by the caller.
 */
@Composable
fun CircumferenceRow(
    value: String,
    whenText: String,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = value, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textPrimary)
            Text(text = whenText, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        Box(Modifier.fillMaxWidth().height(AppBorder.thin).background(AppTheme.colors.borderSubtle))
    }
}
