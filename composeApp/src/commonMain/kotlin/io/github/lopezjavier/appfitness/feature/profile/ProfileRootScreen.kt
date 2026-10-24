package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.delete_account_error
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_account_protected
import io.github.lopezjavier.appfitness.composeapp.resources.profile_delete_account
import io.github.lopezjavier.appfitness.composeapp.resources.profile_deleting_account
import io.github.lopezjavier.appfitness.composeapp.resources.profile_diary_history_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sign_out
import io.github.lopezjavier.appfitness.composeapp.resources.profile_statistics_section
import io.github.lopezjavier.appfitness.composeapp.resources.profile_strength_row
import io.github.lopezjavier.appfitness.composeapp.resources.profile_weight_row
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppAvatar
import io.github.lopezjavier.appfitness.designsystem.AppAvatarDefaults
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

private const val NAME_MAX_LINES = 2
private const val NAME_SKELETON_WIDTH_UNITS = 8

/** The account buttons' callbacks, grouped to keep the layout composables' signatures short. */
@Suppress("LongParameterList") // One callback per row or button.
private class RootActions(
    val onOpenPreferences: () -> Unit,
    val onOpenCatalog: (() -> Unit)?,
    val onOpenStrength: () -> Unit,
    val onOpenWeight: () -> Unit,
    val onOpenDiaryHistory: () -> Unit,
    val onSignOut: () -> Unit,
    val onDelete: () -> Unit,
)

/** Stateful Perfil root: connects [ProfileRootViewModel] to the stateless [ProfileRootScreen]. */
@Composable
fun ProfileRootRoute(
    modifier: Modifier = Modifier,
    onOpenPreferences: () -> Unit = {},
    onOpenCatalog: (() -> Unit)? = null,
    onOpenStrength: () -> Unit = {},
    onOpenDiaryHistory: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
    viewModel: ProfileRootViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileRootScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onOpenPreferences = onOpenPreferences,
        onOpenCatalog = onOpenCatalog,
        onOpenStrength = onOpenStrength,
        onOpenDiaryHistory = onOpenDiaryHistory,
        onOpenWeight = onOpenWeight,
        effects = viewModel.effects,
    )
}

/**
 * The Perfil tab root (ADR-37, US-05): the photo and name above, and the actions pinned to the bottom: "Preferencias"
 * (primary), the debug catalog where [onOpenCatalog] is non-null (local flavor), "Cerrar sesión" and "Borrar cuenta",
 * each behind its own confirmation. [effects] feed the delete-failure snackbar.
 */
@Composable
fun ProfileRootScreen(
    state: ProfileRootUiState,
    onEvent: (ProfileRootEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPreferences: () -> Unit = {},
    onOpenCatalog: (() -> Unit)? = null,
    onOpenStrength: () -> Unit = {},
    onOpenDiaryHistory: () -> Unit = {},
    onOpenWeight: () -> Unit = {},
    effects: Flow<ProfileRootEffect> = emptyFlow(),
) {
    val snackbarHost = remember { SnackbarHostState() }
    DeleteFailureEffects(effects, snackbarHost)
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val actions =
        RootActions(
            onOpenPreferences,
            onOpenCatalog,
            onOpenStrength,
            onOpenWeight,
            onOpenDiaryHistory,
            { confirmingSignOut = true },
            { confirmingDelete = true },
        )
    Box(modifier.fillMaxSize().testTag("profile_screen"), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = Spacing.screenMargin),
        ) {
            when (state) {
                ProfileRootUiState.Loading -> Header(Modifier.weight(1f)) { HeaderSkeleton() }

                ProfileRootUiState.LoadFailed -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.root_error_message),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(ProfileRootEvent.Retry)
                        },
                        testTagPrefix = "profile_root_error",
                    )
                }

                is ProfileRootUiState.Loaded -> Header(Modifier.weight(1f)) { HeaderContent(state) }
            }
            SnackbarHost(snackbarHost) { data -> Snackbar { Text(data.visuals.message) } }
            ActionBlock(state, actions)
        }
    }
    if (confirmingSignOut) {
        SignOutDialog(
            onConfirm = {
                confirmingSignOut = false
                onEvent(ProfileRootEvent.SignOutConfirmed)
            },
            onDismiss = { confirmingSignOut = false },
        )
    }
    if (confirmingDelete) {
        DeleteAccountDialog(
            onConfirm = {
                confirmingDelete = false
                onEvent(ProfileRootEvent.DeleteAccountConfirmed)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** "Estadísticas": a heading and the "Fuerza" and "Historial de comidas" rows. */
@Composable
private fun StatisticsSection(onOpenStrength: () -> Unit, onOpenWeight: () -> Unit, onOpenDiaryHistory: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.profile_statistics_section).uppercase(),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        StatisticsRow(stringResource(Res.string.profile_strength_row), "profile_strength_row", onOpenStrength)
        StatisticsRow(stringResource(Res.string.profile_weight_row), "profile_weight_row", onOpenWeight)
        StatisticsRow(
            stringResource(Res.string.profile_diary_history_row),
            "profile_diary_history_row",
            onOpenDiaryHistory,
        )
    }
}

/** One 48 dp "Estadísticas" row with a decorative chevron; the label is the accessible name. */
@Composable
private fun StatisticsRow(label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Image(
            imageVector = vectorResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(AppTheme.colors.textSecondary),
        )
    }
}

/** Snackbar for a failed account deletion. */
@Composable
private fun DeleteFailureEffects(effects: Flow<ProfileRootEffect>, host: SnackbarHostState) {
    val failed = stringResource(Res.string.delete_account_error)
    val offline = stringResource(Res.string.onboarding_save_error_offline)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ProfileRootEffect.DeleteFailed ->
                    host.showSnackbar(if (effect.offline) offline else failed, duration = SnackbarDuration.Long)
            }
        }
    }
}

/** Scrollable upper block, centred horizontally, starting [Spacing.xl] below the top bar. */
@Composable
private fun Header(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        content()
    }
}

@Composable
private fun HeaderContent(state: ProfileRootUiState.Loaded) {
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
    }
}

@Composable
private fun HeaderSkeleton() {
    Column(
        Modifier.testTag("profile_root_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SkeletonBox(Modifier.size(AppAvatarDefaults.RootSize), AppShapes.full)
        SkeletonBox(Modifier.size(Spacing.xl * NAME_SKELETON_WIDTH_UNITS, Spacing.xl), AppShapes.sm)
    }
}

/**
 * "Estadísticas" (the "Fuerza", "Peso" and "Calorías" rows), "Preferencias", catalog (local only), a 24 dp gap,
 * "Cerrar sesión" and "Borrar cuenta" (loaded only).
 */
@Composable
private fun ActionBlock(state: ProfileRootUiState, actions: RootActions) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state is ProfileRootUiState.Loaded) {
            StatisticsSection(actions.onOpenStrength, actions.onOpenWeight, actions.onOpenDiaryHistory)
            PrimaryButton(
                text = stringResource(Res.string.preferences_title),
                onClick = actions.onOpenPreferences,
                modifier = Modifier.fillMaxWidth().testTag("profile_root_preferences_button"),
            )
        }
        val onOpenCatalog = actions.onOpenCatalog
        if (onOpenCatalog != null) {
            SecondaryButton(
                text = stringResource(Res.string.catalog_title),
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth().testTag("profile_root_catalog_button"),
            )
        }
        GhostButton(
            text = stringResource(Res.string.profile_sign_out),
            onClick = actions.onSignOut,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("profile_root_sign_out"),
            tone = GhostTone.Neutral,
        )
        if (state is ProfileRootUiState.Loaded) {
            val protectedNote = stringResource(Res.string.profile_account_protected)
            val isProtected = state.protection == AccountProtection.Protected
            GhostButton(
                text = stringResource(
                    if (state.isDeleting) Res.string.profile_deleting_account else Res.string.profile_delete_account,
                ),
                onClick = actions.onDelete,
                modifier = Modifier.fillMaxWidth()
                    .semantics { if (isProtected) stateDescription = protectedNote }
                    .testTag("profile_root_delete_account"),
                enabled = state.canDelete,
                tone = GhostTone.Destructive,
                loading = state.isDeleting,
            )
            if (isProtected) {
                // The button also announces this text as its state, so TalkBack users get it with the button.
                Text(
                    text = protectedNote,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("profile_root_delete_protected_note"),
                )
            }
        }
    }
}
