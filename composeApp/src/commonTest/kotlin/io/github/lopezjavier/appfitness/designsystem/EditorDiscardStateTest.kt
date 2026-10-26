package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EditorDiscardStateTest {
    private val state = EditorDiscardState()

    @Test
    fun cleanEditor_backLeavesSilently() {
        state.isDirty = false

        assertEquals(BackResult.Leave, state.onBack())
        assertFalse(state.showDiscardDialog)
    }

    @Test
    fun dirtyEditor_backAsksAndShowsTheDialog() {
        state.isDirty = true

        assertEquals(BackResult.AskDiscard, state.onBack())
        assertTrue(state.showDiscardDialog)
    }

    @Test
    fun descartar_leavesAndHidesTheDialog() {
        state.isDirty = true
        state.onBack()

        assertEquals(BackResult.Leave, state.confirmDiscard())
        assertFalse(state.showDiscardDialog)
    }

    @Test
    fun seguirEditando_staysAndHidesTheDialog() {
        state.isDirty = true
        state.onBack()

        state.keepEditing()

        assertFalse(state.showDiscardDialog)
        assertEquals(BackResult.AskDiscard, state.onBack())
    }

    @Test
    fun dirtyBecomesFalseAfterARevert_backLeavesAgain() {
        state.isDirty = true
        state.isDirty = false

        assertEquals(BackResult.Leave, state.onBack())
    }

    @Test
    fun saveSuccess_closesOnlyOnce() {
        assertTrue(state.onSaveSuccess())
        assertFalse(state.onSaveSuccess())
    }

    @Test
    fun canSave_requiresChanges() {
        state.isDirty = false
        assertFalse(state.canSave())
        state.isDirty = true
        assertTrue(state.canSave())
    }

    @Test
    fun canSave_callerFlagEnablesACleanEditor() {
        state.isDirty = false

        assertTrue(state.canSave(forceEnabled = true))
    }

    @Test
    fun canSave_falseWhileSaving() {
        state.isDirty = true
        state.isSaving = true

        assertFalse(state.canSave())
    }

    @Test
    fun canSave_falseOffline_evenWithTheCallerFlag() {
        state.isDirty = true
        state.online = false

        assertFalse(state.canSave(forceEnabled = true))
    }

    @Test
    fun saver_restoresTheOpenDialog() {
        state.isDirty = true
        state.onBack()
        val saver = EditorDiscardState.Saver
        val saved = with(saver) { SaverScope { true }.save(state) }

        val restored = assertNotNull(saved).let { saver.restore(it) }

        assertTrue(restored!!.showDiscardDialog)
    }
}
