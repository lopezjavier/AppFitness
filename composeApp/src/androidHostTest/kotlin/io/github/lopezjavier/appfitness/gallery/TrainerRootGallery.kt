package io.github.lopezjavier.appfitness.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.feature.trainer.BlockSummary
import io.github.lopezjavier.appfitness.feature.trainer.DiaryCalories
import io.github.lopezjavier.appfitness.feature.trainer.TrainerRootMenu
import io.github.lopezjavier.appfitness.feature.trainer.TrainerRootPreview
import io.github.lopezjavier.appfitness.feature.trainer.TrainerScreen
import io.github.lopezjavier.appfitness.feature.trainer.TrainerUiState
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal const val TRAINER_AREA = "Trainer"
private const val TRAINER_SCREEN = "Trainer"

/** The Trainer hub (TrainerHome): every state the root can show, with the plan and block entries. */
@Suppress("LongMethod") // A list of fixtures.
private fun trainerHome(): List<GalleryEntry> {
    fun home(state: String, ui: TrainerUiState, online: Boolean = true, wide: Boolean = false, fontScale: Float = 1f) =
        GalleryEntry(
            TRAINER_AREA,
            TRAINER_SCREEN,
            state,
            covers = setOf("TrainerHome"),
            wide = wide,
            fontScale = fontScale,
        ) {
            TrainerScreen(state = ui, isOnline = flowOf(online), onEvent = {})
        }

    return listOf(
        home("loading", TrainerUiState.Loading),
        home("no plan yet", TrainerFixtures.contentWithoutPlan()),
        home("proposal waiting for a decision", TrainerFixtures.contentWithProposal()),
        home("plan and block active", TrainerFixtures.contentWithBlock()),
        home("block paused", TrainerFixtures.contentWithBlock(pause = TrainerFixtures.pausedBlock)),
        home("block review due", TrainerFixtures.contentWithBlock(blockReviewDue = true)),
        home("calorie proposal", TrainerFixtures.contentWithBlock(proposal = TrainerFixtures.calorieProposal)),
        home(
            "week bars, empty (Monday)",
            TrainerFixtures.contentWithBlock(
                weeks = TrainerFixtures.weeks(completed = 0, goals = TrainerFixtures.WeekGoalsFixture(0, null)),
                today = TrainerFixtures.planStart.plus(DatePeriod(days = 7)),
            ),
        ),
        home(
            "week bars, behind",
            TrainerFixtures.contentWithBlock(
                weeks = TrainerFixtures.weeks(completed = 0, goals = TrainerFixtures.WeekGoalsFixture(20, 4200)),
            ),
        ),
        home(
            "week bars, done",
            TrainerFixtures.contentWithBlock(
                weeks = TrainerFixtures.weeks(completed = 4, goals = TrainerFixtures.WeekGoalsFixture(150, 7100)),
            ),
        ),
        home(
            "week bars, over target",
            TrainerFixtures.contentWithBlock(
                weeks = TrainerFixtures.weeks(completed = 4, goals = TrainerFixtures.WeekGoalsFixture(200, 8500)),
            ),
        ),
        home("offline", TrainerFixtures.contentWithBlock(), online = false),
        home("large font (1.3)", TrainerFixtures.contentWithBlock(), fontScale = 1.3f),
        home("tablet width", TrainerFixtures.contentWithBlock(), wide = true),
        // The Calorías row (05 §6.0g, always cyan): target 2.000 kcal; "plan and block active" is its empty state
        // ("Sin datos de comidas", no bar).
        home(
            "calories, one day logged",
            TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 1_690, days = 1)),
        ),
        home(
            "calories, in progress (under target)",
            TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 1_690, days = 10)),
        ),
        home(
            "calories, on target",
            TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 2_000, days = 10)),
        ),
        home(
            "calories, over target",
            TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 2_240, days = 10)),
        ),
        home(
            "calories, in progress, large font (1.3)",
            TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 1_690, days = 10)),
            fontScale = 1.3f,
        ),
        home(
            "calories, over target, tablet width",
            TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 2_240, days = 10)),
            wide = true,
        ),
        home(
            "week bars, over target, large font (1.3)",
            TrainerFixtures.contentWithBlock(
                weeks = TrainerFixtures.weeks(completed = 4, goals = TrainerFixtures.WeekGoalsFixture(200, 8500)),
            ),
            fontScale = 1.3f,
        ),
    )
}

/** The swap picker of a flagged exercise, opened from the hub's discomfort follow-up (US-108). */
private fun swapPicker(): List<GalleryEntry> = listOf(
    GalleryEntry(
        TRAINER_AREA,
        TRAINER_SCREEN,
        "swap picker, candidates",
        GalleryKind.Sheet,
        covers = setOf("TrainerHome", "SwapPickerSheet"),
    ) {
        TrainerScreen(
            state = TrainerFixtures.contentWithBlock().copy(swapPicker = TrainerFixtures.swapPicker()),
            isOnline = flowOf(true),
            onEvent = {},
        )
    },
    GalleryEntry(
        TRAINER_AREA,
        TRAINER_SCREEN,
        "swap picker, loading",
        GalleryKind.Sheet,
        covers = setOf("TrainerHome", "SwapPickerSheet"),
    ) {
        TrainerScreen(
            state = TrainerFixtures.contentWithBlock().copy(swapPicker = TrainerFixtures.swapPicker(loading = true)),
            isOnline = flowOf(true),
            onEvent = {},
        )
    },
)

/**
 * The root cards of 2026-11-15 (R-105): each card's ⋮ sheet, the week picker of "Tu bloque" on past, current and
 * upcoming weeks, the paused block's end date, the diet-break week, a 16-week block, "Cambios del entrenador" with an
 * applied, a reverted and a busy row, the dialogs and the cardio sheet the menus open, at 360 dp (font 1.0 and 1.3)
 * and on a tablet. Tall images show the whole root (the block card sits below the plan card).
 */
@Suppress("LongMethod") // A list of fixtures.
private fun trainerRootCards(): List<GalleryEntry> {
    @Suppress("LongParameterList") // One image: its state, layout and the gallery hooks.
    fun root(
        state: String,
        ui: TrainerUiState,
        online: Boolean = true,
        kind: GalleryKind = GalleryKind.Screen,
        tall: Boolean = true,
        wide: Boolean = false,
        fontScale: Float = 1f,
        preview: TrainerRootPreview = TrainerRootPreview(),
        covers: Set<String> = emptySet(),
    ) = GalleryEntry(
        TRAINER_AREA,
        TRAINER_SCREEN,
        state,
        kind,
        covers = setOf("TrainerHome") + covers,
        wide = wide,
        tall = tall && !wide,
        fontScale = fontScale,
    ) {
        TrainerScreen(
            state = ui,
            isOnline = flowOf(online),
            onEvent = {},
            exerciseNames = TrainerFixtures.exerciseNames,
            preview = preview,
        )
    }

    val withChanges = TrainerFixtures.contentWithBlock(
        weeks = TrainerFixtures.mixedWeeks(),
        coachChanges = TrainerFixtures.appliedChanges(),
        busyChangeId = TrainerFixtures.BUSY_CHANGE_ID,
        diaryCalories = DiaryCalories(avgKcal = 1_890, days = 12),
    )
    val withPace = TrainerFixtures.contentWithBlock(diaryCalories = DiaryCalories(avgKcal = 1_890, days = 12))
        .copy(weightPace = TrainerFixtures.weightPace())
    return listOf(
        // The Offline banner sits right below the top bar of the shell, over the offline root.
        GalleryEntry(
            TRAINER_AREA,
            TRAINER_SCREEN,
            "offline, with the Sin conexión banner",
            covers = setOf("TrainerHome"),
        ) {
            Column(Modifier.fillMaxSize()) {
                OfflineBanner(Modifier.fillMaxWidth())
                TrainerScreen(
                    state = TrainerFixtures.contentWithBlock(),
                    isOnline = flowOf(false),
                    onEvent = {},
                    modifier = Modifier.weight(1f),
                )
            }
        },
        root("cards, coach changes (applied, reverted, undo running)", withChanges),
        root("cards, coach changes offline", withChanges.copy(busyChangeId = null), online = false),
        root("cards, large font (1.3)", withChanges, fontScale = 1.3f),
        root("cards, tablet width", withChanges, wide = true),
        // The pace key ("Ritmo" and "Objetivo") shows only under a drawn chart; "cards" above have no weigh-ins (R-102).
        root("plan chart with weigh-ins", withPace),
        root("plan chart with weigh-ins, large font (1.3)", withPace, fontScale = 1.3f),
        root("plan chart with weigh-ins, tablet width", withPace, wide = true),
        root("plan read failed", TrainerUiState.Error, tall = false),
        root(
            "weeks could not be read",
            TrainerFixtures.contentWithBlock(weeks = emptyList()),
        ),
        root("no block yet, Crear bloque", TrainerFixtures.contentWithBlock(block = BlockSummary.None)),
        root(
            "week picker, current week (default)",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.mixedWeeks()),
        ),
        root(
            "week picker, past week selected",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.mixedWeeks()),
            preview = TrainerRootPreview(week = 1),
        ),
        root(
            "week picker, upcoming deload week selected",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.mixedWeeks()),
            preview = TrainerRootPreview(week = 3),
        ),
        root(
            "week picker, upcoming week selected, large font (1.3)",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.mixedWeeks()),
            fontScale = 1.3f,
            preview = TrainerRootPreview(week = 4),
        ),
        root(
            "week picker, diet-break week selected",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.mixedWeeks(), dietBreakWeek = 4),
            preview = TrainerRootPreview(week = 4),
        ),
        root(
            "week picker, paused block with its end date",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.mixedWeeks(), pause = TrainerFixtures.pausedBlock),
        ),
        root(
            "week picker, ended block (last week selected)",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.endedWeeks(), blockReviewDue = true),
        ),
        root(
            "week picker, 6-week block",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.longWeeks(total = 6, current = 3)),
        ),
        root(
            "week picker, 6-week block, large font (1.3)",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.longWeeks(total = 6, current = 3)),
            fontScale = 1.3f,
        ),
        root(
            "week picker, 16-week block",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.longWeeks()),
        ),
        root(
            "week picker, 16-week block, past week selected, large font (1.3)",
            TrainerFixtures.contentWithBlock(weeks = TrainerFixtures.longWeeks()),
            fontScale = 1.3f,
            preview = TrainerRootPreview(week = 3),
        ),
        root(
            "Tu plan menu open",
            TrainerFixtures.contentWithBlock(),
            kind = GalleryKind.Sheet,
            tall = false,
            preview = TrainerRootPreview(openMenu = TrainerRootMenu.Plan),
            covers = setOf("CardMenu"),
        ),
        root(
            "Tu plan menu open, offline",
            TrainerFixtures.contentWithBlock(dietBreakWeek = 4),
            online = false,
            kind = GalleryKind.Sheet,
            tall = false,
            preview = TrainerRootPreview(openMenu = TrainerRootMenu.Plan),
            covers = setOf("CardMenu"),
        ),
        root(
            "Tu bloque menu open",
            TrainerFixtures.contentWithBlock(pause = TrainerFixtures.extendedBlock),
            kind = GalleryKind.Sheet,
            tall = false,
            preview = TrainerRootPreview(openMenu = TrainerRootMenu.Block),
            covers = setOf("CardMenu"),
        ),
        root(
            "Tu bloque menu open, offline",
            TrainerFixtures.contentWithBlock(pause = TrainerFixtures.extendedBlock),
            online = false,
            kind = GalleryKind.Sheet,
            tall = false,
            preview = TrainerRootPreview(openMenu = TrainerRootMenu.Block),
            covers = setOf("CardMenu"),
        ),
        root(
            "cardio sheet from the Tu plan menu",
            TrainerFixtures.contentWithBlock().copy(cardioEdit = TrainerFixtures.cardioSheet(draft = 120)),
            kind = GalleryKind.Sheet,
            tall = false,
            covers = setOf("CardioMinutesSheet"),
        ),
        root(
            "week end picker from the Tu bloque menu",
            TrainerFixtures.contentWithBlock(pause = TrainerFixtures.extendedBlock).copy(extendWeekPickerOpen = true),
            kind = GalleryKind.Sheet,
            tall = false,
            covers = setOf("ExtendWeekPicker"),
        ),
        root(
            "remove block confirmation from the Tu bloque menu",
            TrainerFixtures.contentWithBlock().copy(removeBlockConfirm = true),
            kind = GalleryKind.Dialog,
            tall = false,
            covers = setOf("RemoveBlockDialog"),
        ),
    )
}

internal fun trainerRootEntries(): List<GalleryEntry> = trainerHome() + trainerRootCards() + swapPicker()

/** Renders the Trainer hub and its boxes (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w360dp-h640dp-xxhdpi")
class TrainerRootGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTrainerRoot() = rule.renderGallery(trainerRootEntries().filter { !it.wide && !it.tall })
}
