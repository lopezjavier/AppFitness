package io.github.lopezjavier.appfitness.designsystem

import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppDatePickerDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private var confirmed: LocalDate? = null
    private var dismissed = 0

    // The cells are described by their full date (Robolectric runs in English).
    private fun day(number: Int) = rule.onNode(hasText("October $number, 2026", substring = true))

    private fun show(selected: LocalDate? = LocalDate(2026, 10, 10), maxDate: LocalDate? = LocalDate(2026, 10, 20)) {
        rule.setContent {
            AppTheme {
                AppDatePickerDialog(
                    selected = selected,
                    onConfirm = { confirmed = it },
                    onDismiss = { dismissed++ },
                    confirmLabel = "Listo",
                    dismissLabel = "Cancelar",
                    maxDate = maxDate,
                    testTagPrefix = "picker",
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun selectingADayAndAccepting_reportsThatDate() {
        show()

        day(15).performClick()
        rule.onNodeWithTag("picker_confirm").performClick()

        assertEquals(LocalDate(2026, 10, 15), confirmed)
    }

    @Test
    fun acceptingWithoutChangingTheSelection_reportsTheInitialDate() {
        show()

        rule.onNodeWithTag("picker_confirm").assertIsEnabled().performClick()

        assertEquals(LocalDate(2026, 10, 10), confirmed)
    }

    @Test
    fun daysAfterMaxDateCannotBeSelected() {
        show()

        day(25).assertIsNotEnabled()
        day(20).assertIsEnabled()
    }

    @Test
    fun withoutAMaxDate_laterDaysAreSelectable() {
        show(maxDate = null)

        day(25).assertIsEnabled()
    }

    @Test
    fun cancel_dismissesWithoutReportingADate() {
        show()

        rule.onNodeWithTag("picker_dismiss").performClick()

        assertEquals(1, dismissed)
        assertEquals(null, confirmed)
    }

    @Test
    fun tappingOutsideTheDialog_dismissesWithoutReportingADate() {
        show()

        // A touch beyond the window edge reaches the platform dialog as an outside touch.
        val dialog = ShadowDialog.getLatestDialog()
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, -200f, -200f, 0)
        val up = MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_UP, -200f, -200f, 0)
        rule.runOnUiThread {
            dialog.dispatchTouchEvent(down)
            dialog.dispatchTouchEvent(up)
        }
        rule.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(null, confirmed)
    }

    @Test
    fun withoutAnInitialDate_acceptStaysDisabledUntilADayIsPicked() {
        show(selected = null)

        rule.onNodeWithTag("picker_confirm").assertIsNotEnabled()
        day(12).performClick()
        rule.onNodeWithTag("picker_confirm").assertIsEnabled()
    }

    @Test
    fun conversionRoundTripsTheDay() {
        val date = LocalDate(2026, 2, 28)
        var restored by mutableStateOf(date)

        restored = AppDatePickerDialogDefaults.toDate(AppDatePickerDialogDefaults.toMillis(date))

        assertEquals(date, restored)
    }
}
