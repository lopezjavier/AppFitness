package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.lopezjavier.appfitness.composeapp.resources.fab_label
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_avatar_cd
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationDefaults
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import io.github.lopezjavier.appfitness.designsystem.AppTopBarButton
import io.github.lopezjavier.appfitness.designsystem.UniversalFab
import org.jetbrains.compose.resources.stringResource

/**
 * App shell: top app bar, tab navigation (tabs only), the contextual FAB ([fabAction], tabs only) with
 * its WIP sheet, and the NavHost. On [AppNavigationType.Bar] (compact widths) the navigation is the
 * bottom bar and the FAB sits in the Scaffold slot; on [AppNavigationType.Rail] (>= 600 dp) a side rail
 * carries the FAB at its top. The NavHost is the same composition in both, so switching keeps the
 * active tab, scroll and the open sheet.
 */
@Composable
internal fun AppScaffold(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    navController: NavHostController = rememberNavController(),
    navigationType: AppNavigationType = AppNavigationType.Bar,
) {
    val items =
        remember {
            TopLevelTab.entries.map { AppNavigationItem(it.label, it.icon, it.testTag, it.selectedIcon) }
        }
    val entry by navController.currentBackStackEntryAsState()
    val chrome = entry.chrome()
    val fabAction = chrome.fabAction()
    // Stored action, not the live chrome: the sheet title stays consistent if the chrome changes underneath.
    var openSheet by rememberSaveable { mutableStateOf<FabAction?>(null) }
    val (avatarButton, backButton) = rememberTopBarButtons(navController, entry)
    val isBar = navigationType == AppNavigationType.Bar
    val onTabSelect: (Int) -> Unit = { navController.navigateToTab(TopLevelTab.entries[it]) }
    val fabLabel = stringResource(Res.string.fab_label)
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.surfaceBase,
        topBar = { ShellTopBar(chrome, avatarButton, backButton) },
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
        floatingActionButton = {
            if (isBar && fabAction != null) {
                UniversalFab(text = fabLabel, onClick = { openSheet = fabAction }, modifier = Modifier.testTag("fab"))
            }
        },
    ) { innerPadding ->
        // Single place below the top bar: T-029 inserts the OfflineBanner above this Box.
        ShellContent(
            navController = navController,
            features = features,
            navigationType = navigationType,
            innerPadding = innerPadding,
            rail = {
                if (chrome is ShellChrome.Tabs) {
                    ShellRail(
                        items = items,
                        selectedIndex = chrome.tab.ordinal,
                        onTabSelect = onTabSelect,
                        header = {
                            // Same tag as the phone FAB; the tab's contextual action decides whether it shows.
                            fabAction?.let { action ->
                                UniversalFab(fabLabel, onClick = { openSheet = action }, Modifier.testTag("fab"))
                            }
                        },
                        modifier = it,
                    )
                }
            },
        )
    }
    openSheet?.let { WipActionSheet(action = it, onDismiss = { openSheet = null }) }
}

@Composable
private fun rememberTopBarButtons(
    navController: NavHostController,
    entry: NavBackStackEntry?,
): Pair<AppTopBarButton, AppTopBarButton> {
    // launchSingleTop stops a double tap from pushing two Profiles.
    val openAvatarTarget =
        remember(navController) { { navController.navigateToFullScreen(AppDestinations.AvatarTarget) } }
    // Pops only the full-screen entry the top bar was drawn for: a stale tap cannot pop the tab underneath.
    val goBack = remember(navController, entry) { { navController.popFullScreen(entry) } }
    val avatarDescription = stringResource(Res.string.topbar_avatar_cd)
    val backDescription = stringResource(Res.string.topbar_back_cd)
    val avatarButton =
        remember(avatarDescription, openAvatarTarget) { AppTopBarButton(avatarDescription, openAvatarTarget) }
    val backButton = remember(backDescription, goBack) { AppTopBarButton(backDescription, goBack) }
    return avatarButton to backButton
}

@Composable
private fun ShellRail(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onTabSelect: (Int) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    AppNavigation(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelect = onTabSelect,
        modifier = modifier,
        type = AppNavigationType.Rail,
        header = header,
    )
}

/**
 * NavHost plus, on the rail layout, the rail drawn over its start edge. The NavHost always fills the
 * same area and tab content reserves the (remembered) rail width as start padding, so showing or
 * hiding the rail (Profile) never resizes a tab's viewport: a viewport change at full scroll
 * corrupts the restored list position. The bar layout keeps the same trick with its constant bar height.
 */
@Composable
private fun ShellContent(
    navController: NavHostController,
    features: AppFeatures,
    navigationType: AppNavigationType,
    innerPadding: PaddingValues,
    rail: @Composable (Modifier) -> Unit,
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
            top = innerPadding.calculateTopPadding(),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = insets.bottom,
        )
    Box(Modifier.fillMaxSize()) {
        AppNavHost(
            navController,
            tabPadding = tabPadding,
            fullScreenPadding = innerPadding,
            features = features,
            // Constant across tab <-> Profile, like the padding above: the tab's content never shifts.
            tabContentBottomClearance = insets.contentBottomClearance,
        )
        if (navigationType == AppNavigationType.Rail) {
            rail(
                Modifier
                    .align(Alignment.TopStart)
                    .testTag("nav_rail")
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxHeight()
                    .onSizeChanged { railWidth = with(density) { it.width.toDp() } },
            )
        }
    }
}

@Composable
private fun ShellTopBar(chrome: ShellChrome, avatarButton: AppTopBarButton, backButton: AppTopBarButton) {
    when (chrome) {
        is ShellChrome.Tabs -> AppTopBar(title = stringResource(chrome.tab.label), avatarButton = avatarButton)

        is ShellChrome.FullScreen ->
            AppTopBar(title = stringResource(chrome.title), navigationButton = backButton)
    }
}
