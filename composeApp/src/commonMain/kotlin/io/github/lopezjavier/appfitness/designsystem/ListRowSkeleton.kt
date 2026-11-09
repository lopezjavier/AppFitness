package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

private const val DEFAULT_ROWS = 3

/**
 * Row-shaped loading placeholders (R-31): [rows] boxes as high as a list row ([Spacing.minTouchTarget]) with
 * [AppShapes.sm] corners. The caller owns the screen padding through [modifier]; [testTag] tags the column.
 */
@Composable
fun ListRowSkeleton(modifier: Modifier = Modifier, rows: Int = DEFAULT_ROWS, testTag: String? = null) {
    Column(
        modifier = if (testTag == null) modifier else modifier.testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(rows) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.sm)
        }
    }
}
