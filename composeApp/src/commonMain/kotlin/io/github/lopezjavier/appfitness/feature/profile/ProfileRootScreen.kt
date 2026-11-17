@file:Suppress("TooManyFunctions") // One small composable per block of the Perfil root.

package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.lopezjavier.appfitness.composeapp.resources.px_stats_hero_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.px_stats_hero_strength
import io.github.lopezjavier.appfitness.composeapp.resources.px_stats_hero_weight
import io.github.lopezjavier.appfitness.composeapp.resources.px_stats_value_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.px_stats_value_kg
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.designsystem.AppAvatar
import io.github.lopezjavier.appfitness.designsystem.AppAvatarDefaults
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.HeroStat
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.ListRowSkeleton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.helpTopBarAction
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.feature.notifications.notificationsBarAction
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val NAME_MAX_LINES = 2
private const val NAME_SKELETON_WIDTH_UNITS = 8
private const val SKELETON_ROWS = 4
private val HEADER_HEIGHT = Spacing.xl * 3
private val HERO_AVATAR_SIZE = Spacing.xl * 3

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
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        RootHeader(state)
        AppCard(Modifier.fillMaxWidth()) {
            NavigationRow(
                label = stringResource(Res.string.preferences_my_training_title),
                onClick = actions.onOpenMyTraining,
                modifier = Modifier.testTag("profile_my_training_row"),
                value = state.intention?.let { stringResource(it.label) },
                valueLeading = state.intention?.let { { IntentionIcon(it, selected = true) } },
                leading = { RowBadge(Res.drawable.ic_fitness_center, colors.neonLime) },
            )
        }
        StatisticsSection(state, actions)
        AppCard(Modifier.fillMaxWidth()) {
            NavigationRow(
                label = stringResource(Res.string.preferences_title),
                onClick = actions.onOpenPreferences,
                modifier = Modifier.testTag("profile_preferences_row"),
                leading = { RowBadge(Res.drawable.ic_settings, colors.macroCarbs) },
            )
        }
    }
}

/** The hero card: the photo in a lit ring, the name, the intention as a chip and the highlights as hero stats. */
@Composable
private fun RootHeader(state: ProfileRootUiState.Loaded) {
    val colors = AppTheme.colors
    AppCard(Modifier.fillMaxWidth(), glow = colors.neonLime) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppAvatar(
                    imageUrl = state.avatarUrl,
                    size = HERO_AVATAR_SIZE,
                    testTagPrefix = "profile_root",
                    modifier = Modifier.testTag("profile_root_avatar"),
                    ring = colors.neonLime,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.displayName?.let { name ->
                        Text(
                            text = name,
                            style = AppTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            maxLines = NAME_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("profile_root_name"),
                        )
                    }
                    state.intention?.let { IntentionChip(it) }
                }
            }
            HeroHighlights(state)
        }
    }
}

@Composable
private fun IntentionChip(intention: TrainingIntention) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .clip(AppShapes.full)
            .background(AppPaint.tintSurface(colors.neonLime))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntentionIcon(intention, selected = true, size = Spacing.lg)
        Text(
            text = stringResource(intention.label),
            style = AppTheme.typography.bodySmall,
            color = colors.neonLime,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Weight, strength record and daily calories as hero stats; only those already known show. */
@Composable
private fun HeroHighlights(state: ProfileRootUiState.Loaded) {
    val colors = AppTheme.colors
    val weight = state.weightKg
    val record = state.strengthRecordKg
    val kcal = state.avgKcal
    if (weight == null && record == null && kcal == null) return
    Row(
        Modifier.fillMaxWidth().testTag("profile_root_highlights"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (weight != null) {
            HeroStat(
                Res.drawable.ic_monitor_weight,
                AppNumberFormat.decimal(weight),
                stringResource(Res.string.px_stats_hero_weight),
                colors.athleticCyan,
                Modifier.weight(1f),
                unit = stringResource(Res.string.unit_kg),
            )
        }
        if (record != null) {
            HeroStat(
                Res.drawable.ic_fitness_center,
                AppNumberFormat.decimal(record),
                stringResource(Res.string.px_stats_hero_strength),
                colors.neonLime,
                Modifier.weight(1f),
                unit = stringResource(Res.string.unit_kg),
            )
        }
        if (kcal != null) {
            HeroStat(
                Res.drawable.ic_restaurant_outlined,
                AppNumberFormat.integer(kcal),
                stringResource(Res.string.px_stats_hero_kcal),
                colors.energyAmber,
                Modifier.weight(1f),
            )
        }
    }
}

/** "Estadísticas": a heading and one card of [NavigationRow]s, each label equal to its screen title (R-04, R-109). */
@Composable
private fun StatisticsSection(state: ProfileRootUiState.Loaded, actions: ProfileRootActions) {
    val colors = AppTheme.colors
    val weight = state.weightKg?.let { stringResource(Res.string.px_stats_value_kg, AppNumberFormat.decimal(it)) }
    val record = state.strengthRecordKg?.let {
        stringResource(Res.string.px_stats_value_kg, AppNumberFormat.decimal(it))
    }
    val kcal = state.avgKcal?.let { stringResource(Res.string.px_stats_value_kcal, AppNumberFormat.integer(it)) }
    val rows = listOf(
        StatRowSpec(
            Res.string.profile_weight_row,
            Res.drawable.ic_monitor_weight,
            colors.athleticCyan,
            "profile_weight_row",
            weight,
            actions.onOpenWeight,
        ),
        StatRowSpec(
            Res.string.profile_strength_row,
            Res.drawable.ic_fitness_center,
            colors.neonLime,
            "profile_strength_row",
            record,
            actions.onOpenStrength,
        ),
        StatRowSpec(
            Res.string.profile_diary_history_row,
            Res.drawable.ic_restaurant_outlined,
            colors.energyAmber,
            "profile_diary_history_row",
            kcal,
            actions.onOpenDiaryHistory,
        ),
        StatRowSpec(
            Res.string.profile_training_stats_row,
            Res.drawable.ic_bar_chart,
            colors.macroFat,
            "profile_training_stats_row",
            null,
            actions.onOpenTrainingStats,
        ),
        StatRowSpec(
            Res.string.profile_activity_row,
            Res.drawable.ic_directions_walk_outlined,
            colors.electricGreen,
            "profile_activity_row",
            null,
            actions.onOpenActivity,
        ),
    )
    Column(Modifier.fillMaxWidth()) {
        ListSectionHeader(
            title = stringResource(Res.string.profile_statistics_section),
            modifier = Modifier.testTag("profile_statistics_heading"),
        )
        AppCard(Modifier.fillMaxWidth()) { rows.forEach { StatRow(it) } }
    }
}

private class StatRowSpec(
    val label: StringResource,
    val icon: DrawableResource,
    val tint: Color,
    val tag: String,
    val value: String?,
    val onClick: () -> Unit,
)

@Composable
private fun StatRow(spec: StatRowSpec) {
    NavigationRow(
        label = stringResource(spec.label),
        onClick = spec.onClick,
        modifier = Modifier.testTag(spec.tag),
        value = spec.value,
        leading = { RowBadge(spec.icon, spec.tint) },
    )
}

@Composable
private fun RowBadge(icon: DrawableResource, tint: Color) {
    IconBadge(icon, tint, size = IconBadgeDefaults.SmallSize, iconSize = IconBadgeDefaults.SmallIconSize)
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
