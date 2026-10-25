package io.github.lopezjavier.appfitness.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationDefaults
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import io.github.lopezjavier.appfitness.designsystem.AppTopBarButton
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * App shell: top app bar, tab navigation (tabs only) and the NavHost. On [AppNavigationType.Bar]
 * (compact widths) the navigation is the bottom bar; on [AppNavigationType.Rail] (>= 600 dp) a side
 * rail. The NavHost is the same composition in both, so switching keeps the active tab and scroll.
 * While [isOffline], the "Sin conexión" [OfflineBanner] sits right below the top bar on every
 * destination and layout and pushes the content (and, on the rail layout, the rail) down; on the rail
 * layout it spans the full width above the rail.
 */
@Composable
@Suppress("LongMethod") // One slot per destination.
internal fun AppScaffold(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    navController: NavHostController = rememberNavController(),
    navigationType: AppNavigationType = AppNavigationType.Bar,
    isOffline: Boolean = false,
    profile: ProfileSlot = DefaultProfileSlot,
    preferences: PreferencesSlot = DefaultPreferencesSlot,
    exercisePicker: ExercisePickerSlot = DefaultExercisePickerSlot,
    exerciseDetail: ExerciseDetailSlot = DefaultExerciseDetailSlot,
    routines: RoutinesSlot = DefaultRoutinesSlot,
    routineBuilder: RoutineBuilderSlot = DefaultRoutineBuilderSlot,
    liveSession: LiveSessionSlot = DefaultLiveSessionSlot,
    diary: DiarySlot = DefaultDiarySlot,
    diaryHistory: DiaryHistorySlot = DefaultDiaryHistorySlot,
    weight: WeightSlot = DefaultWeightSlot,
    foodSearch: FoodSearchSlot = DefaultFoodSearchSlot,
    addFood: AddFoodSlot = DefaultAddFoodSlot,
    editEntry: EditEntrySlot = DefaultEditEntrySlot,
    saveTemplate: SaveTemplateSlot = DefaultSaveTemplateSlot,
    createFood: CreateFoodSlot = DefaultCreateFoodSlot,
    templateDetails: TemplateDetailsSlot = DefaultTemplateDetailsSlot,
    mealSlots: MealSlotsSlot = DefaultMealSlotsSlot,
    mealDefaults: MealDefaultsSlot = DefaultMealDefaultsSlot,
    history: HistorySlot = DefaultHistorySlot,
    calendar: CalendarSlot = DefaultCalendarSlot,
    sessionDetail: SessionDetailSlot = DefaultSessionDetailSlot,
    editWorkout: EditWorkoutSlot = DefaultEditWorkoutSlot,
    pastWorkout: PastWorkoutSlot = DefaultPastWorkoutSlot,
    strength: StrengthSlot = DefaultStrengthSlot,
    exerciseProgress: ExerciseProgressSlot = DefaultExerciseProgressSlot,
    muscleVolume: MuscleVolumeSlot = DefaultMuscleVolumeSlot,
) {
    val items = rememberNavigationItems()
    val entry by navController.currentBackStackEntryAsState()
    val chrome = navController.chromeOf(entry)
    val backInterceptor = remember { BackInterceptor() }
    val backButton = rememberBackButton(navController, entry, backInterceptor)
    val isBar = navigationType == AppNavigationType.Bar
    val onTabSelect: (Int) -> Unit = { navController.navigateToTab(TopLevelTab.entries[it]) }
    CompositionLocalProvider(LocalBackInterceptor provides backInterceptor) {
        Scaffold(
            modifier = modifier,
            containerColor = AppTheme.colors.surfaceBase,
            topBar = { ShellTopBar(chrome, backButton) },
            bottomBar = {
                if (isBar && chrome is ShellChrome.Tabs) {
                    AppNavigation(
                        modifier = Modifier.testTag("nav_bar"),
                        items = items,
                        selectedIndex = chrome.tab.ordinal,
                        onItemSelect = onTabSelect,
                    )
                }
            },
        ) { innerPadding ->
            BelowTopBar(isOffline, innerPadding) { contentPadding, contentModifier ->
                ShellContent(
                    navController = navController,
                    features = features,
                    navigationType = navigationType,
                    innerPadding = contentPadding,
                    rail = {
                        if (chrome is ShellChrome.Tabs) {
                            ShellRail(
                                items = items,
                                selectedIndex = chrome.tab.ordinal,
                                onTabSelect = onTabSelect,
                                modifier = it,
                            )
                        }
                    },
                    profile = profile,
                    preferences = preferences,
                    exercisePicker = exercisePicker,
                    exerciseDetail = exerciseDetail,
                    routines = routines,
                    routineBuilder = routineBuilder,
                    liveSession = liveSession,
                    diary = diary,
                    diaryHistory = diaryHistory,
                    weight = weight,
                    foodSearch = foodSearch,
                    addFood = addFood,
                    editEntry = editEntry,
                    saveTemplate = saveTemplate,
                    createFood = createFood,
                    templateDetails = templateDetails,
                    mealSlots = mealSlots,
                    mealDefaults = mealDefaults,
                    history = history,
                    calendar = calendar,
                    sessionDetail = sessionDetail,
                    editWorkout = editWorkout,
                    pastWorkout = pastWorkout,
                    strength = strength,
                    exerciseProgress = exerciseProgress,
                    muscleVolume = muscleVolume,
                    modifier = contentModifier,
                )
            }
        }
    }
}

/**
 * The area below the top bar: the offline banner (when [isOffline]) above [content]. The column consumes the
 * top inset once, so [content] receives the Scaffold padding with a zero top and a modifier that fills the rest.
 */
@Composable
private fun BelowTopBar(
    isOffline: Boolean,
    innerPadding: PaddingValues,
    content: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val start = innerPadding.calculateStartPadding(layoutDirection)
    val end = innerPadding.calculateEndPadding(layoutDirection)
    val belowTopBar =
        PaddingValues(start = start, top = Spacing.none, end = end, bottom = innerPadding.calculateBottomPadding())
    Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
        // The live region lives on the banner root, inside the visibility block: announced on appearance only.
        AnimatedVisibility(isOffline, enter = expandVertically(), exit = shrinkVertically()) {
            OfflineBanner(Modifier.fillMaxWidth().padding(start = start, end = end), testTagPrefix = "offline_banner")
        }
        content(belowTopBar, Modifier.weight(1f))
    }
}

@Composable
private fun rememberNavigationItems(): List<AppNavigationItem> = remember {
    TopLevelTab.entries.map { AppNavigationItem(it.label, it.icon, it.testTag, it.selectedIcon) }
}

@Composable
private fun rememberBackButton(
    navController: NavHostController,
    entry: NavBackStackEntry?,
    interceptor: BackInterceptor,
): AppTopBarButton {
    // Pops only the full-screen entry the top bar was drawn for: a stale tap cannot pop the tab underneath.
    // A destination can take the press over first (for example to confirm discarding edits).
    val goBack =
        remember(navController, entry, interceptor) {
            { if (!interceptor.intercept()) navController.popFullScreen(entry) }
        }
    val backDescription = stringResource(Res.string.topbar_back_cd)
    return remember(backDescription, goBack) { AppTopBarButton(backDescription, goBack) }
}

@Composable
private fun ShellRail(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppNavigation(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelect = onTabSelect,
        modifier = modifier,
        type = AppNavigationType.Rail,
    )
}

/**
 * NavHost plus, on the rail layout, the rail drawn over its start edge. The NavHost always fills the
 * same area and tab content reserves the (remembered) rail width as start padding, so showing or
 * hiding the rail (full-screen destinations) never resizes a tab's viewport: a viewport change at full scroll
 * corrupts the restored list position. The bar layout keeps the same trick with its constant bar height.
 * The top of `innerPadding` is expected to be zero because `BelowTopBar` consumes the top inset.
 */
@Suppress("LongParameterList", "LongMethod") // One slot per destination.
@Composable
private fun ShellContent(
    navController: NavHostController,
    features: AppFeatures,
    navigationType: AppNavigationType,
    innerPadding: PaddingValues,
    rail: @Composable (Modifier) -> Unit,
    profile: ProfileSlot,
    preferences: PreferencesSlot,
    exercisePicker: ExercisePickerSlot,
    exerciseDetail: ExerciseDetailSlot,
    routines: RoutinesSlot,
    routineBuilder: RoutineBuilderSlot,
    liveSession: LiveSessionSlot,
    diary: DiarySlot,
    diaryHistory: DiaryHistorySlot,
    weight: WeightSlot,
    foodSearch: FoodSearchSlot,
    addFood: AddFoodSlot,
    editEntry: EditEntrySlot,
    saveTemplate: SaveTemplateSlot,
    createFood: CreateFoodSlot,
    templateDetails: TemplateDetailsSlot,
    mealSlots: MealSlotsSlot,
    mealDefaults: MealDefaultsSlot,
    history: HistorySlot,
    calendar: CalendarSlot,
    sessionDetail: SessionDetailSlot,
    editWorkout: EditWorkoutSlot,
    pastWorkout: PastWorkoutSlot,
    strength: StrengthSlot,
    exerciseProgress: ExerciseProgressSlot,
    muscleVolume: MuscleVolumeSlot,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    var railWidth by remember { mutableStateOf(AppNavigationDefaults.RailMinWidth) }
    val insets =
        tabContentInsets(
            type = navigationType,
            barHeight = AppNavigationDefaults.barHeight(),
            railWidth = railWidth,
            innerStart = innerPadding.calculateStartPadding(layoutDirection),
            innerBottom = innerPadding.calculateBottomPadding(),
        )
    val tabPadding =
        PaddingValues(
            start = insets.start,
            // The top inset is consumed by BelowTopBar, so the top padding is always zero here.
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = insets.bottom,
        )
    Box(modifier.fillMaxSize()) {
        AppNavHost(
            navController,
            tabPadding = tabPadding,
            fullScreenPadding = innerPadding,
            features = features,
            profile = profile,
            preferences = preferences,
            exercisePicker = exercisePicker,
            exerciseDetail = exerciseDetail,
            routines = routines,
            routineBuilder = routineBuilder,
            liveSession = liveSession,
            diary = diary,
            diaryHistory = diaryHistory,
            weight = weight,
            foodSearch = foodSearch,
            addFood = addFood,
            editEntry = editEntry,
            saveTemplate = saveTemplate,
            createFood = createFood,
            templateDetails = templateDetails,
            mealSlots = mealSlots,
            mealDefaults = mealDefaults,
            history = history,
            calendar = calendar,
            sessionDetail = sessionDetail,
            editWorkout = editWorkout,
            pastWorkout = pastWorkout,
            strength = strength,
            exerciseProgress = exerciseProgress,
            muscleVolume = muscleVolume,
        )
        if (navigationType == AppNavigationType.Rail) {
            rail(
                Modifier
                    .align(Alignment.TopStart)
                    .testTag("nav_rail")
                    .fillMaxHeight()
                    .onSizeChanged { railWidth = with(density) { it.width.toDp() } },
            )
        }
    }
}

@Composable
private fun ShellTopBar(chrome: ShellChrome, backButton: AppTopBarButton) {
    when (chrome) {
        is ShellChrome.Tabs -> AppTopBar(title = stringResource(chrome.tab.label))

        is ShellChrome.FullScreen ->
            AppTopBar(
                title = stringResource(chrome.title),
                navigationButton = backButton,
                testTagPrefix = chrome.topBarTag,
            )

        // The destination draws its own bar; the shell reserves the status bar so the offline banner sits below it.
        ShellChrome.Own -> Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(AppTopBarDefaults.windowInsets))
    }
}
