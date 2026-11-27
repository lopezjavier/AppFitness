package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.SummarizeCircumferences
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object SiteClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 21)

    override fun now(): Instant = Instant.parse("2026-10-21T10:30:00Z")
}

/** T-324 (US-148): one circumference site's screen: hero, chart, "Historial", editing, deleting and its states. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin step.
class CircumferenceSiteScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 21)
    private val connectivity = FakeConnectivityObserver()
    private lateinit var repository: FakeCircumferenceRepository
    private var backs = 0

    /** Cintura: 86,5 cm on 20 sep, 85,5 cm on 19 oct and 84,5 cm yesterday (−2,0 cm, inside its 3,0 cm margin). */
    private val waist = listOf(
        circumferenceRow("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:30:00Z"),
        circumferenceRow("w2", CircumferenceSite.Waist, 85.5, "2026-10-19T08:30:00Z"),
        circumferenceRow("w3", CircumferenceSite.Waist, 84.5, "2026-10-20T08:30:00Z"),
    )

    private fun showState(
        state: CircumferenceSiteUiState,
        site: CircumferenceSite = CircumferenceSite.Waist,
        fontScale: Float? = null,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                AppTheme {
                    CircumferenceSiteScreen(site, state, today, {}, { backs++ }, timeZone = TimeZone.UTC)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun showRoot(rows: List<BodyCircumference>, site: CircumferenceSite = CircumferenceSite.Waist) {
        repository = repositoryOf(rows)
        showRoot(site)
    }

    private fun repositoryOf(rows: List<BodyCircumference>) = FakeCircumferenceRepository().apply { this.rows += rows }

    private fun showRoot(site: CircumferenceSite) {
        val viewModel = CircumferencesViewModel(
            repository = repository,
            validate = ValidateCircumference(),
            waistHipRatio = WaistHipRatio(),
            summarize = SummarizeCircumferences(),
            connectivity = connectivity,
            clock = SiteClock,
            timeZone = TimeZone.UTC,
            savedState = SavedStateHandle(),
        )
        rule.setContent {
            AppTheme { CircumferenceSiteRoot(site, onBack = { backs++ }, viewModel = viewModel, clock = SiteClock) }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun assertShows(tag: String, text: String) {
        rule.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertExists()
    }

    private fun swipeDelete(id: String, label: String) {
        val actions = rule.onNodeWithTag("circumference_row_$id").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)
        val action = actions?.firstOrNull { it.label == label } ?: error("No action '$label' on $id")
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
    }

    private fun assertNoEmptyState() {
        rule.onNodeWithText("Registra tu primera circunferencia.").assertDoesNotExist()
        rule.onNodeWithText("Aún no hay circunferencias").assertDoesNotExist()
        rule.onNodeWithTag("circumference_site_empty").assertDoesNotExist()
    }

    // -- Hero and chart ---------------------------------------------------------------------------------------------

    @Test
    fun hero_showsTheLatestValueItsDayAndTheChangeInsideTheMargin() {
        showState(circumferenceSiteState(CircumferenceSite.Waist, waist))

        rule.onNodeWithTag("circumference_site_title").assertTextEquals("Cintura")
        rule.onNodeWithTag("circumference_site_value", useUnmergedTree = true).assertTextEquals("84,5 cm")
        assertShows("circumference_site_hero", "Ayer")
        assertShows("circumference_site_change", "−2,0 cm")
        assertShows("circumference_site_change", "desde el 20 sep")
        rule.onNodeWithTag("circumference_site_margin_note", useUnmergedTree = true)
            .assertTextEquals("dentro del margen de medida")
    }

    @Test
    fun waist_showsTheChartAndTheMarginLegend() {
        showState(circumferenceSiteState(CircumferenceSite.Waist, waist))

        rule.onNodeWithTag("circumference_site_chart").assertExists()
        assertShows("circumference_site_margin_legend", "Margen de medida (±3,0 cm)")
    }

    @Test
    fun chest_hasAChangeButNoMarginLegendNorNote() {
        val chest = listOf(
            circumferenceRow("c1", CircumferenceSite.Chest, 100.0, "2026-09-20T08:30:00Z"),
            circumferenceRow("c2", CircumferenceSite.Chest, 99.0, "2026-10-20T08:30:00Z"),
        )
        showState(circumferenceSiteState(CircumferenceSite.Chest, chest), CircumferenceSite.Chest)

        rule.onNodeWithTag("circumference_site_chart").assertExists()
        assertShows("circumference_site_change", "−1,0 cm")
        rule.onNodeWithTag("circumference_site_margin_legend").assertDoesNotExist()
        rule.onNodeWithTag("circumference_site_margin_note", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun oneMeasurement_showsTheOnePointNote_andNoChart() {
        showState(circumferenceSiteState(CircumferenceSite.Waist, waist.takeLast(1)))

        rule.onNodeWithTag("circumference_site_one_point", useUnmergedTree = true)
            .assertTextEquals("Con una segunda medida verás aquí su evolución.")
        rule.onNodeWithTag("circumference_site_chart").assertDoesNotExist()
        rule.onNodeWithTag("circumference_site_change", useUnmergedTree = true).assertDoesNotExist()
    }

    // -- Historial --------------------------------------------------------------------------------------------------

    @Test
    fun historial_isNewestFirst_withTheChangeAgainstTheValueBefore() {
        showState(circumferenceSiteState(CircumferenceSite.Waist, waist))

        assertShows("circumference_site_history_header", "Historial")
        assertShows("circumference_site_history_header", "3")
        val newest = rule.onNodeWithTag("circumference_row_w3").getUnclippedBoundsInRoot()
        val middle = rule.onNodeWithTag("circumference_row_w2").getUnclippedBoundsInRoot()
        val oldest = rule.onNodeWithTag("circumference_row_w1").getUnclippedBoundsInRoot()
        assertTrue(newest.bottom <= middle.top && middle.bottom <= oldest.top)
        assertShows("circumference_row_w3", "Ayer · −1,0 cm")
        assertShows("circumference_row_w3", "84,5 cm")
    }

    @Test
    fun tappingAValue_opensEditarMedida_withTheSiteLocked() {
        showRoot(waist)

        tap("circumference_row_w3")

        rule.onNodeWithTag("circumference_sheet_title").assertTextEquals("Editar medida")
        rule.onNodeWithTag("circumference_sheet_value_input_waist").assertExists()
        rule.onNodeWithTag("circumference_sheet_value_input_hip").assertDoesNotExist()
    }

    @Test
    fun guardar_recalculatesTheHeroTheChangeAndTheList() {
        showRoot(waist)

        tap("circumference_row_w3")
        rule.onNodeWithTag("circumference_sheet_value_input_waist").performTextReplacement("83,0")
        tap("circumference_sheet_commit")

        assertEquals(83.0, repository.rows.first { it.id == "w3" }.valueCm, 0.0)
        rule.onNodeWithTag("circumference_sheet").assertDoesNotExist()
        rule.onNodeWithTag("circumference_site_value", useUnmergedTree = true).assertTextEquals("83,0 cm")
        assertShows("circumference_site_change", "−3,5 cm")
        rule.onNodeWithTag("circumference_site_margin_note", useUnmergedTree = true).assertDoesNotExist()
        assertShows("circumference_row_w3", "Ayer · −2,5 cm")
    }

    @Test
    fun eliminarInTheSheet_removesTheValueAtOnce_withNoUndo_andStays() {
        showRoot(waist)

        tap("circumference_row_w3")
        tap("circumference_sheet_delete")

        assertEquals(listOf("w3"), repository.deletes)
        rule.onNodeWithTag("circumference_sheet").assertDoesNotExist()
        rule.onNodeWithTag("circumference_row_w3").assertDoesNotExist()
        rule.onNodeWithTag("circumference_site_value", useUnmergedTree = true).assertTextEquals("85,5 cm")
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals(0, backs)
    }

    @Test
    fun swipeDelete_removesTheValueAtOnce_withNoUndo() {
        showRoot(waist)

        swipeDelete("w3", "Eliminar Cintura 84,5 cm")

        assertEquals(listOf("w3"), repository.deletes)
        rule.onNodeWithTag("circumference_row_w3").assertDoesNotExist()
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
        assertEquals(0, backs)
    }

    @Test
    fun deletingTheLastValue_neverShowsAnEmptyState_andReturnsOnceConfirmed() {
        showRoot(listOf(waist.last()))
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate

        swipeDelete("w3", "Eliminar Cintura 84,5 cm")

        rule.onNodeWithTag("circumference_row_w3").assertDoesNotExist()
        rule.onNodeWithTag("circumference_site_hero").assertExists()
        assertNoEmptyState()
        assertEquals(0, backs)

        gate.complete(Unit)
        rule.waitForIdle()

        assertNoEmptyState()
        assertEquals(1, backs)
    }

    @Test
    fun aSiteWithNoValuesOnArrival_returnsAtOnce() {
        showRoot(listOf(circumferenceRow("h", CircumferenceSite.Hip, 98.0)))

        assertEquals(1, backs)
        assertNoEmptyState()
    }

    @Test
    fun offline_locksSwipe_andDisablesEliminarWithTheHint() {
        connectivity.state.value = false
        showRoot(waist)

        val actions = rule.onNodeWithTag("circumference_row_w3").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)
        assertTrue(actions.isNullOrEmpty())
        tap("circumference_row_w3")

        rule.onNodeWithTag("circumference_sheet_delete").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("circumference_sheet_commit_hint").assertTextEquals("Sin conexión: no se puede guardar")
    }

    // -- States -----------------------------------------------------------------------------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        showState(CircumferenceSiteUiState.Loading)

        rule.onNodeWithTag("circumference_site_loading").assertExists()
        rule.onNodeWithTag("circumference_site_title").assertTextEquals("Cintura")
    }

    @Test
    fun failedLoad_showsTheError_andReintentarReloads() {
        repository = repositoryOf(waist)
        repository.failReads = true
        showRoot(CircumferenceSite.Waist)

        rule.onNodeWithTag("circumference_site_error").assertExists()
        assertShows("circumference_site_error", "No se pudieron cargar las circunferencias.")
        repository.failReads = false
        tap("circumference_site_error_action")

        rule.onNodeWithTag("circumference_site_hero").assertExists()
        assertEquals(0, backs)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-xxhdpi")
    fun wideWindow_showsTwoColumns() {
        showState(circumferenceSiteState(CircumferenceSite.Waist, waist))

        rule.onNodeWithTag("circumference_site_two_column").assertExists()
        val hero = rule.onNodeWithTag("circumference_site_hero").getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("circumference_site_history_list").getUnclippedBoundsInRoot()
        assertTrue(list.left >= hero.right)
        rule.onNodeWithTag("circumference_row_w3").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun fontScale130_keepsTheTitleHeroChartAndFirstValueReachable() {
        assertReachableAt(1.3f)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun fontScale200_keepsTheTitleHeroChartAndFirstValueReachable() {
        assertReachableAt(2f)
    }

    private fun assertReachableAt(scale: Float) {
        showState(circumferenceSiteState(CircumferenceSite.Waist, waist), fontScale = scale)

        rule.onNodeWithTag("circumference_site_title").assertIsDisplayed()
        rule.onNodeWithTag("circumference_site_value", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("circumference_site_list").performScrollToNode(hasTestTag("circumference_site_chart"))
        rule.onNodeWithTag("circumference_site_chart").assertIsDisplayed()
        rule.onNodeWithTag("circumference_site_list").performScrollToNode(hasTestTag("circumference_row_w3"))
        rule.onNodeWithTag("circumference_row_w3").assertIsDisplayed()
        assertShows("circumference_row_w3", "84,5 cm")
    }
}
