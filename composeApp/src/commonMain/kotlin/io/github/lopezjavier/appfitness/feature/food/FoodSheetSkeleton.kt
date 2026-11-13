package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTextFieldDefaults
import io.github.lopezjavier.appfitness.designsystem.FeatureMetrics
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing

private const val SKELETON_TOTALS = 3
private const val SKELETON_CHIPS = 3
private val SkeletonLabelHeight = Spacing.lg

/**
 * Loading placeholder shaped like the food sheet (R-31): the quantity field, the totals line and the meal chips,
 * so nothing jumps when the content arrives. Tagged `<tag>` on the column.
 */
@Composable
internal fun FoodSheetSkeleton(tag: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SkeletonBox(Modifier.fillMaxWidth().height(AppTextFieldDefaults.MinHeight), AppShapes.base)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(SKELETON_TOTALS) {
                SkeletonBox(Modifier.width(FeatureMetrics.SkeletonChipWidth).height(SkeletonLabelHeight))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBox(Modifier.width(FeatureMetrics.SkeletonChipWidth).height(SkeletonLabelHeight))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(SKELETON_CHIPS) {
                    SkeletonBox(
                        Modifier.width(FeatureMetrics.SkeletonChipWidth).height(Spacing.minTouchTarget),
                        AppShapes.full,
                    )
                }
            }
        }
    }
}
