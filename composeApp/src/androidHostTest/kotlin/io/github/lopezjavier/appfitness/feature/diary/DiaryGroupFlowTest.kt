package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateEffect
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateSheet
import io.github.lopezjavier.appfitness.feature.templates.SaveTemplateViewModel
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)
private const val GROUP = "g1"
private const val GROUP_TAG = "diary_group_$GROUP"
private const val TOGGLE_TAG = "diary_group_${GROUP}_toggle"

/** US-55 "Comidas grouped in the Diario", one test per scenario, with the real ViewModel and screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class DiaryGroupFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val templates = FakeMealTemplateRepository(
        mutableMapOf("f-a" to food("f-a", "Avena"), "f-b" to food("f-b", "Plátano")),
    )
    private var editedId: String? = null

    private fun entry(id: String, name: String, kcal: Double, group: String? = null, slot: String = "breakfast") =
        FoodLogEntry(
            id = id,
            logDate = Today,
            mealSlotId = slot,
            foodId = "f-$id",
            foodName = name,
            amount = 100.0,
            portionLabel = null,
            baseUnit = "g",
            kcal = kcal,
            proteinG = 5.0,
            carbsG = 20.0,
            fatG = 3.0,
            groupId = group,
            groupName = group?.let { "Avena con plátano" },
        )

    private val oats get() = entry("oats", "Avena", 150.0, GROUP)
    private val banana get() = entry("banana", "Plátano", 105.0, GROUP)

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = DiaryViewModel(
        FakeMealSlotRepository(),
        diary,
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
        BuildDiaryDay(),
        MutableClock(),
        connectivity,
        FakeMealDefaultsRepository(),
        state,
    )

    private fun start(vararg entries: FoodLogEntry) {
        diary.server[Today] = entries.toList()
        val model = viewModel()
        rule.setContent { Host(model) }
        rule.waitForIdle()
    }

    @Composable
    @Suppress("ViewModelForwarding") // A test host wires the real ViewModels.
    private fun Host(model: DiaryViewModel) {
        var sheetSlotId by remember { mutableStateOf<String?>(null) }
        AppTheme {
            DiaryRoot(
                onOpenPreferences = {},
                onAddToMeal = { _, _ -> },
                onAddFood = {},
                onSaveTemplate = { slot, _ -> sheetSlotId = slot },
                editHost = DiaryEditHost(
                    onEditEntry = { id, _ -> editedId = id },
                    pendingDeleteEntryId = null,
                    onConsumeDelete = {},
                ),
                viewModel = model,
            )
            sheetSlotId?.let { slot ->
                val saveModel = remember(slot) { saveModel(slot) }
                val state by saveModel.uiState.collectAsState()
                LaunchedEffect(saveModel) {
                    saveModel.effects.collect { if (it == SaveTemplateEffect.Saved) sheetSlotId = null }
                }
                SaveTemplateSheet(state, saveModel::onEvent, onDismiss = { sheetSlotId = null })
            }
        }
    }

    /** Like the server RPC: saving also groups the listed entries under one new id. */
    private val groupingTemplates = object : MealTemplateRepository by templates {
        override suspend fun saveAsComida(
            date: LocalDate,
            mealSlotId: String,
            name: String,
            entryIds: List<String>,
            items: List<NewTemplateItem>,
        ): Result<String> {
            val result = templates.saveAsComida(date, mealSlotId, name, entryIds, items)
            diary.push(
                date,
                diary.server.getValue(date).map {
                    if (it.id in entryIds) it.copy(groupId = "new-group", groupName = name) else it
                },
            )
            return result
        }
    }

    private fun saveModel(slotId: String) = SaveTemplateViewModel(
        slotId = slotId,
        date = Today,
        foodDiary = diary,
        buildTemplateItems = BuildTemplateItems(),
        validateTemplateName = ValidateTemplateName(),
        mealTemplates = groupingTemplates,
        connectivity = connectivity,
        savedState = SavedStateHandle(),
    )

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun inGroupHeader(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(TOGGLE_TAG))

    @Test
    fun savedComidaAdded_showsOneCollapsedRowWithTotals_andNoIngredients() {
        start(oats, banana)

        rule.onNodeWithTag(GROUP_TAG).assertExists()
        rule.onNode(inGroupHeader("Avena con plátano"), useUnmergedTree = true).assertExists()
        rule.onNode(inGroupHeader("255 kcal"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("diary_entry_oats").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_banana").assertDoesNotExist()
        rule.onNodeWithTag(TOGGLE_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Contraída"))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun expandingTheGroup_showsIngredients_tapEditsOne_andSwipeDeletesOne() {
        start(oats, banana)

        tap(TOGGLE_TAG)

        rule.onNodeWithTag(TOGGLE_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expandida"))
        rule.onNodeWithTag("diary_entry_oats").assertExists()
        rule.onNodeWithTag("diary_entry_banana").assertExists()
        tap("diary_entry_oats")
        assertEquals("oats", editedId)
        rule.onNodeWithTag("diary_entry_banana").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf("banana"), diary.deleted)
    }

    @Test
    fun aFoodAddedLater_staysOutsideTheGroup() {
        start(oats, banana)
        tap(TOGGLE_TAG)

        diary.push(Today, diary.server.getValue(Today) + entry("cafe", "Café con leche", 60.0))
        rule.waitForIdle()

        rule.onNodeWithTag("diary_entry_cafe").assertExists()
        rule.onNode(hasTestTag("diary_entry_cafe") and hasAnyAncestor(hasTestTag(GROUP_TAG))).assertDoesNotExist()
    }

    @Test
    fun removingIngredients_updatesTheGroup_andTheLastOneRemovesIt() {
        start(oats, banana)
        tap(TOGGLE_TAG)

        rule.onNodeWithTag("diary_entry_oats").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNode(inGroupHeader("105 kcal"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("diary_entry_banana").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        rule.onNodeWithTag(GROUP_TAG).assertDoesNotExist()
    }

    @Test
    fun savingTheMealAsComida_turnsItsFoodsIntoOneGroup() {
        start(entry("a", "Avena", 150.0), entry("b", "Plátano", 105.0))

        tap("diary_section_breakfast_save_template")
        rule.onNodeWithTag("save_template_name").performTextReplacement("Mi desayuno")
        rule.waitForIdle()
        tap("save_template_submit")

        rule.onNodeWithTag("diary_group_new-group").assertExists()
        rule.onNode(
            hasText("Mi desayuno") and hasAnyAncestor(hasTestTag("diary_group_new-group_toggle")),
            useUnmergedTree = true,
        ).assertExists()
        rule.onNodeWithTag("diary_entry_a").assertDoesNotExist()
        assertEquals(listOf("a", "b"), templates.comidaSaves.single().third)
    }

    @Test
    fun entriesWithoutGroup_renderAsIndividualRows() {
        start(entry("a", "Avena", 150.0), entry("b", "Plátano", 105.0))

        rule.onNodeWithTag("diary_entry_a").assertExists()
        rule.onNodeWithTag("diary_entry_b").assertExists()
    }

    @Test
    fun aDefaultComidaAppliedOnANewDay_showsAsACollapsedGroup() {
        start(oats, banana, entry("lunch1", "Arroz", 300.0, "g2", slot = "lunch"))

        rule.onNodeWithTag(GROUP_TAG).assertExists()
        rule.onNodeWithTag("diary_group_g2").assertExists()
        rule.onNodeWithTag("diary_entry_oats").assertDoesNotExist()
        rule.onNodeWithTag("diary_entry_lunch1").assertDoesNotExist()
    }
}
