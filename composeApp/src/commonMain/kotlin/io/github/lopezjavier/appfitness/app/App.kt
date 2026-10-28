package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppMessageHost
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.LocalPinnedFooter
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PinnedFooterState
import io.github.lopezjavier.appfitness.designsystem.navigationType
import io.github.lopezjavier.appfitness.designsystem.typeScale
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.feature.auth.WelcomeRoute
import io.github.lopezjavier.appfitness.feature.onboarding.OnboardingRoute

/**
 * Root composable: theme plus the destination chosen by [rootRoute]. [modifier] reaches the root layout;
 * [isOffline] shows the "Sin conexión" banner on every destination;
 * [pendingReminders] is the count on the bell of the tab roots;
 * [onRetryProfile] and [onOnboardingComplete] are the root actions. [profile], [preferences], [welcome] and
 * [onboarding] are the Perfil root, Preferencias, Welcome and Onboarding destinations (stateful in `AppRoot`, empty or
 * stubbed in tests).
 *
 * The default [rootRoute] is [RootRoute.Main], so previews and shell tests need no session.
 */
@Suppress("LongMethod") // One slot per destination, wired in one place.
@Composable
fun App(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    isOffline: Boolean = false,
    pendingReminders: Int = 0,
    rootRoute: RootRoute = RootRoute.Main,
    onRetryProfile: () -> Unit = {},
    onOnboardingComplete: () -> Unit = {},
    profile: ProfileSlot = DefaultProfileSlot,
    preferences: PreferencesSlot = DefaultPreferencesSlot,
    preferencesSection: PreferencesSectionSlot = DefaultPreferencesSectionSlot,
    account: AccountSlot = DefaultAccountSlot,
    about: AboutSlot = DefaultAboutSlot,
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
    welcome: @Composable () -> Unit = { WelcomeRoute(showTestLogin = features.testLogin != null) },
    onboarding: @Composable () -> Unit = { OnboardingRoute(onComplete = onOnboardingComplete) },
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = windowWidthClassFor(maxWidth)
        AppTheme(typeScale = widthClass.typeScale) {
            // The controller lives above the route branch, so a transient Loading frame (or recreation) does not lose
            // the back stack. Reaching Welcome (sign-out) starts a new epoch: the next user never inherits a stack.
            var epoch by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(rootRoute == RootRoute.Welcome) {
                if (rootRoute == RootRoute.Welcome) epoch++
            }
            val navController = key(epoch) { rememberNavController() }
            when (rootRoute) {
                RootRoute.Main ->
                    AppScaffold(
                        Modifier.fillMaxSize(),
                        navController = navController,
                        features = features,
                        navigationType = widthClass.navigationType,
                        isOffline = isOffline,
                        pendingReminders = pendingReminders,
                        profile = profile,
                        preferences = preferences,
                        preferencesSection = preferencesSection,
                        account = account,
                        about = about,
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

                else ->
                    RootFrame(isOffline) {
                        when (rootRoute) {
                            RootRoute.Loading -> RootLoading()
                            RootRoute.Welcome -> welcome()
                            RootRoute.Onboarding -> onboarding()
                            RootRoute.ProfileError -> RootError(onRetry = onRetryProfile)
                            RootRoute.Main -> Unit
                        }
                    }
            }
        }
    }
}

/** Frame of the destinations outside the shell: `surfaceBase` with the offline banner on top (planning/05 §6). */
@Composable
private fun RootFrame(isOffline: Boolean, content: @Composable () -> Unit) {
    val bannerInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val messages = rememberShellMessages()
    val pinnedFooter = remember { PinnedFooterState() }
    CompositionLocalProvider(LocalAppMessages provides messages, LocalPinnedFooter provides pinnedFooter) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(AppTheme.colors.surfaceBase)) {
                if (isOffline) {
                    OfflineBanner(
                        Modifier.fillMaxWidth().windowInsetsPadding(bannerInsets),
                        testTagPrefix = "offline_banner",
                    )
                }
                // The banner already took the top inset, so the destination must not pad for it again.
                Column(
                    Modifier.weight(
                        1f,
                    ).fillMaxWidth().then(if (isOffline) Modifier.consumeWindowInsets(bannerInsets) else Modifier),
                ) {
                    content()
                }
            }
            // One message host for the destinations outside the shell (R-28), above the pinned footer.
            AppMessageHost(Modifier.align(Alignment.BottomCenter))
        }
    }
}
