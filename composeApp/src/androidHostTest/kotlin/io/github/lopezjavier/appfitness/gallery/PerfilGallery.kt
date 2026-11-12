package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPlace
import io.github.lopezjavier.appfitness.feature.about.AboutScreen
import io.github.lopezjavier.appfitness.feature.about.AboutUiState
import io.github.lopezjavier.appfitness.feature.profile.AccountUiState
import io.github.lopezjavier.appfitness.feature.profile.FoodPreferencesForm
import io.github.lopezjavier.appfitness.feature.profile.PreferencesForm
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubActions
import io.github.lopezjavier.appfitness.feature.profile.PreferencesHubScreen
import io.github.lopezjavier.appfitness.feature.profile.PreferencesSection
import io.github.lopezjavier.appfitness.feature.profile.PreferencesSectionScreen
import io.github.lopezjavier.appfitness.feature.profile.PreferencesUiState
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileRootUiState
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal const val PERFIL_AREA = "Perfil"

private val BaseForm = PreferencesForm(
    name = GalleryFixtures.USER_NAME,
    sex = Sex.Male,
    birthDate = LocalDate(1995, 6, 15),
    heightCm = 180,
    weightKg = 82.4,
    activity = ActivityLevel.Moderate,
    strategy = GoalStrategy.Maintenance,
    adjustment = 0,
    split = MacroSplit.DEFAULT,
    intention = TrainingIntention.Muscle,
    place = TrainingPlace.Gym,
    equipment = setOf(Equipment.Barbell, Equipment.Dumbbell),
    daysPerWeek = 4,
    sessionMinutes = 60,
)

private fun profileRoot(): List<GalleryEntry> {
    fun entry(state: String, ui: ProfileRootUiState, wide: Boolean = false) =
        GalleryEntry(PERFIL_AREA, "Perfil", state, covers = setOf("ProfileHome"), wide = wide) {
            ProfileRootScreen(ui, onEvent = {})
        }
    val loaded = ProfileRootUiState.Loaded(GalleryFixtures.USER_NAME, null, 82.4, 113.5, 2150, TrainingIntention.Muscle)
    return listOf(
        entry("loading", ProfileRootUiState.Loading),
        entry("load failed", ProfileRootUiState.LoadFailed),
        entry("data", loaded),
        entry("no highlights yet", ProfileRootUiState.Loaded(GalleryFixtures.USER_NAME, null)),
        entry("no name", ProfileRootUiState.Loaded(null, null, 82.4)),
        entry("tablet width", loaded, wide = true),
    )
}

private fun loadedPreferences(
    section: PreferencesSection,
    form: PreferencesForm = BaseForm,
    online: Boolean = true,
    saving: Boolean = false,
    healthConnect: Boolean = false,
) = PreferencesUiState.Loaded(
    form = form,
    saved = BaseForm,
    section = section,
    today = GalleryFixtures.today,
    isSaving = saving,
    isOnline = online,
    weightFromHealthConnect = healthConnect,
)

@Suppress("LongMethod") // A list of fixtures.
private fun preferences(): List<GalleryEntry> {
    fun hub(
        state: String,
        account: AccountUiState,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        kind: GalleryKind = GalleryKind.Screen,
        vararg covers: String,
    ) = GalleryEntry(
        PERFIL_AREA,
        "Preferencias",
        state,
        kind,
        covers = setOf("PreferencesRoute") + covers,
        interact = interact,
    ) {
        PreferencesHubScreen(onBack = {}, actions = PreferencesHubActions(), accountState = account)
    }

    fun section(
        title: String,
        sec: PreferencesSection,
        state: String,
        ui: PreferencesUiState,
        kind: GalleryKind = GalleryKind.Screen,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        fontScale: Float = 1f,
        vararg covers: String,
    ) = GalleryEntry(
        PERFIL_AREA,
        title,
        state,
        kind,
        covers = setOf("PreferencesSectionRoute") + covers,
        interact = interact,
        fontScale = fontScale,
    ) { PreferencesSectionScreen(sec, ui, onEvent = {}, onClose = {}) }

    fun scrollTap(tag: String): ComposeContentTestRule.() -> Unit = {
        onNodeWithTag(tag).performScrollTo().performClick()
    }
    val dirty = BaseForm.copy(name = "Alex Nuevo")
    val personal = PreferencesSection.PersonalData
    val unprotected = AccountUiState(protection = AccountProtection.Unprotected)
    return listOf(
        hub("default", AccountUiState()),
        hub("account can be deleted", unprotected),
        hub("offline", AccountUiState(isOnline = false)),
        hub(
            "sign out dialog",
            AccountUiState(),
            scrollTap("preferences_sign_out"),
            GalleryKind.Dialog,
            "SignOutDialog",
        ),
        hub(
            "delete account dialog",
            unprotected,
            scrollTap("preferences_delete_account"),
            GalleryKind.Dialog,
            "DeleteAccountDialog",
        ),
        section("Datos personales", personal, "loading", PreferencesUiState.Loading),
        section("Datos personales", personal, "load failed", PreferencesUiState.LoadFailed),
        section("Datos personales", personal, "unchanged (Guardar disabled)", loadedPreferences(personal)),
        section("Datos personales", personal, "changed (Guardar enabled)", loadedPreferences(personal, dirty)),
        section("Datos personales", personal, "blank name", loadedPreferences(personal, BaseForm.copy(name = ""))),
        section(
            "Datos personales",
            personal,
            "weight from health connect (read-only)",
            loadedPreferences(personal, healthConnect = true),
        ),
        section("Datos personales", personal, "offline", loadedPreferences(personal, dirty, online = false)),
        section("Datos personales", personal, "saving", loadedPreferences(personal, dirty, saving = true)),
        section(
            "Datos personales",
            personal,
            "activity sheet",
            loadedPreferences(personal),
            GalleryKind.Sheet,
            scrollTap("preferences_activity_row"),
            covers = arrayOf("PickerSheet"),
        ),
        section("Datos personales", personal, "large font (1.3)", loadedPreferences(personal, dirty), fontScale = 1.3f),
        section(
            "Objetivo calórico",
            PreferencesSection.EnergyGoal,
            "unchanged",
            loadedPreferences(PreferencesSection.EnergyGoal),
        ),
        section(
            "Objetivo calórico",
            PreferencesSection.EnergyGoal,
            "changed",
            loadedPreferences(
                PreferencesSection.EnergyGoal,
                BaseForm.copy(strategy = GoalStrategy.Deficit, adjustment = -300),
            ),
        ),
        section("Macros", PreferencesSection.Macros, "unchanged", loadedPreferences(PreferencesSection.Macros)),
        section(
            "Macros",
            PreferencesSection.Macros,
            "changed",
            loadedPreferences(PreferencesSection.Macros, BaseForm.copy(split = MacroSplit(35, 42, 23))),
        ),
        section("Cocina", PreferencesSection.Cooking, "unchanged", loadedPreferences(PreferencesSection.Cooking)),
        section(
            "Cocina",
            PreferencesSection.Cooking,
            "validation error",
            loadedPreferences(
                PreferencesSection.Cooking,
                BaseForm.copy(foodPreferences = FoodPreferencesForm(griddleText = "")),
            ),
        ),
        section(
            "Entrenamiento",
            PreferencesSection.Training,
            "unchanged",
            loadedPreferences(PreferencesSection.Training),
        ),
        section(
            "Entrenamiento",
            PreferencesSection.Training,
            "changed",
            loadedPreferences(PreferencesSection.Training, BaseForm.copy(warmUpIncluded = true)),
        ),
        section("Avisos", PreferencesSection.Reminders, "unchanged", loadedPreferences(PreferencesSection.Reminders)),
        section(
            "Avisos",
            PreferencesSection.Reminders,
            "changed",
            loadedPreferences(
                PreferencesSection.Reminders,
                BaseForm.copy(weightReminder = false, blockEndReminder = false),
            ),
        ),
        section(
            "Avisos",
            PreferencesSection.Reminders,
            "check-in day sheet",
            loadedPreferences(PreferencesSection.Reminders),
            GalleryKind.Sheet,
            scrollTap("preferences_checkin_weekday_row"),
            covers = arrayOf("WeekdayPicker"),
        ),
        section(
            "Mi entrenamiento",
            PreferencesSection.MyTraining,
            "filled",
            loadedPreferences(PreferencesSection.MyTraining),
        ),
        section(
            "Mi entrenamiento",
            PreferencesSection.MyTraining,
            "empty",
            loadedPreferences(
                PreferencesSection.MyTraining,
                BaseForm.copy(
                    intention = null,
                    place = null,
                    equipment = emptySet(),
                    daysPerWeek = null,
                    sessionMinutes = null,
                ),
            ),
        ),
        section(
            "Mi entrenamiento",
            PreferencesSection.MyTraining,
            "intention sheet",
            loadedPreferences(PreferencesSection.MyTraining),
            GalleryKind.Sheet,
            scrollTap("preferences_intention_field"),
            covers = arrayOf("IntentionPicker"),
        ),
    )
}

private fun about(): List<GalleryEntry> = listOf(
    GalleryEntry(PERFIL_AREA, "Acerca de", "default", covers = setOf("AboutRoute")) {
        AboutScreen(AboutUiState(BuildInfo("AppFitness", "1.0", 7, "prod")), onBack = {}, onOpenLicenses = {})
    },
)

internal fun perfilEntries(): List<GalleryEntry> =
    profileRoot() + preferences() + about() + perfilMealsEntries() + perfilWeightEntries() +
        perfilStatsEntries()

/** Renders the Perfil entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class PerfilGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersProfileAndPreferences() = rule.renderGallery(
        (profileRoot() + preferences() + about()).filter {
            !it.wide
        },
    )

    @Test
    fun rendersMeals() = rule.renderGallery(perfilMealsEntries().filter { !it.wide })

    @Test
    fun rendersWeight() = rule.renderGallery(perfilWeightEntries().filter { !it.wide })

    @Test
    fun rendersStatistics() = rule.renderGallery(perfilStatsEntries().filter { !it.wide })
}
