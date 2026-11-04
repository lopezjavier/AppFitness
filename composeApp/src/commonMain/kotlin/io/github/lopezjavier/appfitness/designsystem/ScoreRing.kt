package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.score_ring_of
import org.jetbrains.compose.resources.stringResource

private const val PERCENT_MAX = 100f

/**
 * Score ring 0 to [max] (176 dp, same geometry as [ProgressRing]): the number with one decimal and "de N" in the
 * centre, and a pill with [bandLabel] below, tinted with [AppColors.scoreScale]. The colour only reinforces the number
 * and the label. The whole ring is one accessibility node (progress bar, [description] and [stateText]) carrying
 * the caller's [modifier]; [valueTag] and [bandTag] tag the inner texts for the unmerged tree.
 */
@Composable
fun ScoreRing(
    score: Double,
    bandLabel: String,
    description: String,
    stateText: String,
    modifier: Modifier = Modifier,
    max: Int = SCORE_RING_MAX,
    valueTag: String? = null,
    bandTag: String? = null,
) {
    val fraction = (score / max).toFloat().coerceIn(0f, 1f)
    val sweep by animateFloatAsState(
        targetValue = fraction * ProgressRingDefaults.FULL_SWEEP,
        animationSpec = tween(ProgressRingDefaults.ANIMATION_MILLIS),
        label = "score_ring_sweep",
    )
    val tint = AppTheme.colors.scoreScale(score * SCORE_RING_MAX / max)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            stateDescription = stateText
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(ProgressRingDefaults.Size), contentAlignment = Alignment.Center) {
            RingArc(
                sweep = sweep,
                track = AppTheme.colors.surfaceOverlay,
                progress = tint,
                size = ProgressRingDefaults.Size,
                strokeWidth = ProgressRingDefaults.StrokeWidth,
            )
            Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = AppNumberFormat.oneDecimal(score),
                    style = ProgressRingDefaults.valueStyle(AppTheme.typography),
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = valueTag?.let { Modifier.testTag(it) } ?: Modifier,
                )
                Text(
                    text = stringResource(Res.string.score_ring_of, max.toString()),
                    style = ProgressRingDefaults.captionStyle(AppTheme.typography),
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        PillContainer(modifier = Modifier.clearAndSetSemantics {}) {
            Text(
                text = bandLabel,
                style = ProgressRingDefaults.statusStyle(AppTheme.typography),
                color = tint,
                textAlign = TextAlign.Center,
                modifier = bandTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/**
 * Compact ring for a similarity percentage 0 to 100 (40 dp, for a list row's trailing slot): the whole number in the
 * centre, no "%" sign (the semantics say [description]). One neutral [color] for every row (similarity is not good or
 * bad); no animation, so a re-sort does not flicker. One accessibility node carrying the caller's [modifier].
 */
@Composable
fun PercentRing(percent: Int, description: String, modifier: Modifier = Modifier, color: Color? = null) {
    val fraction = (percent / PERCENT_MAX).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(ProgressRingDefaults.CompactSize)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        RingArc(
            sweep = fraction * ProgressRingDefaults.FULL_SWEEP,
            track = AppTheme.colors.surfaceOverlay,
            progress = color ?: AppTheme.colors.athleticCyan,
            size = ProgressRingDefaults.CompactSize,
            strokeWidth = ProgressRingDefaults.CompactStroke,
        )
        Box(Modifier.clearAndSetSemantics {}) {
            Text(
                text = percent.toString(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

const val SCORE_RING_MAX = 10

@Preview
@Composable
private fun ScoreRingPreview() {
    AppTheme {
        Column(
            Modifier.background(AppTheme.colors.surfaceRaised).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ScoreRing(7.5, "Aceptable", "Puntuación de la carpeta", "7,5 de 10, aceptable")
            PercentRing(87, "87 por ciento de similitud")
        }
    }
}
