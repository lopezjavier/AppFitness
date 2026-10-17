package io.github.lopezjavier.appfitness.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_avatar_cd
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationDefaults
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import io.github.lopezjavier.appfitness.designsystem.AppTopBarButton
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
internal fun AppScaffold(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    navController: NavHostController = rememberNavController(),
    navigationType: AppNavigationType = AppNavigationType.Bar,
    isOffline: Boolean = false,
    avatarUrl: String? = null,
    onSignOut: () -> Unit = {},
) {
    val items = rememberNavigationItems()
    val entry by navController.currentBackStackEntryAsState()
    val chrome = entry.chrome()
    val (avatarButton, backButton) = rememberTopBarButtons(navController, entry)
    val isBar = navigationType == AppNavigationType.Bar
    val onTabSelect: (Int) -> Unit = { navController.navigateToTab(TopLevelTab.entries[it]) }
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.surfaceBase,
        topBar = { ShellTopBar(chrome, avatarButton, backButton, avatarUrl) },
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
                onSignOut = onSignOut,
                modifier = contentModifier,
            )
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
 * hiding the rail (Profile) never resizes a tab's viewport: a viewport change at full scroll
 * corrupts the restored list position. The bar layout keeps the same trick with its constant bar height.
 * The top of `innerPadding` is expected to be zero because `BelowTopBar` consumes the top inset.
 */
@Suppress("LongParameterList")
@Composable
private fun ShellContent(
    navController: NavHostController,
    features: AppFeatures,
    navigationType: AppNavigationType,
    innerPadding: PaddingValues,
    rail: @Composable (Modifier) -> Unit,
    onSignOut: () -> Unit,
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
            onSignOut = onSignOut,
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
private fun ShellTopBar(
    chrome: ShellChrome,
    avatarButton: AppTopBarButton,
    backButton: AppTopBarButton,
    avatarUrl: String?,
) {
    when (chrome) {
        is ShellChrome.Tabs ->
            AppTopBar(
                title = stringResource(chrome.tab.label),
                avatarButton = avatarButton,
                avatarImageUrl = avatarUrl,
            )

        is ShellChrome.FullScreen ->
            AppTopBar(title = stringResource(chrome.title), navigationButton = backButton)
    }
}
