package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-117: the food audit screen renders its states and reports taps; it never offers to delete or swipe a food. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class FoodAuditScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<FoodAuditEvent>()
    private var backs = 0

    private fun row(
        id: String,
        kcal: Double = 500.0,
        computed: Double = 250.0,
        level: EnergyCheckLevel = EnergyCheckLevel.Mismatch,
        brand: String? = null,
    ) = FoodAuditRow(id, "Alimento $id", brand, kcal, computed, computed - kcal, level)

    private fun show(state: FoodAuditUiState) {
        rule.setContent {
            AppTheme { FoodAuditScreen(state, emptyFlow(), { events += it }, { backs++ }) }
        }
        rule.waitForIdle()
    }

    @Test
    fun row_showsNameDeclaredMacrosAndSignedGap() {
        show(FoodAuditUiState.Content(listOf(row("a"))))

        rule.onNodeWithTag("food_audit_screen").assertExists()
        rule.onNodeWithTag("food_audit_title").assertTextContains("Revisar alimentos")
        rule.onNodeWithText("Alimento a").assertExists()
        rule.onNodeWithText("500 kcal declaradas · macros 250 kcal").assertExists()
        rule.onNodeWithText("−250 kcal").assertExists()
    }

    @Test
    fun positiveGap_isShownWithPlus() {
        show(FoodAuditUiState.Content(listOf(row("a", kcal = 100.0, computed = 160.0))))

        rule.onNodeWithText("+60 kcal").assertExists()
    }

    @Test
    fun gap_isDerivedFromTheRoundedFigures() {
        show(FoodAuditUiState.Content(listOf(row("a", kcal = 500.0, computed = 249.6))))

        rule.onNodeWithText("500 kcal declaradas · macros 250 kcal").assertExists()
        rule.onNodeWithText("−250 kcal").assertExists()
    }

    @Test
    fun brand_isShownOnItsOwnLine_whenPresent() {
        show(FoodAuditUiState.Content(listOf(row("a", brand = "Hacendado"))))

        rule.onNodeWithText("Hacendado\n500 kcal declaradas · macros 250 kcal").assertExists()
    }

    @Test
    fun clearlyWrongRow_saysSo_andMismatchRowDoesNot() {
        show(
            FoodAuditUiState.Content(
                listOf(row("a", level = EnergyCheckLevel.ClearlyWrong), row("b")),
            ),
        )

        assertEquals(1, rule.onAllNodesWithText("Parece un error").fetchSemanticsNodes().size)
        rule.onNodeWithTag("food_audit_row_a").assertTextContains("Parece un error", substring = true)
    }

    @Test
    fun rows_keepTheGivenOrder() {
        show(FoodAuditUiState.Content(listOf(row("z"), row("a"), row("m"))))

        val tops = listOf("z", "a", "m").map {
            rule.onNodeWithTag("food_audit_row_$it").getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sortedBy { it.value }, tops)
    }

    @Test
    fun rows_are48dpHigh() {
        show(FoodAuditUiState.Content(listOf(row("a"))))

        rule.onNodeWithTag("food_audit_row_a").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingARow_opensThatFood() {
        show(FoodAuditUiState.Content(listOf(row("a"), row("b"))))

        rule.onNodeWithTag("food_audit_row_b").performClick()

        assertEquals(listOf<FoodAuditEvent>(FoodAuditEvent.OpenFood("b")), events)
    }

    @Test
    fun rows_haveNoDeleteOrSwipeAffordance() {
        show(FoodAuditUiState.Content(listOf(row("a"))))

        assertTrue(rule.onAllNodes(hasTestTag("food_audit_row_a_delete")).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Eliminar").assertDoesNotExist()
    }

    @Test
    fun empty_showsAllClearWithoutAction() {
        show(FoodAuditUiState.Empty)

        rule.onNodeWithTag("food_audit_empty").assertExists()
        rule.onNodeWithText("Todo en orden").assertExists()
        rule.onNodeWithText("Ninguno de tus alimentos tiene calorías y macros que no cuadren.").assertExists()
        rule.onNodeWithTag("food_audit_empty_action").assertDoesNotExist()
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(FoodAuditUiState.Loading)

        rule.onNodeWithTag("food_audit_loading").assertExists()
        rule.onNodeWithTag("food_audit_list").assertDoesNotExist()
    }

    @Test
    fun error_offersRetry() {
        show(FoodAuditUiState.Error)

        rule.onNodeWithTag("food_audit_error").assertExists()
        rule.onNodeWithTag("food_audit_retry").performClick()

        assertEquals(listOf<FoodAuditEvent>(FoodAuditEvent.Retry), events)
    }

    @Test
    fun offline_showsTheOfflineState() {
        show(FoodAuditUiState.Offline)

        rule.onNodeWithTag("food_audit_offline").assertExists()
        rule.onNodeWithText("Sin conexión").assertExists()
    }

    @Test
    fun backArrow_leaves() {
        show(FoodAuditUiState.Empty)

        rule.onNodeWithTag("food_audit_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun helpButton_opensTheInfoSheetWithThreeBullets() {
        show(FoodAuditUiState.Empty)

        rule.onNodeWithTag("food_audit_help").assertHeightIsAtLeast(48.dp).performClick()

        rule.onNodeWithTag("food_audit_help_sheet_bullet_1").assertExists()
        rule.onNodeWithTag("food_audit_help_sheet_bullet_2").assertTextContains("No se cambia nada", substring = true)
        rule.onNodeWithTag("food_audit_help_sheet_bullet_3").assertTextContains("Guardar igualmente", substring = true)
    }

    @Test
    fun restoration_keepsTheScrollPosition_andTheOpenHelpSheet() {
        val rows = (1..40).map { row("f$it") }
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { FoodAuditScreen(FoodAuditUiState.Content(rows), emptyFlow(), {}, {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("food_audit_list").performScrollToIndex(30)
        rule.waitForIdle()
        rule.onNodeWithTag("food_audit_row_f31").assertExists()
        rule.onNodeWithTag("food_audit_help").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_audit_row_f31").assertExists()
        rule.onNodeWithTag("food_audit_help_sheet_bullet_1").assertExists()
    }
}
