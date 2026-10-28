package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.lopezjavier.appfitness.app.WeightEntryTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** US-57: a tapped reminder opens its entry on the weight screen exactly once, even across recreation (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class OpenRequestedEntryTest {
    @get:Rule
    val rule = createComposeRule()

    private val weightEvents = mutableListOf<WeightEvent>()
    private val circumferenceEvents = mutableListOf<CircumferencesEvent>()

    @Test
    fun weightTarget_opensTodaysEntryOnceTheScreenIsLoaded() {
        var weightReady by mutableStateOf(false)
        rule.setContent {
            OpenRequestedEntry(WeightEntryTarget.Weight, weightReady, false, { weightEvents += it }, {})
        }
        rule.waitForIdle()
        assertEquals(emptyList<WeightEvent>(), weightEvents)

        weightReady = true
        rule.waitForIdle()

        assertEquals(listOf<WeightEvent>(WeightEvent.LogToday), weightEvents)
    }

    @Test
    fun circumferenceTarget_opensTheNewMeasurementSheet() {
        rule.setContent {
            OpenRequestedEntry(
                WeightEntryTarget.Circumference,
                weightReady = true,
                circumferencesReady = true,
                onWeightEvent = { weightEvents += it },
                onCircumferenceEvent = { circumferenceEvents += it },
            )
        }
        rule.waitForIdle()

        assertEquals(listOf<CircumferencesEvent>(CircumferencesEvent.OpenAdd), circumferenceEvents)
        assertEquals(emptyList<WeightEvent>(), weightEvents)
    }

    @Test
    fun noTarget_opensNothing() {
        rule.setContent { OpenRequestedEntry(null, true, true, { weightEvents += it }, { circumferenceEvents += it }) }
        rule.waitForIdle()

        assertEquals(emptyList<WeightEvent>(), weightEvents)
        assertEquals(emptyList<CircumferencesEvent>(), circumferenceEvents)
    }

    @Test
    fun recreation_doesNotOpenTheEntryAgain() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            OpenRequestedEntry(WeightEntryTarget.Weight, true, true, { weightEvents += it }, {})
        }
        rule.waitForIdle()
        assertEquals(1, weightEvents.size)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(1, weightEvents.size)
    }
}
