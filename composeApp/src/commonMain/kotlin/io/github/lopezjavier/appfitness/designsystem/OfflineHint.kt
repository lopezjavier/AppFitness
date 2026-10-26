package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.offline_hint_template
import org.jetbrains.compose.resources.stringResource

private const val OFFLINE_HINT_PREFIX = "Sin conexión: no se puede "

/**
 * Formats the standard hint under a write action disabled offline (R-37), for example
 * `offlineHint("guardar")` gives "Sin conexión: no se puede guardar". [action] is an infinitive in lower case.
 * Mirrors the `offline_hint_template` string; the composable uses the resource, this keeps the format testable.
 */
fun offlineHint(action: String): String = OFFLINE_HINT_PREFIX + action

/** Tag of [OfflineHint]. */
const val OFFLINE_HINT_TAG = "offline_hint"

/**
 * The hint under a write action disabled offline (R-37): "Sin conexión: no se puede <[action]>" in `bodySmall`
 * (12 sp) `textSecondary`, so it reads AA and never below 11 sp. [action] is an infinitive in lower case.
 */
@Composable
fun OfflineHint(action: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.offline_hint_template, action),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = modifier.testTag(OFFLINE_HINT_TAG),
    )
}
