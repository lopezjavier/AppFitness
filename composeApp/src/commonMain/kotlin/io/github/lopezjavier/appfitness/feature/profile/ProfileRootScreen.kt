package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_directions_walk_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.composeapp.resources.ic_restaurant_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_settings
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_my_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_about_action_cd
import io.github.lopezjavier.appfitness.composeapp.resources.profile_activity_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_diary_history_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_statistics_section
import io.github.lopezjavier.appfitness.composeapp.resources.profile_strength_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_training_stats_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_row
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.designsystem.AppAvatar
import io.github.lopezjavier.appfitness.designsystem.AppAvatarDefaults
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListRowSkeleton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.helpTopBarAction
import io.github.lopezjavier.appfitness.feature.notifications.notificationsBarAction
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val NAME_MAX_LINES = 2
private const val NAME_SKELETON_WIDTH_UNITS = 8
private const val SKELETON_ROWS = 4
private val HEADER_HEIGHT = Spacing.xl * 3
private val STAT_ICON_SIZE = Spacing.xl

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
    val onOpenActivity: () -> Unit = {},
    val onOpenMyTraining: () -> Unit = {},
)

/** Stateful Perfil root: connects [ProfileRootViewModel] to the stateless [ProfileRootScreen]. */
@Composable
fun ProfileRootRoute(
    modifier: Modifier = Modifier,
    actions: ProfileRootActions = ProfileRootActions(),
    viewModel: ProfileRootViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Back from the "Mi entrenamiento" editor the row shows the intention just saved.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ProfileRootEvent.ReloadIntention) }
    ProfileRootScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier, actions = actions)
}

/**
 * The Perfil tab root (ADR-37, US-05, US-56) on [AppScreen]: the "?" (Acerca de) and the notifications bell in the
 * bar, the 72 dp photo-and-name header, three cards (C-150, R-109): "Mi entrenamiento" (value = the intention),
 * "Estadísticas" with the "Peso", "Fuerza", "Calorías", "Entrenos" and "Actividad" rows (chevron only, no value), and
 * "Preferencias" alone. The bar holds the "?" (Acerca de) then the bell. "Mi entrenamiento" shows its value once it
 * is known. The debug catalog button appears where [ProfileRootActions.onOpenCatalog] is non-null (local flavor).
 * It holds no editable state: nothing to restore.
 */
@Composable
fun ProfileRootScreen(
    state: ProfileRootUiState,
    onEvent: (ProfileRootEvent) -> Unit,
    modifier: Modifier = Modifier,
    actions: ProfileRootActions = ProfileRootActions(),
) {
    val bell = notificationsBarAction("profile_action_notifications")
    val aboutCd = stringResource(Res.string.profile_about_action_cd)
    val onOpenAbout = actions.onOpenAbout
    val about = remember(aboutCd, onOpenAbout) {
        helpTopBarAction(aboutCd, "profile_action_about", onOpenAbout)
    }
    val barActions = remember(about, bell) { listOfNotNull(about, bell) }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        RootHeader(state)
        AppCard(Modifier.fillMaxWidth()) {
            NavigationRow(
                label = stringResource(Res.string.preferences_my_training_title),
                onClick = actions.onOpenMyTraining,
                modifier = Modifier.testTag("profile_my_training_row"),
                value = state.intention?.let { stringResource(it.label) },
                valueLeading = state.intention?.let { { IntentionIcon(it, selected = true) } },
            )
        }
        StatisticsSection(actions)
        AppCard(Modifier.fillMaxWidth()) {
            NavigationRow(
                label = stringResource(Res.string.preferences_title),
                onClick = actions.onOpenPreferences,
                modifier = Modifier.testTag("profile_preferences_row"),
                leading = { RowIcon(Res.drawable.ic_settings) },
            )
        }
    }
}

/** One 72 dp row: the 56 dp photo and the name, start-aligned (C-150). */
@Composable
private fun RootHeader(state: ProfileRootUiState.Loaded) {
    Row(
        Modifier.fillMaxWidth().height(HEADER_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).testTag("profile_root_name"),
            )
        }
    }
}

/** "Estadísticas": a heading and one card of [NavigationRow]s, each label equal to its screen title (R-04, R-109). */
@Composable
private fun StatisticsSection(actions: ProfileRootActions) {
    Column(Modifier.fillMaxWidth()) {
        ListSectionHeader(
            title = stringResource(Res.string.profile_statistics_section),
            modifier = Modifier.testTag("profile_statistics_heading"),
        )
        AppCard(Modifier.fillMaxWidth()) {
            StatRow(Res.string.profile_weight_row, Res.drawable.ic_monitor_weight, "profile_weight_row") {
                actions.onOpenWeight()
            }
            StatRow(Res.string.profile_strength_row, Res.drawable.ic_fitness_center, "profile_strength_row") {
                actions.onOpenStrength()
            }
            StatRow(
                Res.string.profile_diary_history_row,
                Res.drawable.ic_restaurant_outlined,
                "profile_diary_history_row",
            ) { actions.onOpenDiaryHistory() }
            StatRow(Res.string.profile_training_stats_row, Res.drawable.ic_bar_chart, "profile_training_stats_row") {
                actions.onOpenTrainingStats()
            }
            StatRow(Res.string.profile_activity_row, Res.drawable.ic_directions_walk_outlined, "profile_activity_row") {
                actions.onOpenActivity()
            }
        }
    }
}

@Composable
private fun StatRow(label: StringResource, icon: DrawableResource, tag: String, onClick: () -> Unit) {
    NavigationRow(
        label = stringResource(label),
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        leading = { RowIcon(icon) },
    )
}

@Composable
private fun RowIcon(icon: DrawableResource) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AppTheme.colors.textSecondary,
        modifier = Modifier.size(STAT_ICON_SIZE),
    )
}

@Composable
private fun RootSkeleton() {
    Column(
        Modifier.testTag("profile_root_loading").fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            Modifier.height(HEADER_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(AppAvatarDefaults.RootSize), AppShapes.full)
            SkeletonBox(Modifier.size(Spacing.xl * NAME_SKELETON_WIDTH_UNITS, Spacing.xl), AppShapes.sm)
        }
        ListRowSkeleton(Modifier.fillMaxWidth(), rows = SKELETON_ROWS)
    }
}
