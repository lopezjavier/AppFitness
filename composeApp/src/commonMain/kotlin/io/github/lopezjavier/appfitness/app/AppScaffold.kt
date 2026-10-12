package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppTheme

/** App shell: bottom navigation plus the tab NavHost. T-022 adds the topBar, T-027 the FAB. */
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
