package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_avatar_cd
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import org.jetbrains.compose.resources.stringResource

/** App shell: top app bar, bottom navigation and the tab NavHost. T-027 adds the FAB. */
@Composable
internal fun AppScaffold(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    val items =
        remember {
            TopLevelTab.entries.map { AppNavigationItem(it.label, it.icon, it.testTag) }
        }
    val currentTab = navController.currentTopLevelTab()
    Scaffold(
        modifier = modifier,
        containerColor = AppTheme.colors.surfaceBase,
        topBar = {
            AppTopBar(
                title = stringResource(currentTab.label),
                onAvatarClick = { navController.navigateToTab(TopLevelTab.AvatarTarget) },
                avatarContentDescription = stringResource(Res.string.topbar_avatar_cd),
            )
        },
        bottomBar = {
            AppNavigation(
                items = items,
                selectedIndex = currentTab.ordinal,
                onItemSelect = { navController.navigateToTab(TopLevelTab.entries[it]) },
            )
        },
    ) { innerPadding ->
        AppNavHost(navController, Modifier.padding(innerPadding))
    }
}
