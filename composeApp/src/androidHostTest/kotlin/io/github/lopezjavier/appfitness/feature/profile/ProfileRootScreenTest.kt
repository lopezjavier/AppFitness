package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppAvatarDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-05 and US-56 on the Perfil root: photo, name, the gear to Preferencias and the statistics rows with values. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ProfileRootScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ProfileRootEvent>()
    private var catalogOpens = 0
    private var preferencesOpens = 0
    private var strengthOpens = 0
    private var diaryHistoryOpens = 0
    private var weightOpens = 0
    private var trainingStatsOpens = 0
    private var aboutOpens = 0
    private var activityOpens = 0
    private var myTrainingOpens = 0

    private fun loaded(
        name: String? = "Ana María",
        avatarUrl: String? = null,
        weightKg: Double? = 79.4,
        strengthRecordKg: Double? = 113.5,
        avgKcal: Int? = 1950,
    ) = ProfileRootUiState.Loaded(name, avatarUrl, weightKg, strengthRecordKg, avgKcal)

    private fun show(state: ProfileRootUiState = loaded(), withCatalog: Boolean = false) {
        events.clear()
        catalogOpens = 0
        preferencesOpens = 0
        strengthOpens = 0
        diaryHistoryOpens = 0
        weightOpens = 0
        trainingStatsOpens = 0
        aboutOpens = 0
        activityOpens = 0
        myTrainingOpens = 0
        rule.setContent {
            AppTheme {
                ProfileRootScreen(
                    state = state,
                    onEvent = { events += it },
                    actions = ProfileRootActions(
                        onOpenPreferences = { preferencesOpens++ },
                        onOpenCatalog = if (withCatalog) ({ catalogOpens++ }) else null,
                        onOpenStrength = { strengthOpens++ },
                        onOpenWeight = { weightOpens++ },
                        onOpenDiaryHistory = { diaryHistoryOpens++ },
                        onOpenTrainingStats = { trainingStatsOpens++ },
                        onOpenAbout = { aboutOpens++ },
                        onOpenActivity = { activityOpens++ },
                        onOpenMyTraining = { myTrainingOpens++ },
                    ),
                )
            }
        }
        rule.waitForIdle()
    }

    // -- header ---------------------------------------------------------------------------------------------------

    @Test
    fun header_showsTheNameAndThePlaceholderWhenThereIsNoPhoto() {
        show(loaded(avatarUrl = null))
        rule.onNodeWithTag("profile_root_name").assertTextEquals("Ana María")
        rule.onNodeWithTag("profile_root_avatar_placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun avatar_isTheRootSize() {
        show()
        val bounds = rule.onNodeWithTag("profile_root_avatar").getUnclippedBoundsInRoot()
        assertEquals(AppAvatarDefaults.RootSize, bounds.width)
        assertEquals(AppAvatarDefaults.RootSize, bounds.height)
    }

    @Test
    fun aMissingName_showsNoNameLine() {
        show(loaded(name = null))
        rule.onNodeWithTag("profile_root_name").assertDoesNotExist()
        rule.onNodeWithTag("profile_root_avatar").assertExists()
    }

    // -- Mi entrenamiento -----------------------------------------------------------------------------------------

    @Test
    fun miEntrenamientoRow_isInThePerfilRoot_andOpensTheEditor() {
        show()
        rule.onNodeWithTag("profile_my_training_row").assertTextEquals("Mi entrenamiento").performClick()
        assertEquals(1, myTrainingOpens)
    }

    @Test
    fun miEntrenamientoRow_showsTheChosenIntention() {
        show(ProfileRootUiState.Loaded("Ana", null, intention = TrainingIntention.Strength))
        rule.onNodeWithTag("profile_my_training_row").assertTextContains("Ganar fuerza", substring = true)
    }

    @Test
    fun miEntrenamientoRow_withoutAnIntention_showsNoValue() {
        show()
        rule.onNodeWithTag("profile_my_training_row").assertTextEquals("Mi entrenamiento")
    }

    // -- gear -----------------------------------------------------------------------------------------------------

    @Test
    fun gear_isNamedPreferencias_meetsTheTouchTarget_andOpensPreferences() {
        show()
        rule.onNodeWithTag("profile_title").assertTextEquals("Perfil")
        rule.onNodeWithTag("profile_action_0")
            .assertContentDescriptionEquals("Preferencias")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, preferencesOpens)
    }

    @Test
    fun root_hasNoBackArrow_noPreferencesButton_noSignOutAndNoDelete() {
        show()
        rule.onNodeWithTag("profile_back").assertDoesNotExist()
        listOf("profile_root_preferences_button", "profile_root_sign_out", "profile_root_delete_account").forEach {
            rule.onNodeWithTag(it).assertDoesNotExist()
        }
        rule.onNodeWithTag("profile_root_delete_protected_note").assertDoesNotExist()
    }

    // -- statistics rows ------------------------------------------------------------------------------------------

    @Test
    fun statisticsRows_showTheirValues_asOneNodeLabelAndValue() {
        show()
        rule.onNodeWithTag("profile_statistics_heading").assertTextEquals("Estadísticas")
        rule.onNodeWithTag("profile_weight_row").assertTextEquals("Peso", "79,4 kg")
        rule.onNodeWithTag("profile_strength_row").assertTextEquals("Fuerza", "Récord 1RM 113,5 kg")
        rule.onNodeWithTag("profile_diary_history_row").assertTextEquals("Calorías", "media 1.950 kcal")
        rule.onNodeWithTag("profile_training_stats_row").assertTextEquals("Entrenamiento")
        rule.onNodeWithTag("profile_activity_row").assertTextEquals("Actividad")
    }

    @Test
    fun aStatisticWithoutValue_showsOnlyItsLabel() {
        show(loaded(weightKg = null, strengthRecordKg = null, avgKcal = null))
        rule.onNodeWithTag("profile_weight_row").assertTextEquals("Peso")
        rule.onNodeWithTag("profile_strength_row").assertTextEquals("Fuerza")
        rule.onNodeWithTag("profile_diary_history_row").assertTextEquals("Calorías")
    }

    @Test
    fun everyRow_meetsTheTouchTarget_andOpensItsDestination() {
        show()
        listOf("weight", "strength", "diary_history", "training_stats", "activity").forEach {
            rule.onNodeWithTag("profile_${it}_row").assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(
            listOf(1, 1, 1, 1, 1),
            listOf(weightOpens, strengthOpens, diaryHistoryOpens, trainingStatsOpens, activityOpens),
        )
    }

    @Test
    fun aboutRow_isPresentAndOpensAbout() {
        show()

        rule.onNodeWithTag("profile_about_row").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, aboutOpens)
    }

    // -- catalog (local flavor) -----------------------------------------------------------------------------------

    @Test
    fun catalogButton_appearsOnlyWhereItIsAvailable() {
        show(withCatalog = false)
        rule.onNodeWithTag("profile_root_catalog_button").assertDoesNotExist()
    }

    @Test
    fun catalogButton_opensTheCatalog() {
        show(withCatalog = true)
        rule.onNodeWithTag("profile_root_catalog_button").assertIsDisplayed().performClick()
        assertEquals(1, catalogOpens)
    }

    // -- states ---------------------------------------------------------------------------------------------------

    @Test
    fun loading_showsTheSkeletonWithoutRows() {
        rule.mainClock.autoAdvance = false
        show(ProfileRootUiState.Loading)
        rule.onNodeWithTag("profile_root_loading").assertExists()
        rule.onNodeWithTag("profile_weight_row").assertDoesNotExist()
    }

    @Test
    fun loadFailure_showsRetry_andRetryIsSent() {
        show(ProfileRootUiState.LoadFailed)
        rule.onNodeWithTag("profile_root_error_action").assertTextEquals("Reintentar").performClick()
        assertEquals(listOf<ProfileRootEvent>(ProfileRootEvent.Retry), events)
    }

    // -- tablet ---------------------------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_capsTheContentWidth() {
        show()
        val row = rule.onNodeWithTag("profile_weight_row").getUnclippedBoundsInRoot()
        assertTrue("row width ${row.width}", row.width <= WindowWidthClassDefaults.FormContentMaxWidth)
    }
}
