package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rest_live
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rest_reset
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rest_start
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_rest_static
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_rest_timer
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.RestCountdown
import io.github.lopezjavier.appfitness.designsystem.RestTimerBar
import io.github.lopezjavier.appfitness.designsystem.RestTimerBarDefaults
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val STATIC_REMAINING_SECONDS = 72
private const val STATIC_TOTAL_SECONDS = 120
private const val LIVE_TICK_MILLIS = 200L
private val LIVE_REST = 2.minutes

/** Catalog section of the rest timer bar (T-025): a deterministic demo and a real countdown. */
internal fun LazyListScope.restTimerItems() {
    item { RestTimerSection() }
}

/** A running rest: only the end mark is stored, like the E5 `endsAt` approach; nothing is decremented. */
private class LiveRest(val endMark: TimeMark, val total: Duration)

@Composable
private fun RestTimerSection() {
    CatalogSection(stringResource(Res.string.catalog_section_rest_timer), "catalog_section_rest_timer") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StaticRestDemo()
                LiveRestDemo()
            }
        }
    }
}

@Composable
private fun StaticRestDemo() {
    var remainingSeconds by rememberSaveable { mutableStateOf(STATIC_REMAINING_SECONDS) }
    var totalSeconds by rememberSaveable { mutableStateOf(STATIC_TOTAL_SECONDS) }
    var visible by rememberSaveable { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        VariantCaption(stringResource(Res.string.catalog_rest_static))
        if (visible) {
            RestTimerBar(
                remaining = remainingSeconds.seconds,
                total = totalSeconds.seconds,
                onAddTime = {
                    val extended = RestTimerBarDefaults.extend(
                        RestCountdown(remainingSeconds.seconds, totalSeconds.seconds),
                    )
                    remainingSeconds = extended.remaining.inWholeSeconds.toInt()
                    totalSeconds = extended.total.inWholeSeconds.toInt()
                },
                onSkip = { visible = false },
                modifier = Modifier.testTag("catalog_rest_timer"),
                testTagPrefix = "catalog_rest_timer",
            )
        }
        SecondaryButton(
            text = stringResource(Res.string.catalog_rest_reset),
            onClick = {
                remainingSeconds = STATIC_REMAINING_SECONDS
                totalSeconds = STATIC_TOTAL_SECONDS
                visible = true
            },
            modifier = Modifier.fillMaxWidth().testTag("catalog_rest_timer_reset"),
        )
    }
}

@Composable
private fun LiveRestDemo() {
    var live by remember { mutableStateOf<LiveRest?>(null) }
    var remaining by remember { mutableStateOf(Duration.ZERO) }
    LaunchedEffect(live) {
        val rest = live ?: return@LaunchedEffect
        while (true) {
            remaining = -rest.endMark.elapsedNow()
            if (remaining <= Duration.ZERO) {
                live = null
                break
            }
            delay(LIVE_TICK_MILLIS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        VariantCaption(stringResource(Res.string.catalog_rest_live))
        GhostButton(
            text = stringResource(Res.string.catalog_rest_start),
            onClick = {
                remaining = LIVE_REST
                live = LiveRest(TimeSource.Monotonic.markNow() + LIVE_REST, LIVE_REST)
            },
            modifier = Modifier.fillMaxWidth().testTag("catalog_rest_timer_start"),
        )
        live?.let { rest ->
            RestTimerBar(
                remaining = remaining,
                total = rest.total,
                onAddTime = {
                    val extended = RestTimerBarDefaults.extend(RestCountdown(remaining, rest.total))
                    remaining = extended.remaining
                    live = LiveRest(TimeSource.Monotonic.markNow() + extended.remaining, extended.total)
                },
                onSkip = { live = null },
                modifier = Modifier.testTag("catalog_rest_timer_live"),
                testTagPrefix = "catalog_rest_timer_live",
            )
        }
    }
}
