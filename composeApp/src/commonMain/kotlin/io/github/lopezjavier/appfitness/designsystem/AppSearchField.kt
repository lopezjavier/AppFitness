package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_clear_search
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_search
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Search field (R-26): an [AppTextField] with a decorative search icon, a "Borrar búsqueda" clear button
 * (`<testTagPrefix>_clear` unless [clearTestTag] is given; only while the text is not empty and the
 * field is [enabled]) and the keyboard action "Buscar", which hides the keyboard and calls [onSearch]
 * (screens that search live as the query changes pass an empty lambda). The field itself is tagged [testTagPrefix].
 */
@Suppress("LongParameterList") // Field, search and tag contract of R-26.
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    onSearch: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearTestTag: String = "${testTagPrefix}_clear",
) {
    val keyboard = LocalSoftwareKeyboardController.current
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        testTagPrefix = testTagPrefix,
        leading = {
            Icon(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(AppTopBarDefaults.NavigationIconSize),
            )
        },
        trailing = if (value.isNotEmpty() && enabled) {
            {
                AppIconButton(
                    contentDescription = stringResource(Res.string.action_clear_search),
                    icon = Res.drawable.ic_close,
                    testTag = clearTestTag,
                    onClick = { onValueChange("") },
                    tint = AppTheme.colors.textSecondary,
                )
            }
        } else {
            null
        },
        imeAction = ImeAction.Search,
        onImeAction = {
            keyboard?.hide()
            onSearch()
        },
        capitalization = KeyboardCapitalization.None,
    )
}
