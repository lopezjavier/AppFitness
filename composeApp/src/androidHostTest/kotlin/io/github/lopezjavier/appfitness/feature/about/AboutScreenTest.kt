package io.github.lopezjavier.appfitness.feature.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-45: opening "Acerca de", the three credits and the open-source licences row. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AboutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var licenseOpens = 0
    private var backs = 0

    private fun show(info: BuildInfo = BuildInfo("AppFitness", "1.0", 7, "prod")) {
        licenseOpens = 0
        backs = 0
        rule.setContent {
            AppTheme {
                AboutScreen(AboutUiState(info), onBack = { backs++ }, onOpenLicenses = { licenseOpens++ })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun opening_showsTheAppNameAndTheVersion() {
        show()

        rule.onNodeWithTag("about_app_name").assertTextEquals("AppFitness")
        rule.onNodeWithTag("about_version").assertTextEquals("Versión 1.0 (7)")
    }

    @Test
    fun opening_onANonProdFlavor_appendsTheFlavorToTheVersionLine() {
        show(BuildInfo("AppFitness", "1.0", 7, "local"))

        rule.onNodeWithTag("about_version").assertTextEquals("Versión 1.0 (7) · local")
    }

    @Test
    fun credits_showsTheThreeSources() {
        show()

        rule.onNodeWithTag("about_credits_heading").assertIsDisplayed()
        listOf("RepDB", "Open Food Facts", "Google Gemini").forEachIndexed { index, title ->
            val credit = rule.onNodeWithTag("about_credit_$index").performScrollTo()
            credit.assertIsDisplayed()
            rule.onNodeWithTag("about_credit_$index").assertTextContains(title)
        }
        rule.onNodeWithTag("about_credit_0").assertTextContains("Datos de ejercicios por RepDB (repdb.co)")
    }

    @Test
    fun licensesRow_opensTheLicencesOnce() {
        show()

        rule.onNodeWithTag("about_licenses_row").performScrollTo().performClick()

        assertEquals(1, licenseOpens)
    }

    @Test
    fun screen_recomposesAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                AboutScreen(
                    AboutUiState(BuildInfo("AppFitness", "1.0", 7, "prod")),
                    onBack = {},
                    onOpenLicenses = {},
                )
            }
        }

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("about_version").assertTextEquals("Versión 1.0 (7)")
    }
}
