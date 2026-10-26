package io.github.lopezjavier.appfitness.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-56 editing never applies silently: discard matrix, offline gating, single close and Confirm on save. */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One scenario per test.
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppEditorScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val haptics = RecordingAppHaptics()
    private var dirty by mutableStateOf(false)
    private var online by mutableStateOf(true)
    private var saving by mutableStateOf(false)
    private var saved by mutableStateOf(false)
    private var closes = 0
    private var saves = 0
    private var destructive = 0

    private fun show(withDestructive: Boolean = false) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppHaptics provides haptics) {
                    AppEditorScreen(
                        title = "Editar",
                        isDirty = dirty,
                        isSaving = saving,
                        isOnline = online,
                        saved = saved,
                        onSave = { saves++ },
                        onClose = { closes++ },
                        destructiveAction =
                        if (withDestructive) EditorDestructiveAction("Eliminar") { destructive++ } else null,
                        testTagPrefix = "ed",
                    ) { Box(androidx.compose.ui.Modifier) }
                }
            }
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun cleanEditor_backLeavesSilentlyWithoutDialog() {
        show()
        tap("ed_back")
        assertEquals(1, closes)
        rule.onNodeWithTag("ed_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun dirtyEditor_back_asksBeforeLeaving() {
        dirty = true
        show()
        tap("ed_back")
        assertEquals(0, closes)
        rule.onNodeWithTag("ed_discard_dialog").assertIsDisplayed()
    }

    @Test
    fun dirtyEditor_systemBack_asksBeforeLeaving() {
        dirty = true
        show()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, closes)
        rule.onNodeWithTag("ed_discard_dialog").assertIsDisplayed()
    }

    @Test
    fun discard_leaves() {
        dirty = true
        show()
        tap("ed_back")
        tap("ed_discard_confirm")
        assertEquals(1, closes)
        rule.onNodeWithTag("ed_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun keepEditing_staysAndHidesTheDialog() {
        dirty = true
        show()
        tap("ed_back")
        tap("ed_keep_editing")
        assertEquals(0, closes)
        rule.onNodeWithTag("ed_discard_dialog").assertDoesNotExist()
        rule.onNodeWithTag("ed_save").assertIsDisplayed()
    }

    @Test
    fun save_isDisabledUntilSomethingChanged() {
        show()
        rule.onNodeWithTag("ed_save").assertIsNotEnabled()
        dirty = true
        rule.waitForIdle()
        rule.onNodeWithTag("ed_save").assertIsEnabled()
    }

    @Test
    fun save_click_callsOnSave() {
        dirty = true
        show()
        tap("ed_save")
        assertEquals(1, saves)
    }

    @Test
    fun saveSuccess_closesOnceWithExactlyOneConfirm() {
        dirty = true
        show()
        saved = true
        rule.waitForIdle()
        saved = false
        rule.waitForIdle()
        saved = true
        rule.waitForIdle()
        assertEquals(1, closes)
        haptics.assertSingle(HapticEvent.Confirm)
    }

    @Test
    fun saving_disablesSave() {
        dirty = true
        saving = true
        show()
        rule.onNodeWithTag("ed_save").assertIsNotEnabled()
    }

    @Test
    fun offline_disablesSaveAndShowsTheHint() {
        dirty = true
        online = false
        show()
        rule.onNodeWithTag("ed_save").assertIsNotEnabled()
        rule.onNodeWithTag("offline_hint").assertIsDisplayed().assertTextEquals("Sin conexión: no se puede guardar")
    }

    @Test
    fun online_showsNoHint() {
        dirty = true
        show()
        rule.onNodeWithTag("offline_hint").assertDoesNotExist()
    }

    @Test
    fun offline_tappingTheDisabledSave_vibratesOneReject() {
        dirty = true
        online = false
        show()
        tap("ed_save")
        assertEquals(0, saves)
        haptics.assertSingle(HapticEvent.Reject)
    }

    @Test
    fun destructiveAction_isShownAndCallable() {
        show(withDestructive = true)
        tap("ed_delete")
        assertEquals(1, destructive)
    }

    @Test
    fun withoutDestructiveAction_hasNoDeleteButton() {
        show()
        rule.onNodeWithTag("ed_delete").assertDoesNotExist()
    }
}
