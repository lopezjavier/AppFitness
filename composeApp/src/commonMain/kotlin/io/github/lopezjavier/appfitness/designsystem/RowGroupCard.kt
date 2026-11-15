@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Sizes of [RowGroupCard]. */
object RowGroupCardDefaults {
    /** The hairline between two rows is 1 dp `borderSubtle`, inset by the card's text margin. */
    val HairlineInset = Spacing.lg
}

/**
 * One [AppCard] that groups the rows of a list (R-95, C-112, C-120): the rows are drawn edge to edge (they keep their
 * own padding) and sit apart by a 1 dp `borderSubtle` hairline inset 16 dp at the start. Rows are given as [rows], one
 * composable each, so the hairline is only drawn between them. An optional [title] (`titleMedium`, a heading) opens
 * the card in a 48 dp header row with the card's 16 dp text margin, and no hairline under it.
 */
@Composable
fun RowGroupCard(rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier, title: String? = null) {
    AppCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        Column {
            if (title != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouchTarget)
                        .padding(horizontal = RowGroupCardDefaults.HairlineInset),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = RowGroupCardDefaults.HairlineInset)
                            .height(1.dp)
                            .background(AppTheme.colors.borderSubtle),
                    )
                }
                row()
            }
        }
    }
}
