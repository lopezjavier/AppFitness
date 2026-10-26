package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AppSearchField]: clear button, "Buscar" keyboard action and disabled state (R-26). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppSearchFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private var query by mutableStateOf("")
    private var searches = 0

    private fun show(initial: String = "", enabled: Boolean = true) {
        query = initial
        searches = 0
        rule.setContent {
            AppTheme {
                AppSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Buscar",
                    placeholder = "Nombre",
                    onSearch = { searches++ },
                    enabled = enabled,
                    testTagPrefix = "search",
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun emptyQuery_hasNoClearButton() {
        show()
        rule.onNodeWithTag("search_clear").assertDoesNotExist()
    }

    @Test
    fun clear_appearsWithText_isDescribedAndClearsTheQuery() {
        show("pollo")
        rule.onNodeWithTag("search_clear")
            .assertContentDescriptionEquals("Borrar búsqueda")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        rule.waitForIdle()
        assertEquals("", query)
        rule.onNodeWithTag("search_clear").assertDoesNotExist()
    }

    @Test
    fun disabledField_hidesClear() {
        show("pollo", enabled = false)
        rule.onNodeWithTag("search_clear").assertDoesNotExist()
    }

    @Test
    fun keyboardAction_isSearchAndCallsOnSearch() {
        show("pollo")
        rule.onNodeWithTag("search").assert(
            SemanticsMatcher("ime action is Search") {
                it.config.getOrNull(SemanticsProperties.ImeAction) == ImeAction.Search
            },
        )
        rule.onNodeWithTag("search").performImeAction()
        assertEquals(1, searches)
    }
}
