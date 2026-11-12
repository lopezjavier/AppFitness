package io.github.lopezjavier.appfitness.gallery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.PeriodTraining
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.model.WeeklySummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyWeightTrend
import io.github.lopezjavier.appfitness.feature.checkin.CheckInScreen
import io.github.lopezjavier.appfitness.feature.checkin.CheckInSummaryState
import io.github.lopezjavier.appfitness.feature.checkin.CheckInUiState
import io.github.lopezjavier.appfitness.feature.diary.DiaryContent
import io.github.lopezjavier.appfitness.feature.diary.DiaryGoalsState
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryUiState
import io.github.lopezjavier.appfitness.feature.diary.MealSectionUi
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal const val DIARIO_AREA = "Diario"

internal val GallerySlots = listOf(
    MealSlot("breakfast", MealCode.Breakfast, "Desayuno", 0),
    MealSlot("lunch", MealCode.Lunch, "Comida", 1),
    MealSlot("snack", MealCode.Snack, "Merienda", 2),
    MealSlot("dinner", MealCode.Dinner, "Cena", 3),
)

internal fun galleryEntryRow(id: String, slotId: String, name: String, kcal: Double, amount: Double = 100.0) =
    FoodLogEntry(id, GalleryFixtures.today, slotId, "f-$id", name, amount, null, "g", kcal, 12.0, 20.0, 5.0)

private fun section(slot: MealSlot, entries: List<FoodLogEntry>, remaining: Int?) = MealSectionUi(
    slot = slot,
    entries = entries,
    totals = MealTotals(entries.sumOf { it.kcal }, entries.sumOf { it.proteinG }, entries.sumOf { it.carbsG }, 0.0),
    isEmpty = entries.isEmpty(),
    showRemainingHint = entries.isEmpty() && remaining != null && remaining > 0,
    remainingKcal = remaining,
)

private fun diaryContent(
    entries: List<FoodLogEntry> = emptyList(),
    totals: MealTotals = MealTotals(0.0, 0.0, 0.0, 0.0),
    goals: DiaryGoalsState = DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = 2400),
    date: LocalDate = GalleryFixtures.today,
    isOffline: Boolean = false,
    defaultsFailed: Boolean = false,
    label: DateLabel = DateLabel.Today,
) = DiaryUiState.Content(
    DiaryContent(
        date = date,
        today = GalleryFixtures.today,
        label = label,
        canGoNext = date != GalleryFixtures.today,
        goals = goals,
        meals = GallerySlots.map { slot ->
            section(slot, entries.filter { it.mealSlotId == slot.id }, (goals as? DiaryGoalsState.Set)?.remainingKcal)
        },
        dayTotals = totals,
        collapsedSlotIds = emptySet(),
        isOffline = isOffline,
        defaultsFailed = defaultsFailed,
        isToday = date == GalleryFixtures.today,
        pickerMarkedDays = setOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 20)),
    ),
)

private val DayEntries = listOf(
    galleryEntryRow("1", "breakfast", "Avena con leche", 380.0),
    galleryEntryRow("2", "breakfast", "Plátano", 105.0),
    galleryEntryRow("3", "lunch", "Pechuga de pollo", 330.0, 200.0),
    galleryEntryRow("4", "lunch", "Arroz integral", 220.0, 180.0),
)

private fun diaryScreens(): List<GalleryEntry> {
    fun home(
        state: String,
        ui: DiaryUiState,
        vararg covers: String,
        onBack: (() -> Unit)? = null,
        wide: Boolean = false,
    ) = GalleryEntry(
        DIARIO_AREA,
        if (onBack == null) "Diario" else "Diario de un día pasado",
        state,
        covers = setOf(if (onBack == null) "DiaryHome" else "DiaryDayRoute") + covers,
        wide = wide,
    ) { DiaryScreen(ui, onEvent = {}, onBack = onBack) }

    val withEntries = diaryContent(DayEntries, MealTotals(1035.0, 48.0, 80.0, 20.0), remaining())
    val past = LocalDate(2026, 10, 19)
    return listOf(
        home("loading", DiaryUiState.Loading),
        home("error", DiaryUiState.Error),
        home("empty day", diaryContent()),
        home("day with entries", withEntries),
        home(
            "over target",
            diaryContent(
                DayEntries,
                MealTotals(2650.0, 150.0, 300.0, 90.0),
                DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = -250),
            ),
        ),
        home("no goals set", diaryContent(DayEntries, MealTotals(1035.0, 48.0, 80.0, 20.0), DiaryGoalsState.NotSet)),
        home("offline", diaryContent(DayEntries, MealTotals(1035.0, 48.0, 80.0, 20.0), remaining(), isOffline = true)),
        home("defaults failed", diaryContent(defaultsFailed = true)),
        GalleryEntry(DIARIO_AREA, "Diario", "bell with notices", covers = setOf("DiaryHome")) {
            CompositionLocalProvider(LocalNotificationsEntry provides NotificationsEntry(3) {}) {
                DiaryScreen(withEntries, onEvent = {})
            }
        },
        GalleryEntry(
            DIARIO_AREA,
            "Diario",
            "day picker sheet",
            GalleryKind.Sheet,
            covers = setOf("DiaryHome", "DiaryDay"),
            interact = { onNodeWithTag("diary_day_picker_open").performClick() },
        ) { DiaryScreen(withEntries, onEvent = {}) },
        home("tablet width", withEntries, wide = true),
        home(
            "past day",
            diaryContent(
                DayEntries,
                MealTotals(1035.0, 48.0, 80.0, 20.0),
                remaining(),
                date = past,
                label = DateLabel.Yesterday,
            ),
            onBack = {},
        ),
        home("past day empty", diaryContent(date = past, label = DateLabel.Yesterday), onBack = {}),
    )
}

private fun remaining() = DiaryGoalsState.Set(DiaryGoals(2400, 180, 270, 67), remainingKcal = 1365)

private fun checkIns(): List<GalleryEntry> {
    val sunday = LocalDate(2026, 10, 18)
    val monday = LocalDate(2026, 10, 12)
    val summary = CheckInSummaryState.Loaded(
        WeeklySummary(
            PeriodTraining(TrainingSummary(4, 40, 12_400.0, 0), TrainingSummary(3, 30, 11_600.0, 0)),
            listOf(WeekRecord("bench", PersonalRecordType.HeaviestLift, 100.0, LocalDate(2026, 10, 16))),
            WeeklyWeightTrend.Delta(-0.4),
        ),
    )

    fun ready(
        energy: Int? = null,
        answer: CheckIn? = null,
        summaryState: CheckInSummaryState = summary,
        offline: Boolean = false,
    ) = CheckInUiState.Ready(
        CheckInDay(sunday, monday, answer),
        energy,
        summaryState,
        isOffline = offline,
        exerciseNames = mapOf("bench" to "Press banca"),
    )

    fun entry(state: String, ui: CheckInUiState) =
        GalleryEntry(DIARIO_AREA, "Check-in semanal", state, covers = setOf("CheckInRoute")) {
            CheckInScreen(ui, onEvent = {}, onClose = {})
        }
    return listOf(
        entry("loading", CheckInUiState.Loading),
        entry("load failed", CheckInUiState.LoadFailed),
        entry("new, no energy chosen (Guardar disabled)", ready()),
        entry("new, energy chosen (Guardar enabled)", ready(energy = 4)),
        entry("already answered", ready(energy = 3, answer = CheckIn(monday, 3))),
        entry("summary loading", ready(summaryState = CheckInSummaryState.Loading)),
        entry("summary failed", ready(summaryState = CheckInSummaryState.Failed)),
        entry("offline", ready(energy = 4, offline = true)),
    )
}

internal fun diarioEntries(): List<GalleryEntry> =
    diaryScreens() + checkIns() + diarioFoodEntries() + diarioTemplateEntries() + notificationCenterEntries()

/** Renders the Diario entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class DiarioGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersDiaryAndCheckIn() = rule.renderGallery(diaryScreens().filter { !it.wide && !it.tall } + checkIns())

    @Test
    fun rendersFood() = rule.renderGallery(diarioFoodEntries().filter { !it.wide && !it.tall })

    @Test
    fun rendersTemplatesAndEntries() = rule.renderGallery(diarioTemplateEntries().filter { !it.wide && !it.tall })
}
