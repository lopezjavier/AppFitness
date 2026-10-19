package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Window width buckets of the adaptive shell. [Wide] covers the M3 Medium and Expanded classes. */
enum class WindowWidthClass { Compact, Wide }

object WindowWidthClassDefaults {
    /** First width (inclusive) that is [WindowWidthClass.Wide]; the M3 Medium breakpoint. */
    val WideMinWidth: Dp = 600.dp

    /** Cap of single-column action blocks (sign-in buttons, dialogs) on wide windows. */
    val NarrowContentMaxWidth: Dp = 360.dp

    /** Cap of a multi-step form column (onboarding) on wide windows. */
    val FormContentMaxWidth: Dp = 480.dp

    /** Cap of a single column of cards (the live workout) on wide windows. */
    val CardColumnMaxWidth: Dp = 640.dp

    /** Cap of a two-column settings screen (Profile) on wide windows. */
    val TwoColumnContentMaxWidth: Dp = 960.dp
}

/** Single source of the 600 dp breakpoint. */
fun windowWidthClassFor(width: Dp): WindowWidthClass =
    if (width >= WindowWidthClassDefaults.WideMinWidth) WindowWidthClass.Wide else WindowWidthClass.Compact

val WindowWidthClass.navigationType: AppNavigationType
    get() = when (this) {
        WindowWidthClass.Compact -> AppNavigationType.Bar
        WindowWidthClass.Wide -> AppNavigationType.Rail
    }

val WindowWidthClass.typeScale: TypeScale
    get() = when (this) {
        WindowWidthClass.Compact -> TypeScale.Phone
        WindowWidthClass.Wide -> TypeScale.Tablet
    }
