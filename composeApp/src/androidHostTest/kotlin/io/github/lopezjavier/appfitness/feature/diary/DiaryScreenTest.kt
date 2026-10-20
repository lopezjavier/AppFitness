package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)
private const val FONT_SCALE = 1.3f

private val DefaultSlots = listOf(
    MealSlot("breakfast", MealCode.Breakfast, "Desayuno", 0),
    MealSlot("lunch", MealCode.Lunch, "Comida", 1),
    MealSlot("snack", MealCode.Snack, "Merienda", 2),
    MealSlot("dinner", MealCode.Dinner, "Cena", 3),
)

private fun entry(
    id: String,
    slotId: String,
    name: String = "Food $id",
    amount: Double = 100.0,
    portion: String? = null,
    kcal: Double = 100.0,
) = FoodLogEntry(id, Today, slotId, "f-$id", name, amount, portion, "g", kcal, 0.0, 0.0, 0.0)

private fun section(slot: MealSlot, entries: List<FoodLogEntry>, remaining: Int?) = MealSectionUi(
    slot = slot,
    entries = entries,
    totals = MealTotals(entries.sumOf { it.kcal }, 0.0, 0.0, 0.0),
    isEmpty = entries.isEmpty(),
    showRemainingHint = entries.isEmpty() && remaining != null && remaining > 0,
    remainingKcal = remaining,
)

private fun content(
    entries: List<FoodLogEntry> = emptyList(),
    totals: MealTotals = MealTotals(1840.0, 142.0, 210.0, 48.0),
    goals: DiaryGoalsState = DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = 560),
    slots: List<MealSlot> = DefaultSlots,
    canGoNext: Boolean = false,
    collapsed: Set<String> = emptySet(),
) = DiaryUiState.Content(
    DiaryContent(
        date = Today,
        label = DateLabel.Today,
        canGoNext = canGoNext,
        goals = goals,
        meals = slots.map { slot ->
            section(slot, entries.filter { it.mealSlotId == slot.id }, (goals as? DiaryGoalsState.Set)?.remainingKcal)
        },
        dayTotals = totals,
        collapsedSlotIds = collapsed,
    ),
)

/** US-19 day structure, US-20 progress and US-23 default meals, rendered from a given state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class DiaryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<DiaryEvent>()

    private fun show(state: DiaryUiState, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme { DiaryScreen(state, { events += it }) }
            }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun stateDescription(tag: String): String? = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun dayStructure_showsTodayAndTheFourDefaultSectionsInOrderEachWithItsAddButton() {
        show(content())

        rule.onNodeWithTag("diary_date_label").assertTextEquals("Hoy")
        val tags = DefaultSlots.map { "diary_section_${it.id}" }
        tags.forEach { rule.onNodeWithTag(it).assertExists() }
        assertEquals(tags, tags.sortedBy(::top))
        rule.onNodeWithText("Añadir al desayuno").assertExists()
        rule.onNodeWithText("Añadir a la comida").assertExists()
        rule.onNodeWithText("Añadir a la merienda").assertExists()
        rule.onNodeWithText("Añadir a la cena").assertExists()
    }

    @Test
    fun addButton_emitsAddToMealForItsSection() {
        show(content())

        rule.onNodeWithTag("diary_section_dinner_add").performScrollTo().performClick()

        assertEquals(listOf<DiaryEvent>(DiaryEvent.AddToMeal("dinner")), events)
    }

    @Test
    fun renamedDefaultSlot_andCustomSlot_useTheLabelInTheAddButton() {
        val slots = listOf(
            MealSlot("breakfast", MealCode.Breakfast, "Almuerzo", 0),
            MealSlot("custom", null, "Pre-entreno", 1),
        )
        show(content(slots = slots))

        rule.onNodeWithText("Añadir a Almuerzo").assertExists()
        rule.onNodeWithText("Añadir a Pre-entreno").assertExists()
        rule.onNodeWithText("Añadir al desayuno").assertDoesNotExist()
    }

    @Test
    fun sectionHeader_showsLabelKcalAndMacrosOfTheMeal() {
        val rows = listOf(entry("a", "breakfast", kcal = 700.5))
        val state = content(entries = rows)
        show(state)

        rule.onNodeWithTag("diary_section_breakfast_toggle").assertExists()
        rule.onNodeWithText("Desayuno", useUnmergedTree = true).assertExists()
        rule.onNode(
            hasText("701 kcal") and hasAnyAncestor(hasTestTag("diary_section_breakfast_toggle")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun entryRow_showsNameAmountWithUnitAndKcal() {
        show(content(entries = listOf(entry("a", "lunch", "Arroz", 220.0, kcal = 285.3))))

        rule.onNodeWithTag("diary_entry_a").assertExists()
        rule.onNodeWithText("Arroz").assertExists()
        rule.onNodeWithText("220 g").assertExists()
        rule.onNode(hasText("285 kcal") and hasAnyAncestor(hasTestTag("diary_entry_a"))).assertExists()
    }

    @Test
    fun entryRow_withPortionLabel_showsThePortionInsteadOfTheAmount() {
        show(content(entries = listOf(entry("a", "lunch", "Huevo", 55.0, portion = "1 huevo"))))

        rule.onNodeWithText("1 huevo").assertExists()
    }

    @Test
    fun entryRow_decimalAmount_usesCommaWithOneDecimal() {
        show(content(entries = listOf(entry("a", "lunch", "Aceite", 12.5))))

        rule.onNodeWithText("12,5 g").assertExists()
    }

    @Test
    fun dayProgress_showsRingNumbersAndMacroBars() {
        show(content())

        rule.onNodeWithText("1.840", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("de 2.400 kcal", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("560 restantes", useUnmergedTree = true).assertExists()
        assertEquals("142 de 180 gramos, 79 por ciento", stateDescription("diary_macro_protein"))
        assertEquals("210 de 270 gramos, 78 por ciento", stateDescription("diary_macro_carbs"))
        assertEquals("48 de 67 gramos, 72 por ciento", stateDescription("diary_macro_fat"))
    }

    @Test
    fun dashboard_listsProteinCarbsFatInOrder() {
        show(content())

        val tags = listOf("diary_macro_protein", "diary_macro_carbs", "diary_macro_fat")
        assertEquals(tags, tags.sortedBy(::top))
        assertTrue(top("diary_ring") < top("diary_macro_protein"))
    }

    @Test
    fun calorieExcess_showsTheAmountOverTheTarget() {
        show(
            content(
                totals = MealTotals(2550.0, 0.0, 0.0, 0.0),
                goals = DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = -150),
            ),
        )

        rule.onNodeWithText("2.550", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("+150 kcal en exceso", useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptySection_showsWhatIsLeftOfTheDayAndStillOffersAdd() {
        show(content(entries = listOf(entry("a", "breakfast"))))

        rule.onNodeWithTag("diary_section_snack_empty_hint").assertTextEquals("Te quedan 560 kcal")
        rule.onNodeWithTag("diary_section_snack_add").assertExists()
        rule.onNodeWithTag("diary_section_breakfast_empty_hint").assertDoesNotExist()
    }

    @Test
    fun emptySection_withoutRemainingHint_showsNoText() {
        show(content(goals = DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = -150)))

        rule.onNodeWithTag("diary_section_snack_empty_hint").assertDoesNotExist()
        rule.onNodeWithTag("diary_section_snack_add").assertExists()
    }

    @Test
    fun noGoals_showsThePromptInsteadOfTheDashboardAndKeepsTheSections() {
        show(content(goals = DiaryGoalsState.NotSet))

        rule.onNodeWithTag("diary_goals_prompt").assertExists()
        rule.onNodeWithText("Configura tu objetivo", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("diary_ring").assertDoesNotExist()
        rule.onNodeWithTag("diary_macro_protein").assertDoesNotExist()
        rule.onNodeWithTag("diary_section_breakfast").assertExists()

        rule.onNodeWithTag("diary_goals_prompt").performClick()

        assertEquals(listOf<DiaryEvent>(DiaryEvent.OpenGoals), events)
    }

    @Test
    fun dateHeader_arrowsEmitEventsAndNextIsDisabledOnToday() {
        show(content(canGoNext = false))

        rule.onNodeWithTag("diary_date_next").assertIsNotEnabled()
        rule.onNodeWithTag("diary_date_prev").assertIsEnabled().performClick()

        assertEquals(listOf<DiaryEvent>(DiaryEvent.PreviousDay), events)
    }

    @Test
    fun dateHeader_nextIsEnabledOnAPastDay() {
        show(content(canGoNext = true))

        rule.onNodeWithTag("diary_date_next").assertIsEnabled().performClick()

        assertEquals(listOf<DiaryEvent>(DiaryEvent.NextDay), events)
    }

    @Test
    fun sectionHeader_togglesAndDescribesItsState() {
        show(content(entries = listOf(entry("a", "breakfast"))))

        assertEquals("Expandida", stateDescription("diary_section_breakfast_toggle"))
        rule.onNodeWithTag("diary_section_breakfast_toggle").performClick()

        assertEquals(listOf<DiaryEvent>(DiaryEvent.ToggleSection("breakfast")), events)
    }

    @Test
    fun collapsedSection_hidesItsEntriesAndSaysSo() {
        show(content(entries = listOf(entry("a", "breakfast")), collapsed = setOf("breakfast")))

        rule.onNodeWithTag("diary_entry_a").assertDoesNotExist()
        assertEquals("Contraída", stateDescription("diary_section_breakfast_toggle"))
        rule.onNodeWithTag("diary_section_lunch_add").assertExists()
    }

    @Test
    fun loading_showsSkeletonsOnly() {
        show(DiaryUiState.Loading)

        rule.onNodeWithTag("diary_loading").assertExists()
        rule.onNodeWithTag("diary_date_label").assertDoesNotExist()
    }

    @Test
    fun error_offersRetry() {
        show(DiaryUiState.Error)

        rule.onNodeWithText("No se pudo cargar tu diario").assertExists()
        rule.onNodeWithTag("diary_error_action").performClick()

        assertEquals(listOf<DiaryEvent>(DiaryEvent.Retry), events)
    }

    @Test
    fun largeFont_keepsEverythingReachable() {
        val longName = "Una comida con un nombre muy largo para ver el ajuste"
        show(content(entries = listOf(entry("a", "dinner", longName))), FONT_SCALE)

        rule.onNodeWithTag("diary_ring").assertIsDisplayed()
        rule.onNodeWithTag("diary_section_dinner_add").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("diary_entry_a").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-xxhdpi")
    fun wideWindow_putsTheDashboardLeftOfTheMeals() {
        show(content())

        val ring = rule.onNodeWithTag("diary_ring").getUnclippedBoundsInRoot()
        val breakfast = rule.onNodeWithTag("diary_section_breakfast").getUnclippedBoundsInRoot()
        assertTrue("ring ${ring.right} must end before meals ${breakfast.left}", ring.right <= breakfast.left)
    }

    @Test
    fun compactWindow_stacksTheDashboardAboveTheMeals() {
        show(content())

        val ring = rule.onNodeWithTag("diary_ring").getUnclippedBoundsInRoot()
        val breakfast = rule.onNodeWithTag("diary_section_breakfast").getUnclippedBoundsInRoot()
        assertTrue("ring ${ring.bottom} above meals ${breakfast.top}", ring.bottom <= breakfast.top)
    }
}
