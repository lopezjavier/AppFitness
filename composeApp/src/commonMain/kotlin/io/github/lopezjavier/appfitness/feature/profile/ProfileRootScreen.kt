package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_settings
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_about_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_calories_value
import io.github.lopezjavier.appfitness.composeapp.resources.profile_diary_history_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_statistics_section
import io.github.lopezjavier.appfitness.composeapp.resources.profile_strength_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_strength_value
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_training_stats_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_value
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.designsystem.AppAvatar
import io.github.lopezjavier.appfitness.designsystem.AppAvatarDefaults
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.analytics.formatKilos
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val NAME_MAX_LINES = 2
private const val NAME_SKELETON_WIDTH_UNITS = 8
private const val SKELETON_ROWS = 4

/** The callbacks of the Perfil root, grouped to keep the layout composables' signatures short. */
@Suppress("LongParameterList") // One callback per row or button.
class ProfileRootActions(
    val onOpenPreferences: () -> Unit = {},
    val onOpenCatalog: (() -> Unit)? = null,
    val onOpenStrength: () -> Unit = {},
    val onOpenWeight: () -> Unit = {},
    val onOpenDiaryHistory: () -> Unit = {},
    val onOpenTrainingStats: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
)

/** Stateful Perfil root: connects [ProfileRootViewModel] to the stateless [ProfileRootScreen]. */
@Composable
fun ProfileRootRoute(
    modifier: Modifier = Modifier,
    actions: ProfileRootActions = ProfileRootActions(),
    viewModel: ProfileRootViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileRootScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier, actions = actions)
}

/**
 * The Perfil tab root (ADR-37, US-05, US-56) on [AppScreen]: the "Preferencias" gear in the bar, the photo and name,
 * and "Estadísticas" with the "Peso", "Fuerza", "Calorías" and "Entrenamiento" rows. A row shows its value once it is
 * known and none when it could not be read. The debug catalog button appears where [ProfileRootActions.onOpenCatalog]
 * is non-null (local flavor). It holds no editable state, so there is nothing to restore.
 */
@Composable
fun ProfileRootScreen(
    state: ProfileRootUiState,
    onEvent: (ProfileRootEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: ProfileRootActions = ProfileRootActions(),
) {
    val gear = stringResource(Res.string.preferences_title)
    val barActions = remember(gear, actions.onOpenPreferences) {
        listOf(AppTopBarAction(Res.drawable.ic_settings, gear, actions.onOpenPreferences, "profile_action_0"))
    }
    AppScreen(
        title = stringResource(Res.string.profile_title),
        modifier = modifier,
        actions = barActions,
        testTagPrefix = "profile",
    ) {
        Box(Modifier.fillMaxSize().testTag("profile_screen"), contentAlignment = Alignment.TopCenter) {
            when (state) {
                ProfileRootUiState.Loading -> RootBody(actions.onOpenCatalog) { RootSkeleton() }

                ProfileRootUiState.LoadFailed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.root_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(ProfileRootEvent.Retry)
                        },
                        testTagPrefix = "profile_root_error",
                    )
                }

                is ProfileRootUiState.Loaded -> RootBody(actions.onOpenCatalog) { RootLoaded(state, actions) }
            }
        }
    }
}

/** Scrollable body below the bar: [content], then the local-only catalog button. */
@Composable
private fun RootBody(onOpenCatalog: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        content()
        if (onOpenCatalog != null) {
            SecondaryButton(
                text = stringResource(Res.string.catalog_title),
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth().testTag("profile_root_catalog_button"),
            )
        }
    }
}

@Composable
private fun RootLoaded(state: ProfileRootUiState.Loaded, actions: ProfileRootActions) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        AppAvatar(
            imageUrl = state.avatarUrl,
            size = AppAvatarDefaults.RootSize,
            testTagPrefix = "profile_root",
            modifier = Modifier.testTag("profile_root_avatar"),
        )
        state.displayName?.let { name ->
            Text(
                text = name,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("profile_root_name"),
            )
        }
        StatisticsSection(state, actions)
        NavigationRow(
            label = stringResource(Res.string.profile_about_row),
            onClick = actions.onOpenAbout,
            modifier = Modifier.fillMaxWidth().testTag("profile_about_row"),
        )
    }
}

/** "Estadísticas": a heading and one [NavigationRow] per destination, each label equal to its screen title (R-04). */
@Composable
private fun StatisticsSection(state: ProfileRootUiState.Loaded, actions: ProfileRootActions) {
    val weight = state.weightKg?.let { stringResource(Res.string.profile_weight_value, formatKilos(it)) }
    val strength = state.strengthRecordKg?.let { stringResource(Res.string.profile_strength_value, formatKilos(it)) }
    val calories = state.avgKcal?.let {
        stringResource(Res.string.profile_calories_value, AppNumberFormat.integer(it))
    }
    Column(Modifier.fillMaxWidth()) {
        ListSectionHeader(
            title = stringResource(Res.string.profile_statistics_section),
            modifier = Modifier.testTag("profile_statistics_heading"),
        )
        NavigationRow(
            label = stringResource(Res.string.profile_weight_row),
            onClick = actions.onOpenWeight,
            modifier = Modifier.testTag("profile_weight_row"),
            value = weight,
        )
        NavigationRow(
            label = stringResource(Res.string.profile_strength_row),
            onClick = actions.onOpenStrength,
            modifier = Modifier.testTag("profile_strength_row"),
            value = strength,
        )
        NavigationRow(
            label = stringResource(Res.string.profile_diary_history_row),
            onClick = actions.onOpenDiaryHistory,
            modifier = Modifier.testTag("profile_diary_history_row"),
            value = calories,
        )
        NavigationRow(
            label = stringResource(Res.string.profile_training_stats_row),
            onClick = actions.onOpenTrainingStats,
            modifier = Modifier.testTag("profile_training_stats_row"),
        )
    }
}

@Composable
private fun RootSkeleton() {
    Column(
        Modifier.testTag("profile_root_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SkeletonBox(Modifier.size(AppAvatarDefaults.RootSize), AppShapes.full)
        SkeletonBox(Modifier.size(Spacing.xl * NAME_SKELETON_WIDTH_UNITS, Spacing.xl), AppShapes.sm)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(SKELETON_ROWS) {
                SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.sm)
            }
        }
    }
}
