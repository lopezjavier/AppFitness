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
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppMessageHost
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationDefaults
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.LocalPinnedFooter
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PinnedFooterState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.notifications.LocalNotificationsEntry
import io.github.lopezjavier.appfitness.feature.notifications.NotificationsEntry

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
    pendingReminders: Int = 0,
    messages: AppMessages = rememberShellMessages(),
    profile: ProfileSlot = DefaultProfileSlot,
    preferences: PreferencesSlot = DefaultPreferencesSlot,
    preferencesSection: PreferencesSectionSlot = DefaultPreferencesSectionSlot,
    account: AccountSlot = DefaultAccountSlot,
    about: AboutSlot = DefaultAboutSlot,
    activity: ActivitySlot = DefaultActivitySlot,
    exercisePicker: ExercisePickerSlot = DefaultExercisePickerSlot,
    exerciseDetail: ExerciseDetailSlot = DefaultExerciseDetailSlot,
    routines: RoutinesSlot = DefaultRoutinesSlot,
    routineBuilder: RoutineBuilderSlot = DefaultRoutineBuilderSlot,
    routineDetails: RoutineDetailsSlot = DefaultRoutineDetailsSlot,
    liveSession: LiveSessionSlot = DefaultLiveSessionSlot,
    diary: DiarySlot = DefaultDiarySlot,
    diaryHistory: DiaryHistorySlot = DefaultDiaryHistorySlot,
    weight: WeightSlot = DefaultWeightSlot,
    weightHistory: WeightHistorySlot = DefaultWeightHistorySlot,
    notificationCenter: NotificationCenterSlot = DefaultNotificationCenterSlot,
    checkIn: CheckInSlot = DefaultCheckInSlot,
    foodSearch: FoodSearchSlot = DefaultFoodSearchSlot,
    addFood: AddFoodSlot = DefaultAddFoodSlot,
    editEntry: EditEntrySlot = DefaultEditEntrySlot,
    saveTemplate: SaveTemplateSlot = DefaultSaveTemplateSlot,
    createFood: CreateFoodSlot = DefaultCreateFoodSlot,
    templateDetails: TemplateDetailsSlot = DefaultTemplateDetailsSlot,
    mealSlots: MealSlotsSlot = DefaultMealSlotsSlot,
    mealDefaults: MealDefaultsSlot = DefaultMealDefaultsSlot,
    history: HistorySlot = DefaultHistorySlot,
    sessionDetail: SessionDetailSlot = DefaultSessionDetailSlot,
    editWorkout: EditWorkoutSlot = DefaultEditWorkoutSlot,
    pastWorkout: PastWorkoutSlot = DefaultPastWorkoutSlot,
    strength: StrengthSlot = DefaultStrengthSlot,
    exerciseProgress: ExerciseProgressSlot = DefaultExerciseProgressSlot,
    muscleVolume: MuscleVolumeSlot = DefaultMuscleVolumeSlot,
    trainingStats: TrainingStatsSlot = DefaultTrainingStatsSlot,
) {
    val items = rememberNavigationItems()
    val entry by navController.currentBackStackEntryAsState()
    val chrome = navController.chromeOf(entry)
    val backInterceptor = remember { BackInterceptor() }
    val isBar = navigationType == AppNavigationType.Bar
    val onTabSelect: (Int) -> Unit = { navController.navigateToTab(TopLevelTab.entries[it]) }
    val pinnedFooter = remember { PinnedFooterState() }
    val openNotifications = remember(navController) { { navController.navigateToFullScreen(NotificationCenterRoute) } }
    val notificationsEntry = remember(pendingReminders, openNotifications) {
        NotificationsEntry(pendingReminders, openNotifications)
    }
    CompositionLocalProvider(
        LocalNotificationsEntry provides notificationsEntry,
        LocalBackInterceptor provides backInterceptor,
        LocalAppMessages provides messages,
        LocalPinnedFooter provides pinnedFooter,
    ) {
        Scaffold(
            modifier = modifier,
            containerColor = AppTheme.colors.surfaceBase,
            topBar = { ShellTopBar() },
            bottomBar = {
                val tab = chrome.tabOrNull()
                if (isBar && tab != null) {
                    AppNavigation(
                        modifier = Modifier.testTag("nav_bar"),
                        items = items,
                        selectedIndex = tab.ordinal,
                        onItemSelect = onTabSelect,
                    )
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                BelowTopBar(isOffline, innerPadding) {
                        contentPadding,
                        contentModifier,
                    ->
                    ShellContent(
                        navController = navController,
                        features = features,
                        navigationType = navigationType,
                        innerPadding = contentPadding,
                        rail = {
                            val railTab = chrome.tabOrNull()
                            if (railTab != null) {
                                ShellRail(
                                    items = items,
                                    selectedIndex = railTab.ordinal,
                                    onTabSelect = onTabSelect,
                                    modifier = it,
                                )
                            }
                        },
                        profile = profile,
                        preferences = preferences,
                        preferencesSection = preferencesSection,
                        account = account,
                        about = about,
                        activity = activity,
                        exercisePicker = exercisePicker,
                        exerciseDetail = exerciseDetail,
                        routines = routines,
                        routineBuilder = routineBuilder,
                        routineDetails = routineDetails,
                        liveSession = liveSession,
                        diary = diary,
                        diaryHistory = diaryHistory,
                        weight = weight,
                        weightHistory = weightHistory,
                        notificationCenter = notificationCenter,
                        checkIn = checkIn,
                        foodSearch = foodSearch,
                        addFood = addFood,
                        editEntry = editEntry,
                        saveTemplate = saveTemplate,
                        createFood = createFood,
                        templateDetails = templateDetails,
                        mealSlots = mealSlots,
                        mealDefaults = mealDefaults,
                        history = history,
                        sessionDetail = sessionDetail,
                        editWorkout = editWorkout,
                        pastWorkout = pastWorkout,
                        strength = strength,
                        exerciseProgress = exerciseProgress,
                        muscleVolume = muscleVolume,
                        trainingStats = trainingStats,
                        modifier = contentModifier,
                    )
                }
                // Above the bottom bar (or the nav bar / keyboard) and, via LocalPinnedFooter, above a pinned footer.
                AppMessageHost(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = innerPadding.calculateBottomPadding()),
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
    // An `AppScreen` pads for the system insets itself: the shell reserved them (status bar spacer, bars), so they
    // count as consumed and nothing is padded twice.
    Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()).consumeWindowInsets(innerPadding)) {
        // The live region lives on the banner root, inside the visibility block: announced on appearance only.
        AnimatedVisibility(isOffline, enter = expandVertically(), exit = shrinkVertically()) {
            OfflineBanner(Modifier.fillMaxWidth().padding(start = start, end = end), testTagPrefix = "offline_banner")
        }
        content(belowTopBar, Modifier.weight(1f))
    }
}

/** The shell's message queue: lives as long as the shell, so a message survives the destination that sent it. */
@Composable
internal fun rememberShellMessages(): AppMessages {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppMessages(scope) }
}

@Composable
private fun rememberNavigationItems(): List<AppNavigationItem> = remember {
    TopLevelTab.entries.map { AppNavigationItem(it.label, it.icon, it.testTag, it.selectedIcon) }
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
    preferencesSection: PreferencesSectionSlot,
    account: AccountSlot,
    about: AboutSlot,
    activity: ActivitySlot,
    exercisePicker: ExercisePickerSlot,
    exerciseDetail: ExerciseDetailSlot,
    routines: RoutinesSlot,
    routineBuilder: RoutineBuilderSlot,
    routineDetails: RoutineDetailsSlot,
    liveSession: LiveSessionSlot,
    diary: DiarySlot,
    diaryHistory: DiaryHistorySlot,
    weight: WeightSlot,
    weightHistory: WeightHistorySlot,
    notificationCenter: NotificationCenterSlot,
    checkIn: CheckInSlot,
    foodSearch: FoodSearchSlot,
    addFood: AddFoodSlot,
    editEntry: EditEntrySlot,
    saveTemplate: SaveTemplateSlot,
    createFood: CreateFoodSlot,
    templateDetails: TemplateDetailsSlot,
    mealSlots: MealSlotsSlot,
    mealDefaults: MealDefaultsSlot,
    history: HistorySlot,
    sessionDetail: SessionDetailSlot,
    editWorkout: EditWorkoutSlot,
    pastWorkout: PastWorkoutSlot,
    strength: StrengthSlot,
    exerciseProgress: ExerciseProgressSlot,
    muscleVolume: MuscleVolumeSlot,
    trainingStats: TrainingStatsSlot,
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
            preferencesSection = preferencesSection,
            account = account,
            about = about,
            activity = activity,
            exercisePicker = exercisePicker,
            exerciseDetail = exerciseDetail,
            routines = routines,
            routineBuilder = routineBuilder,
            routineDetails = routineDetails,
            liveSession = liveSession,
            diary = diary,
            diaryHistory = diaryHistory,
            weight = weight,
            weightHistory = weightHistory,
            notificationCenter = notificationCenter,
            checkIn = checkIn,
            foodSearch = foodSearch,
            addFood = addFood,
            editEntry = editEntry,
            saveTemplate = saveTemplate,
            createFood = createFood,
            templateDetails = templateDetails,
            mealSlots = mealSlots,
            mealDefaults = mealDefaults,
            history = history,
            sessionDetail = sessionDetail,
            editWorkout = editWorkout,
            pastWorkout = pastWorkout,
            strength = strength,
            exerciseProgress = exerciseProgress,
            muscleVolume = muscleVolume,
            trainingStats = trainingStats,
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

/** The shell reserves only the status bar: every destination draws its own header (T-128). */
@Composable
private fun ShellTopBar() {
    Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(AppTopBarDefaults.windowInsets))
}

/** The tab whose bottom bar or rail this chrome shows, or null for a full-screen chrome. */
internal fun ShellChrome.tabOrNull(): TopLevelTab? = when (this) {
    is ShellChrome.TabScreen -> tab
    ShellChrome.Screen -> null
}
