package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.amount_photo_confidence
import io.github.lopezjavier.appfitness.composeapp.resources.amount_photo_confidence_cd
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val CONFIDENCE_TAG = "amount_photo_confidence"

/**
 * "Confianza 7/10" under the amount once a photo estimate filled it (ADR-72, R-41). The text is always `textPrimary`
 * (the ramp's red fails 4.5:1 as text); the dot in the [AppTheme] confidence scale is only a reinforcement. The
 * whole line is one TalkBack node ("Confianza 7 de 10").
 */
@Composable
fun EstimateConfidenceLine(confidence: Int, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.amount_photo_confidence_cd, confidence)
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = description
            testTag = CONFIDENCE_TAG
        },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Spacing.md).background(AppTheme.colors.confidenceScale(confidence), CircleShape)
                .testTag("${CONFIDENCE_TAG}_dot"),
        )
        Text(
            text = stringResource(Res.string.amount_photo_confidence, confidence),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
        )
    }
}
