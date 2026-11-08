package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R-59: the "no fit" note of the Types step opens "Mi entrenamiento" through the full builder screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockBuilderNoFitTapTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockBuilderEvent>()

    @Test
    fun tappingTheNote_onTheBuilderScreen_emitsOpenMyTraining() {
        val chosen = listOf(RoutineType.Upper, RoutineType.Lower, RoutineType.Upper, RoutineType.Lower)
        val split = SplitCatalog.splitsFor(chosen.size)[0]
        val options = chosen.indices.map { SplitCatalog.dayOptions(split, chosen, it, null) }
        val state = BlockBuilderUiState.Types(split, chosen, options, null, NoFitInfo(1, 95, 45))
        rule.setContent { AppTheme { BlockBuilderScreen(state, flowOf(true), { events += it }, {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("block_types_no_fit").performClick()

        assertEquals(listOf<BlockBuilderEvent>(BlockBuilderEvent.OpenMyTraining), events)
    }
}
