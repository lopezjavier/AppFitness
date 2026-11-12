package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.app.RootError
import io.github.lopezjavier.appfitness.app.RootLoading
import io.github.lopezjavier.appfitness.app.TopLevelTab
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.feature.auth.WelcomeError
import io.github.lopezjavier.appfitness.feature.auth.WelcomeScreen
import io.github.lopezjavier.appfitness.feature.auth.WelcomeUiState
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterScreen
import io.github.lopezjavier.appfitness.feature.notifications.NotificationCenterUiState
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingPhase
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingSaveError
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingScreen
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingStep
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingUiState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val INICIO = "Inicio"
private const val DIARIO = "Diario"
private const val SHARED = "Compartido"

private fun onboarding(step: OnboardingStep, block: (OnboardingUiState) -> OnboardingUiState = { it }) = block(
    OnboardingUiState(
        step = step,
        birthDate = LocalDate(1995, 6, 15),
        phase = OnboardingPhase.Content,
        today = GalleryFixtures.today,
    ),
)

@Suppress("LongMethod") // A list of fixtures.
private fun inicioScreens(): List<GalleryEntry> {
    fun welcome(state: String, ui: WelcomeUiState, testLogin: Boolean = false) =
        GalleryEntry(INICIO, "Welcome", state, covers = setOf("RootRoute.Welcome")) {
            WelcomeScreen(ui, showTestLogin = testLogin, onEvent = {})
        }

    fun step(name: String, state: String, ui: OnboardingUiState) =
        GalleryEntry(INICIO, name, state, covers = setOf("RootRoute.Onboarding")) {
            OnboardingScreen(ui, onEvent = {})
        }
    return listOf(
        GalleryEntry(INICIO, "Loading", "default", covers = setOf("RootRoute.Loading")) { RootLoading() },
        welcome("default", WelcomeUiState()),
        welcome("with Test login", WelcomeUiState(), testLogin = true),
        welcome("signing in", WelcomeUiState(inFlight = true)),
        welcome("error no network", WelcomeUiState(error = WelcomeError.NoNetwork)),
        welcome("error generic", WelcomeUiState(error = WelcomeError.Generic)),
        step("Onboarding about you", "empty", onboarding(OnboardingStep.AboutYou)),
        step("Onboarding about you", "filled", onboarding(OnboardingStep.AboutYou) { it.copy(sex = Sex.Male) }),
        step(
            "Onboarding about you",
            "asks for the name",
            onboarding(OnboardingStep.AboutYou) { it.copy(needsName = true, sex = Sex.Female) },
        ),
        step("Onboarding measures", "default", onboarding(OnboardingStep.Measures)),
        step("Onboarding activity", "empty", onboarding(OnboardingStep.Activity)),
        step(
            "Onboarding activity",
            "filled",
            onboarding(OnboardingStep.Activity) { it.copy(activity = ActivityLevel.Moderate) },
        ),
        step("Onboarding goals", "default", onboarding(OnboardingStep.Goals)),
        step(
            "Onboarding goals",
            "save error",
            onboarding(OnboardingStep.Goals) { it.copy(saveError = OnboardingSaveError.Other) },
        ),
        step(
            "Onboarding goals",
            "offline",
            onboarding(OnboardingStep.Goals) { it.copy(isOffline = true, saveError = OnboardingSaveError.Network) },
        ),
        step(
            "Onboarding goals",
            "saving",
            onboarding(OnboardingStep.Goals) {
                it.copy(phase = OnboardingPhase.Saving)
            },
        ),
        step(
            "Onboarding goals",
            "completed",
            onboarding(OnboardingStep.Goals) { it.copy(phase = OnboardingPhase.Completed) },
        ),
        step(
            "Onboarding",
            "loading",
            onboarding(OnboardingStep.AboutYou) { it.copy(phase = OnboardingPhase.Loading) },
        ),
        step(
            "Onboarding",
            "load error",
            onboarding(OnboardingStep.AboutYou) { it.copy(phase = OnboardingPhase.LoadError) },
        ),
        GalleryEntry(INICIO, "Profile error", "default", covers = setOf("RootRoute.ProfileError")) {
            RootError(onRetry = {})
        },
    )
}

internal fun notificationCenterEntries(): List<GalleryEntry> {
    val today = GalleryFixtures.today
    val weight = Reminder(ReminderKind.Weight, LocalDate(2026, 10, 17), days = 4)
    val circumference = Reminder(ReminderKind.Circumference, LocalDate(2026, 10, 7), days = 14)
    val summary = Reminder(ReminderKind.WeekSummary, LocalDate(2026, 10, 18), days = 0, week = 3)
    val blockEnd = Reminder(ReminderKind.BlockEnd, LocalDate(2026, 10, 19), days = 2)
    val checkInDue = Reminder(ReminderKind.CheckIn, LocalDate(2026, 10, 20), days = 0)
    val window = CheckInDay(LocalDate(2026, 10, 20), LocalDate(2026, 10, 14), answer = null)

    fun entry(state: String, ui: NotificationCenterUiState) = GalleryEntry(
        DIARIO,
        "Centro de avisos",
        state,
        covers = setOf("NotificationCenterRoute"),
        alsoOpenedFrom = listOf("Perfil"),
    ) {
        NotificationCenterScreen(ui, onEvent = {}, onOpenReminder = {}, onBack = {}, onOpenReports = {})
    }
    fun content(vararg reminders: Reminder, checkIn: CheckInDay? = null) =
        NotificationCenterUiState.Content(reminders.toList(), today, checkIn)

    fun answered(energy: Int) = window.copy(answer = CheckIn(LocalDate(2026, 10, 14), energy = energy))

    fun weightDays(days: Int) = Reminder(ReminderKind.Weight, today.minus(days, DateTimeUnit.DAY), days)

    fun circumferenceDays(days: Int) = Reminder(ReminderKind.Circumference, today.minus(days, DateTimeUnit.DAY), days)
    return listOf(
        entry("loading", NotificationCenterUiState.Loading),
        entry("empty (Todo al día)", content()),
        entry("all notices", content(weight, circumference, summary, blockEnd, checkInDue, checkIn = window)),
        entry("error", NotificationCenterUiState.Error),
        entry("Weight alone", content(weight)),
        entry("Weight, due today", content(weightDays(0))),
        entry("Weight, 1 day", content(weightDays(1))),
        entry("Weight, 4 days", content(weight)),
        entry("Weight, 30 days", content(weightDays(30))),
        entry("Circumference alone", content(circumference)),
        entry("Circumference, due today (0 weeks)", content(circumferenceDays(0))),
        entry("Circumference, 1 week", content(circumferenceDays(7))),
        entry("Circumference, 2 weeks", content(circumference)),
        entry("Circumference, 6 weeks", content(circumferenceDays(42))),
        entry("CheckIn alone, with window", content(checkInDue, checkIn = window)),
        entry("CheckIn alone, without window", content(checkInDue)),
        entry("WeekSummary alone, with week number", content(summary)),
        entry("WeekSummary alone, without week number", content(summary.copy(week = null))),
        entry("BlockEnd alone", content(blockEnd)),
        entry("BlockEnd, due today", content(blockEnd.copy(dueDate = today, days = 0))),
        entry("check-in answered, energy 1", content(weight, checkIn = answered(1))),
        entry("check-in answered, energy 3", content(checkIn = answered(3))),
        entry("check-in answered, energy 5", content(summary, checkIn = answered(5))),
    )
}

private fun shared(): List<GalleryEntry> {
    fun items() = TopLevelTab.entries.map { AppNavigationItem(it.label, it.icon, it.testTag, it.selectedIcon) }
    fun bar(tab: TopLevelTab) = GalleryEntry(SHARED, "Bottom bar", "${tab.name} selected") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AppNavigation(items(), tab.ordinal, onItemSelect = {}, modifier = Modifier.fillMaxWidth())
        }
    }
    return TopLevelTab.entries.map(::bar) +
        listOf(
            GalleryEntry(SHARED, "Offline banner", "default") {
                Column(Modifier.fillMaxSize()) { OfflineBanner(Modifier.fillMaxWidth()) }
            },
        )
}

internal fun inicioEntries(): List<GalleryEntry> = inicioScreens() + shared()

/** Renders the Inicio, Centro de avisos and Compartido entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class InicioGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersEveryEntry() = rule.renderGallery(inicioEntries().filter { !it.wide && !it.tall })

    @Test
    fun rendersCentroDeAvisos() = rule.renderGallery(notificationCenterEntries())

    @Test
    fun writesTheIndexManifest() = writeGalleryManifest()
}
