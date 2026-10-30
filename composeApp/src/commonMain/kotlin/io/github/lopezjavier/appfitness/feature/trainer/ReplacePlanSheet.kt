package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_create
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_replace_message
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_replace_title
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val PREFIX = "trainer_replace_sheet"

/**
 * Notice before replacing the current plan (US-78): an edit-archetype sheet with the pinned "Crear plan" footer (R-09).
 * Closing (X or back) only calls [onDismiss]; the footer calls [onConfirm] and then [onDismiss].
 * T-176 retires this sheet in favour of "Antes de empezar".
 */
@Composable
internal fun ReplacePlanSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppEditSheet(
        title = stringResource(Res.string.trainer_plan_replace_title),
        commit = SheetCommit.Add,
        onCommit = {
            onConfirm()
            onDismiss()
        },
        onDismiss = onDismiss,
        testTagPrefix = PREFIX,
        commitLabel = stringResource(Res.string.trainer_plan_create),
    ) {
        Text(
            text = stringResource(Res.string.trainer_plan_replace_message),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(top = Spacing.sm).testTag("${PREFIX}_message"),
        )
    }
}
