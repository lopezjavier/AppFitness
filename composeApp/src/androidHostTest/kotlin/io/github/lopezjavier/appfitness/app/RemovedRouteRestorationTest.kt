package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40: a back stack saved by a build that still had a destination this build removed (the AI usage screen) is
 * dropped, not restored (restoring an unknown destination makes Navigation throw), and the app starts at its start
 * tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class RemovedRouteRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private var registry by mutableStateOf(SaveableStateRegistry(restoredValues = null, canBeSaved = { true }))
    private var shown by mutableStateOf(true)
    private var version by mutableStateOf(NAV_STATE_VERSION)

    /** Opens a full-screen destination from the Trainer tab, saves the state, leaves and comes back. */
    private fun openFreeTextThenRecreateWith(newVersion: Int) {
        rule.setContent {
            if (shown) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    App(
                        navStateVersion = version,
                        trainer = { modifier, actions ->
                            Box(
                                modifier.fillMaxSize().testTag("trainer_screen")
                                    .clickable(onClick = actions.onOpenFreeText),
                            )
                        },
                    )
                }
            }
        }
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.onNodeWithTag("trainer_screen").performClick()
        rule.onNodeWithTag("free_text_screen").assertExists()
        val saved = registry.performSave()
        rule.runOnIdle { shown = false }
        rule.waitForIdle()

        registry = SaveableStateRegistry(restoredValues = saved, canBeSaved = { true })
        version = newVersion
        rule.runOnIdle { shown = true }
        rule.waitForIdle()
    }

    @Test
    fun savedStackFromTheSameVersion_isRestored() {
        openFreeTextThenRecreateWith(NAV_STATE_VERSION)

        rule.onNodeWithTag("free_text_screen").assertExists()
    }

    @Test
    fun savedStackFromAnOlderVersion_isDropped_andTheAppStartsAtTheStartTab() {
        openFreeTextThenRecreateWith(NAV_STATE_VERSION + 1)

        rule.onNodeWithTag("free_text_screen").assertDoesNotExist()
        rule.onNodeWithTag("nav_tab_diary").assertExists()
    }
}
