package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R-16 / R-18: the shared exercise row (thumbnail + name opens the details, optional supporting line). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One behaviour per test.
class ExerciseRowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingTheRow_opensTheExercise() {
        var opened = 0
        rule.setContent {
            AppTheme { ExerciseRow(name = "Press banca", supporting = null, onOpen = { opened++ }, tagPrefix = "row") }
        }

        rule.onNodeWithText("Press banca").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun theRow_showsThePlaceholderThumbnailWhenThereIsNoImage() {
        rule.setContent {
            AppTheme { ExerciseRow(name = "Sentadilla", supporting = null, onOpen = {}, tagPrefix = "row") }
        }

        rule.onNodeWithTag("row_thumbnail_placeholder", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theSupportingLine_isShownUnderTheName() {
        rule.setContent {
            AppTheme { ExerciseRow(name = "Remo", supporting = "3 x 8", onOpen = {}, tagPrefix = "row") }
        }

        rule.onNodeWithText("3 x 8").assertIsDisplayed()
    }

    @Test
    fun theRow_isAtLeast48dpHigh_andCarriesItsTag() {
        rule.setContent { AppTheme { ExerciseRow(name = "Remo", supporting = null, onOpen = {}, tagPrefix = "row") } }

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theTrailingSlot_isRendered() {
        rule.setContent {
            AppTheme {
                ExerciseRow(name = "Remo", supporting = null, onOpen = {
                }, tagPrefix = "row", trailing = { Text("fin") })
            }
        }

        rule.onNodeWithText("fin").assertIsDisplayed()
    }

    @Test
    fun withOnSelect_theRowBodySelects_andTheThumbnailOpensTheDetail() {
        var opened = 0
        var selected = 0
        rule.setContent {
            AppTheme {
                ExerciseRow(
                    name = "Press banca",
                    supporting = null,
                    onOpen = { opened++ },
                    tagPrefix = "row",
                    onSelect = { selected++ },
                )
            }
        }

        rule.onNodeWithText("Press banca").performClick()
        assertEquals(0, opened)
        assertEquals(1, selected)

        rule.onNodeWithTag("row_thumbnail").performClick()
        assertEquals(1, opened)
        assertEquals(1, selected)
    }

    @Test
    fun withOnSelect_theThumbnailTargetIsAtLeast48dp_andDescribesTheExercise() {
        rule.setContent {
            AppTheme {
                ExerciseRow(name = "Remo", supporting = null, onOpen = {}, tagPrefix = "row", onSelect = {})
            }
        }

        rule.onNodeWithTag("row_thumbnail").assertContentDescriptionEquals("Ver detalle de Remo")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("row_thumbnail_placeholder", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun withoutOnSelect_thereIsNoSeparateThumbnailTarget() {
        rule.setContent {
            AppTheme { ExerciseRow(name = "Remo", supporting = null, onOpen = {}, tagPrefix = "row") }
        }

        rule.onNodeWithTag("row_thumbnail").assertDoesNotExist()
    }

    @Test
    fun headlineTrailing_rendersRightAfterTheHeadline_onTheSameLine() {
        rule.setContent {
            AppTheme {
                AppListRow(
                    headline = "Remo",
                    headlineTrailing = { Text("*", Modifier.testTag("mark")) },
                    trailing = { Text("fin", Modifier.testTag("end")) },
                )
            }
        }

        val name = rule.onNodeWithText("Remo").fetchSemanticsNode().boundsInRoot
        val mark = rule.onNodeWithTag("mark").fetchSemanticsNode().boundsInRoot
        val end = rule.onNodeWithTag("end").fetchSemanticsNode().boundsInRoot
        assertTrue(mark.left >= name.right)
        assertTrue(mark.top < name.bottom && mark.bottom > name.top)
        assertTrue(mark.right <= end.left)
    }

    @Test
    fun withoutHeadlineTrailing_theHeadlineIsAloneInItsColumn() {
        rule.setContent { AppTheme { AppListRow(headline = "Remo", supporting = "3 x 8") } }

        rule.onNodeWithText("Remo").assertIsDisplayed()
        rule.onNodeWithText("3 x 8").assertIsDisplayed()
    }

    @Test
    fun aFavouriteRow_keepsTheStarTagAndReadsFavorito() {
        rule.setContent {
            AppTheme {
                ExerciseRow(
                    name = "Remo",
                    supporting = null,
                    onOpen = {},
                    tagPrefix = "row",
                    trailing = { Text("fin") },
                    favorite = true,
                    favoriteTestTag = "row_star",
                )
            }
        }

        rule.onNodeWithTag("row_star", useUnmergedTree = true).assertContentDescriptionEquals("Favorito")
        rule.onNodeWithText("fin").assertIsDisplayed()
        rule.onNodeWithTag("row").assert(
            SemanticsMatcher("row reads Favorito") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Favorito") == true
            },
        )
    }

    @Test
    fun aSelectableRow_byDefault_selectsOnBodyTap_andOpensOnTheThumbnail() {
        var selected = 0
        var opened = 0
        rule.setContent {
            AppTheme {
                ExerciseRow("Remo", null, { opened++ }, "row", onSelect = { selected++ })
            }
        }

        rule.onNodeWithText("Remo").performClick()
        rule.onNodeWithTag("row_thumbnail").performClick()

        assertEquals(1, selected)
        assertEquals(1, opened)
    }

    @Test
    fun aDisabledSelectableRow_doesNotSelect_butTheThumbnailStillOpensTheDetails() {
        var selected = 0
        var opened = 0
        rule.setContent {
            AppTheme {
                ExerciseRow("Remo", "Ya está en esta rutina", {
                    opened++
                }, "row", onSelect = { selected++ }, enabled = false)
            }
        }

        rule.onNodeWithText("Remo").performClick()
        rule.onNodeWithTag("row_thumbnail").performClick()

        assertEquals(0, selected)
        assertEquals(1, opened)
        rule.onNodeWithText("Ya está en esta rutina").assertIsDisplayed()
    }
}
