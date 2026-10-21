package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Day = LocalDate(2026, 10, 19)

/** Diary -> food search -> add-food sheet -> back to the Diary (US-17). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class FoodNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private var dismissed = 0

    private val diarySlot: DiarySlot = { modifier, _, onAddFood, _, _ ->
        Column(modifier.fillMaxSize().testTag("diary_screen")) {
            Box(Modifier.size(48.dp).testTag("diary_add_food").clickable { onAddFood(null, Day) })
            Box(Modifier.size(48.dp).testTag("diary_add_dinner").clickable { onAddFood("dinner", Day) })
        }
    }
    private val searchSlot: FoodSearchSlot = { modifier, _, _, onOpenFood, onCreateFood, _, _ ->
        Column(modifier.fillMaxSize().testTag("food_search_screen")) {
            Box(Modifier.size(48.dp).testTag("search_pick").clickable { onOpenFood("chicken") })
            Box(Modifier.size(48.dp).testTag("search_create").clickable { onCreateFood("8410", "Tortilla") })
        }
    }
    private val createFoodSlot: CreateFoodSlot = { modifier, barcode, name, onSaved ->
        Column(modifier.fillMaxSize().testTag("create_food_screen")) {
            Text("$barcode|$name", Modifier.testTag("create_args"))
            Box(Modifier.size(48.dp).testTag("create_save").clickable { onSaved("new-1") })
        }
    }
    private val addFoodSlot: AddFoodSlot = { foodId, slotId, date, onAdded, onDismiss ->
        Column(Modifier.testTag("add_food_sheet")) {
            Text("$foodId|$slotId|$date", Modifier.testTag("sheet_args"))
            Box(Modifier.size(48.dp).testTag("sheet_add").clickable(onClick = onAdded))
            Box(
                Modifier.size(48.dp).testTag("sheet_dismiss").clickable {
                    dismissed++
                    onDismiss()
                },
            )
        }
    }

    private fun showShell() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                AppScaffold(
                    navController = nav,
                    diary = diarySlot,
                    foodSearch = searchSlot,
                    addFood = addFoodSlot,
                    createFood = createFoodSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun diaryLevelAddAction_opensTheSearchWithoutAMeal_andHidesTheBottomBar() {
        showShell()

        click("diary_add_food")

        rule.onNodeWithTag("food_search_screen").assertExists()
        rule.onNodeWithTag("food_search_back").assertExists()
        rule.onNodeWithTag("nav_tab_diary").assertDoesNotExist()
    }

    @Test
    fun pickingAFood_opensTheSheetWithTheMealAndDay_andAddingReturnsToTheDiary() {
        showShell()
        click("diary_add_dinner")

        click("search_pick")
        rule.onNodeWithTag("sheet_args").assertTextEquals("chicken|dinner|2026-10-19")
        click("sheet_add")

        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithTag("food_search_screen").assertDoesNotExist()
        assertTrue(nav.currentDestination?.hasRoute(DiaryHome::class) == true)
    }

    @Test
    fun dismissingTheSheet_leavesTheSearchOpen() {
        showShell()
        click("diary_add_food")
        click("search_pick")

        click("sheet_dismiss")

        assertEquals(1, dismissed)
        rule.onNodeWithTag("sheet_args").assertDoesNotExist()
        rule.onNodeWithTag("food_search_screen").assertExists()
    }

    @Test
    fun backFromTheSearch_returnsToTheDiary() {
        showShell()
        click("diary_add_food")

        click("food_search_back")

        rule.onNodeWithTag("diary_screen").assertExists()
    }

    @Test
    fun creatingAFood_opensTheFormWithTheArgs_thenTheSheetOfTheNewFood_andAddingReturnsToTheDiary() {
        showShell()
        click("diary_add_dinner")

        click("search_create")
        rule.onNodeWithTag("create_args").assertTextEquals("8410|Tortilla")
        rule.onNodeWithTag("nav_tab_diary").assertDoesNotExist()
        click("create_save")

        rule.onNodeWithTag("sheet_args").assertTextEquals("new-1|dinner|2026-10-19")
        rule.onNodeWithTag("create_food_screen").assertDoesNotExist()
        click("sheet_add")
        rule.onNodeWithTag("diary_screen").assertExists()
    }

    @Test
    fun dismissingTheSheetOfACreatedFood_returnsToTheSearchNotTheForm() {
        showShell()
        click("diary_add_food")
        click("search_create")
        click("create_save")

        click("sheet_dismiss")

        rule.onNodeWithTag("food_search_screen").assertExists()
        rule.onNodeWithTag("create_food_screen").assertDoesNotExist()
    }
}
