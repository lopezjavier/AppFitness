package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BodyHeatmapTest {
    @get:Rule
    val rule = createComposeRule()

    private val clicks = mutableListOf<MuscleGroup>()

    private val regions = listOf(
        BodyRegion(MuscleGroup.Chest, 3.0, "Pecho, 3 series", "3", "body_chest"),
        BodyRegion(MuscleGroup.Triceps, 1.5, "Tríceps, 1,5 series", "1,5", "body_triceps"),
        BodyRegion(MuscleGroup.Quads, 0.0, "Cuádriceps, 0 series", "0", "body_quads"),
        BodyRegion(MuscleGroup.Calves, 1.0, "Gemelos, 1 serie", "1", "body_calves"),
    )

    private fun show(fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme { BodyHeatmap(regions, { clicks += it }, tagPrefix = "body") }
            }
        }
    }

    @Test
    fun heatStep_isZeroForNothing_andOneStepPerQuartileOfTheMaximum() {
        assertEquals(0, BodyHeatmapDefaults.step(0.0, 8.0))
        assertEquals(1, BodyHeatmapDefaults.step(0.5, 8.0))
        assertEquals(1, BodyHeatmapDefaults.step(2.0, 8.0))
        assertEquals(2, BodyHeatmapDefaults.step(4.0, 8.0))
        assertEquals(3, BodyHeatmapDefaults.step(5.0, 8.0))
        assertEquals(4, BodyHeatmapDefaults.step(8.0, 8.0))
        assertEquals(0, BodyHeatmapDefaults.step(3.0, 0.0))
    }

    @Test
    fun bothSilhouettesExist_andRegionsDescribeTheirSets() {
        show()

        rule.onNodeWithTag("body_front").assertExists()
        rule.onNodeWithTag("body_back").assertExists()
        rule.onNodeWithTag("body_chest").assertContentDescriptionEquals("Pecho, 3 series")
        rule.onNodeWithTag("body_triceps").assertContentDescriptionEquals("Tríceps, 1,5 series")
    }

    @Test
    fun tappingARegion_reportsItsMuscle() {
        show()

        rule.onNodeWithTag("body_chest").performClick()
        rule.onNodeWithTag("body_quads").performClick()

        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Quads), clicks)
    }

    @Test
    fun regions_haveAtLeast48dpTargets_evenAtLargeFontScale() {
        show(fontScale = 2f)

        listOf("body_chest", "body_triceps", "body_calves", "body_quads").forEach {
            rule.onNodeWithTag(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
}
