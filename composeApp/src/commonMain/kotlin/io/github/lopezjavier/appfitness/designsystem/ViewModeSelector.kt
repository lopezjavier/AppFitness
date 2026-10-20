package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.view_mode_detail
import io.github.lopezjavier.appfitness.composeapp.resources.view_mode_list
import io.github.lopezjavier.appfitness.composeapp.resources.view_mode_summary
import org.jetbrains.compose.resources.stringResource

/**
 * Detalle / Lista / Resumen control for a list of logged items (the diary). A thin wrapper over [SegmentedSelector]:
 * [selectedIndex] is 0 = Detalle, 1 = Lista, 2 = Resumen. The segments are tagged `<testTagPrefix>_detail`, `_list`
 * and `_summary`.
 */
@Composable
fun ViewModeSelector(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTagPrefix: String = "view_mode",
) {
    val detail = stringResource(Res.string.view_mode_detail)
    val list = stringResource(Res.string.view_mode_list)
    val summary = stringResource(Res.string.view_mode_summary)
    val options = remember(detail, list, summary, testTagPrefix) {
        listOf(
            AppOption(detail, "${testTagPrefix}_detail"),
            AppOption(list, "${testTagPrefix}_list"),
            AppOption(summary, "${testTagPrefix}_summary"),
        )
    }
    SegmentedSelector(options, selectedIndex, onSelect, modifier, enabled)
}
