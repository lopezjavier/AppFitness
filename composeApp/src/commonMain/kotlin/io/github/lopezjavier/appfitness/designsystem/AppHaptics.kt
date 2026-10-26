package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** The UI feedback events of R-29 (planning/05-design-system.md section 9.2). */
enum class HapticEvent { Armed, Disarmed, Confirm, Reject, Tick }

/**
 * Semantic UI haptics, one vibration per action (R-29). Confirm: saved, added, deleted, set completed.
 * Reject: an action was refused or failed. Armed/Disarmed: a gesture crosses its threshold.
 * Tick: a stepper or reorder step. Nothing else vibrates; the end-of-rest alert uses the `Haptics` port.
 */
interface AppHaptics {
    fun armed()

    fun disarmed()

    fun confirm()

    fun reject()

    fun tick()
}

/** Silent implementation: the default of [LocalAppHaptics] (previews, tests, platforms without haptics). */
object NoAppHaptics : AppHaptics {
    override fun armed() = Unit

    override fun disarmed() = Unit

    override fun confirm() = Unit

    override fun reject() = Unit

    override fun tick() = Unit
}

/** The haptics of the current platform; the shell provides the real one. */
val LocalAppHaptics = staticCompositionLocalOf<AppHaptics> { NoAppHaptics }

/** Fires [event] on this [AppHaptics]. */
fun AppHaptics.perform(event: HapticEvent) {
    when (event) {
        HapticEvent.Armed -> armed()
        HapticEvent.Disarmed -> disarmed()
        HapticEvent.Confirm -> confirm()
        HapticEvent.Reject -> reject()
        HapticEvent.Tick -> tick()
    }
}
