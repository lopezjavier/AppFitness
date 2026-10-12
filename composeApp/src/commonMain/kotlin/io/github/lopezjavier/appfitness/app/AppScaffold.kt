package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_avatar_cd
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationDefaults
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import io.github.lopezjavier.appfitness.designsystem.AppTopBarButton
import org.jetbrains.compose.resources.stringResource

/** App shell: top app bar, bottom navigation (tabs only) and the NavHost. T-027 adds the FAB. */
@Composable
internal fun AppScaffold(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val items =
        remember {
            TopLevelTab.entries.map { AppNavigationItem(it.label, it.icon, it.testTag, it.selectedIcon) }
        }
    val chrome = navController.currentChrome()
    // dropUnlessResumed: a double tap cannot push two Profiles or pop the tab under Profile.
    val openAvatarTarget = dropUnlessResumed { navController.navigateToFullScreen(AppDestinations.AvatarTarget) }
    val goBack = dropUnlessResumed { navController.popBackStack() }
    val avatarDescription = stringResource(Res.string.topbar_avatar_cd)
    val backDescription = stringResource(Res.string.topbar_back_cd)
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.surfaceBase,
        topBar = {
            when (chrome) {
                is ShellChrome.Tabs ->
                    AppTopBar(
                        title = stringResource(chrome.tab.label),
                        avatarButton = AppTopBarButton(avatarDescription, openAvatarTarget),
                    )

                is ShellChrome.FullScreen ->
                    AppTopBar(
                        title = stringResource(chrome.title),
                        navigationButton = AppTopBarButton(backDescription, goBack),
                    )
            }
        },
        bottomBar = {
            if (chrome is ShellChrome.Tabs) {
                AppNavigation(
                    items = items,
                    selectedIndex = chrome.tab.ordinal,
                    onItemSelect = { navController.navigateToTab(TopLevelTab.entries[it]) },
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
        AppNavHost(navController, tabPadding = tabPadding, fullScreenPadding = innerPadding)
    }
}
