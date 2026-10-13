package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import io.github.lopezjavier.appfitness.designsystem.AppTopBarButton
import io.github.lopezjavier.appfitness.designsystem.UniversalFab
import io.github.lopezjavier.appfitness.designsystem.UniversalFabDefaults
import org.jetbrains.compose.resources.stringResource

/**
 * App shell: top app bar, bottom navigation (tabs only), the contextual FAB ([fabAction], tabs only)
 * with its WIP sheet, and the NavHost.
 */
@Composable
internal fun AppScaffold(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    navController: NavHostController = rememberNavController(),
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
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.surfaceBase,
        topBar = { ShellTopBar(chrome, avatarButton, backButton) },
        bottomBar = {
            if (chrome is ShellChrome.Tabs) {
                AppNavigation(
                    modifier = Modifier.testTag("nav_bar"),
                    items = items,
                    selectedIndex = chrome.tab.ordinal,
                    onItemSelect = { navController.navigateToTab(TopLevelTab.entries[it]) },
                )
            }
        },
        floatingActionButton = {
            if (fabAction != null) {
                UniversalFab(
                    text = stringResource(Res.string.fab_label),
                    onClick = { openSheet = fabAction },
                    modifier = Modifier.testTag("fab"),
                )
            }
        },
    ) { innerPadding ->
        // Tab screens keep the bottom-bar space while Profile is open, so their viewport does not change
        // when the bar hides: a viewport change at full scroll corrupts the restored list position.
        val layoutDirection = LocalLayoutDirection.current
        val tabPadding =
            PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = AppNavigationDefaults.barHeight(),
            )
        AppNavHost(
            navController,
            tabPadding = tabPadding,
            fullScreenPadding = innerPadding,
            features = features,
            // Constant across tab <-> Profile, like the bar height above: the tab's content never shifts.
            tabContentBottomClearance = UniversalFabDefaults.ContentClearance,
        )
    }
    openSheet?.let { WipActionSheet(action = it, onDismiss = { openSheet = null }) }
}

@Composable
private fun ShellTopBar(chrome: ShellChrome, avatarButton: AppTopBarButton, backButton: AppTopBarButton) {
    when (chrome) {
        is ShellChrome.Tabs -> AppTopBar(title = stringResource(chrome.tab.label), avatarButton = avatarButton)

        is ShellChrome.FullScreen ->
            AppTopBar(title = stringResource(chrome.title), navigationButton = backButton)
    }
}
