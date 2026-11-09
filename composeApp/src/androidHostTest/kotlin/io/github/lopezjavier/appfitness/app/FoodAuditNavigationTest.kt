package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import io.github.lopezjavier.appfitness.feature.food.CreateFoodEffect
import io.github.lopezjavier.appfitness.feature.food.CreateFoodScreen
import io.github.lopezjavier.appfitness.feature.food.CreateFoodViewModel
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.FoodAuditEffect
import io.github.lopezjavier.appfitness.feature.food.FoodAuditEvent
import io.github.lopezjavier.appfitness.feature.food.FoodAuditScreen
import io.github.lopezjavier.appfitness.feature.food.FoodAuditViewModel
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubActions
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-117 navigation: the Preferencias hub row opens the audit, a row opens the food's editor, saving there returns to
 * a list without that food, leaving without saving leaves the list alone, and both screens survive recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // Slots and helpers of the host plus one method per scenario.
class FoodAuditNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun auditRow(id: String) =
        FoodAuditRow(id, "Alimento $id", null, 500.0, 165.0, -335.0, EnergyCheckLevel.ClearlyWrong)

    private fun manual(id: String) {
        foods.known[id] = food(id, "Alimento $id", kcal = 500.0, isManual = true)
    }

    private val profileSlot: ProfileSlot = { modifier, actions ->
        ProfileRootScreen(ProfileRootUiState.Loaded("Ana", null), {}, modifier, actions = actions)
    }

    private val hubSlot: PreferencesSlot = { modifier, onBack, _, _, onOpenFoodAudit ->
        PreferencesHubScreen(
            onBack = onBack,
            actions = PreferencesHubActions(onOpenFoodAudit = onOpenFoodAudit),
            modifier = modifier,
        )
    }

    private val auditSlot: FoodAuditSlot = { modifier, edited, onConsumeEdit, onOpenEditor, onBack ->
        val model: FoodAuditViewModel = viewModel(
            factory = viewModelFactory { initializer { FoodAuditViewModel(foods, connectivity) } },
        )
        LaunchedEffect(edited) {
            if (edited) {
                model.onEvent(FoodAuditEvent.Refresh)
                onConsumeEdit()
            }
        }
        LaunchedEffect(model) {
            model.effects.collect { effect ->
                if (effect is FoodAuditEffect.OpenEditor) onOpenEditor(effect.foodId)
            }
        }
        val state by model.uiState.collectAsStateWithLifecycle()
        FoodAuditScreen(state, connectivity.isOnline, model::onEvent, onBack, modifier)
    }

    /** The real edit form; a saved food leaves the audit list, as it does on the server after a fix or a flag. */
    private val createFoodSlot: CreateFoodSlot = { modifier, _, _, _, editFoodId, onBack, onSaved ->
        val id = requireNotNull(editFoodId)
        val model: CreateFoodViewModel = viewModel(
            key = "edit_$id",
            factory = viewModelFactory {
                initializer { CreateFoodViewModel(null, null, foods, connectivity, createSavedStateHandle(), id) }
            },
        )
        LaunchedEffect(model) {
            model.effects.collect { effect ->
                if (effect is CreateFoodEffect.Saved) {
                    foods.auditResult = Result.success(foods.auditResult.getOrThrow().filter { it.foodId != id })
                    onSaved(effect.foodId)
                }
            }
        }
        val state by model.uiState.collectAsStateWithLifecycle()
        CreateFoodScreen(state, model::onEvent, onBack, modifier)
    }

    private fun showShell() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    preferences = hubSlot,
                    foodAudit = auditSlot,
                    createFood = createFoodSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openAuditFromTheHub() {
        tap("profile_action_0")
        tap("preferences_row_food_audit")
    }

    private fun twoBrokenFoods() {
        manual("a")
        manual("b")
        foods.auditResult = Result.success(listOf(auditRow("a"), auditRow("b")))
    }

    @Test
    fun hubRow_opensTheAudit() {
        twoBrokenFoods()
        showShell()
        tap("nav_tab_profile")

        openAuditFromTheHub()

        rule.onNodeWithTag("food_audit_screen").assertExists()
        rule.onNodeWithTag("food_audit_row_a").assertExists()
        rule.onNodeWithTag("food_audit_row_b").assertExists()
    }

    @Test
    fun aRow_opensTheEditorWithThatFoodsNumbers() {
        twoBrokenFoods()
        showShell()
        tap("nav_tab_profile")
        openAuditFromTheHub()

        tap("food_audit_row_b")

        rule.onNodeWithTag("create_food_screen").assertExists()
        rule.onNodeWithTag("create_food_name_field").assertTextContains("Alimento b")
        rule.onNodeWithTag("create_food_kcal_field").assertTextContains("500")
    }

    @Test
    fun savingAnywayAFailingFood_returnsToAListWithoutIt() {
        twoBrokenFoods()
        showShell()
        tap("nav_tab_profile")
        openAuditFromTheHub()
        tap("food_audit_row_a")
        rule.onNodeWithTag("create_food_name_field").performTextReplacement("Alimento a2")
        tap("create_food_save")
        rule.onNodeWithTag("create_food_energy_warning").assertExists()

        tap("create_food_save_anyway")

        rule.onNodeWithTag("food_audit_screen").assertExists()
        rule.onNodeWithTag("food_audit_row_a").assertDoesNotExist()
        rule.onNodeWithTag("food_audit_row_b").assertExists()
        assertEquals(2, foods.auditCalls)
    }

    @Test
    fun leavingTheEditorWithoutSaving_keepsTheListAsItWas_withoutReloading() {
        twoBrokenFoods()
        showShell()
        tap("nav_tab_profile")
        openAuditFromTheHub()
        tap("food_audit_row_a")

        tap("create_food_back")

        rule.onNodeWithTag("food_audit_row_a").assertExists()
        rule.onNodeWithTag("food_audit_row_b").assertExists()
        assertEquals(1, foods.auditCalls)
        assertTrue(foods.updates.isEmpty())
    }

    @Test
    fun auditAndOpenEditor_surviveRecreation_withTheTypedDraft() {
        twoBrokenFoods()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    preferences = hubSlot,
                    foodAudit = auditSlot,
                    createFood = createFoodSlot,
                )
            }
        }
        rule.waitForIdle()
        tap("nav_tab_profile")
        openAuditFromTheHub()
        tap("food_audit_row_a")
        rule.onNodeWithTag("create_food_name_field").performTextReplacement("Borrador")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("create_food_screen").assertExists()
        rule.onNodeWithTag("create_food_name_field").assertTextContains("Borrador")
        assertTrue(foods.updates.isEmpty())
    }
}
