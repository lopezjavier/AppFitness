package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SecondaryButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show(enabled: Boolean = true) {
        clicks = 0
        rule.setContent {
            AppTheme {
                Row {
                    SecondaryButton("Atrás", onClick = {
                        clicks++
                    }, modifier = Modifier.testTag("secondary"), enabled = enabled)
                    PrimaryButton("Continuar", onClick = {}, modifier = Modifier.testTag("primary"))
                }
            }
        }
    }

    @Test
    fun secondary_isAsTallAsThePrimary_andAtLeast48dp() {
        show()
        rule.onNodeWithTag("secondary").assertHeightIsAtLeast(AppButtonDefaults.MinHeight)
        rule.onNodeWithTag("secondary").assertHeightIsEqualTo(AppButtonDefaults.PrimaryMinHeight)
        rule.onNodeWithTag("primary").assertHeightIsEqualTo(AppButtonDefaults.PrimaryMinHeight)
    }

    @Test
    fun enabledSecondary_callsBack() {
        show()
        rule.onNodeWithTag("secondary").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledSecondary_isNotEnabled_andIgnoresTaps() {
        show(enabled = false)
        rule.onNodeWithTag("secondary").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}
