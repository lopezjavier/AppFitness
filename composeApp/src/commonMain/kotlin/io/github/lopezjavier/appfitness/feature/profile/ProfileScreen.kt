package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.profile_save
import io.github.lopezjavier.appfitness.composeapp.resources.profile_saved
import io.github.lopezjavier.appfitness.composeapp.resources.profile_sign_out
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_message
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
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
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategySection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 3
private const val SKELETON_CARD_UNITS = 5

/** Stateful Profile tab: connects [ProfileViewModel] to the stateless [ProfileScreen]. */
@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    onOpenCatalog: (() -> Unit)? = null,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        onOpenCatalog = onOpenCatalog,
        effects = viewModel.effects,
    )
}

/**
 * The Profile tab (US-06, US-07, US-08, US-05 sign out). Cards scroll above a pinned "Guardar" footer; from 600 dp
 * the personal data and energy cards sit in the left column and the macro split in the right one. [onOpenCatalog] is
 * non-null only where the debug component catalog is available (local flavor). [effects] feed the "Cambios
 * guardados" / error snackbars and the confirm haptic.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: (() -> Unit)? = null,
    effects: Flow<ProfileEffect> = emptyFlow(),
) {
    val snackbarHost = remember { SnackbarHostState() }
    SaveEffects(effects, snackbarHost)
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().testTag("profile_screen"), contentAlignment = Alignment.TopCenter) {
        when (state) {
            ProfileUiState.Loading -> ProfileLoading()

            ProfileUiState.LoadFailed -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.root_error_message),
                        action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(ProfileEvent.Retry) },
                        testTagPrefix = "profile_error",
                    )
                }
                AccountActions(onOpenCatalog, {
                    confirmingSignOut = true
                }, Modifier.padding(horizontal = Spacing.screenMargin))
            }

            is ProfileUiState.Loaded -> ProfileLoaded(state, onEvent, snackbarHost, onOpenCatalog) {
                confirmingSignOut = true
            }
        }
    }
    if (confirmingSignOut) {
        AppConfirmDialog(
            title = stringResource(Res.string.sign_out_dialog_title),
            message = stringResource(Res.string.sign_out_dialog_message),
            confirmLabel = stringResource(Res.string.sign_out_dialog_confirm),
            cancelLabel = stringResource(Res.string.sign_out_dialog_cancel),
            onConfirm = {
                confirmingSignOut = false
                onEvent(ProfileEvent.SignOutConfirmed)
            },
            onDismiss = { confirmingSignOut = false },
            testTagPrefix = "sign_out_dialog",
        )
    }
}

/** Haptic and snackbar for the result of a save: confirm on success only, a long message-only snackbar on failure. */
@Composable
private fun SaveEffects(effects: Flow<ProfileEffect>, host: SnackbarHostState) {
    val haptic = LocalHapticFeedback.current
    val saved = stringResource(Res.string.profile_saved)
    val failed = stringResource(Res.string.onboarding_save_error)
    val offline = stringResource(Res.string.onboarding_save_error_offline)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ProfileEffect.Saved -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    host.showSnackbar(saved)
                }

                is ProfileEffect.SaveFailed ->
                    host.showSnackbar(if (effect.offline) offline else failed, duration = SnackbarDuration.Long)
            }
        }
    }
}

@Composable
private fun ProfileLoading() {
    Column(
        Modifier
            .widthIn(max = WindowWidthClassDefaults.TwoColumnContentMaxWidth)
            .fillMaxSize()
            .padding(Spacing.screenMargin)
            .testTag("profile_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_CARDS) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * SKELETON_CARD_UNITS), AppShapes.lg)
        }
    }
}

@Composable
private fun ProfileLoaded(
    state: ProfileUiState.Loaded,
    onEvent: (ProfileEvent) -> Unit,
    snackbarHost: SnackbarHostState,
    onOpenCatalog: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= WindowWidthClassDefaults.WideMinWidth
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.TwoColumnContentMaxWidth).fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                    .testTag("profile_content"),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                if (wide) {
                    TwoColumns(state, onEvent, onOpenCatalog, onSignOut)
                } else {
                    PersonalAndEnergy(state, onEvent)
                    MacroSplitCard(state, onEvent)
                    AccountActions(onOpenCatalog, onSignOut, Modifier.padding(top = Spacing.blockGap - Spacing.cardGap))
                }
            }
            SnackbarHost(snackbarHost) { data -> Snackbar { Text(data.visuals.message) } }
            SaveFooter(state, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.PersonalAndEnergy(state: ProfileUiState.Loaded, onEvent: (ProfileEvent) -> Unit) {
    PersonalDataCard(state, onEvent)
    AppCard(Modifier.fillMaxWidth()) {
        EnergyStrategySection(
            state = state.energy,
            onEvent = { onEvent(ProfileEvent.Energy(it)) },
            testTagPrefix = "profile_energy",
        )
    }
}

@Composable
private fun TwoColumns(
    state: ProfileUiState.Loaded,
    onEvent: (ProfileEvent) -> Unit,
    onOpenCatalog: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            PersonalAndEnergy(state, onEvent)
            AccountActions(onOpenCatalog, onSignOut, Modifier.padding(top = Spacing.blockGap - Spacing.cardGap))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            MacroSplitCard(state, onEvent)
        }
    }
}

/** Catalog (local flavor only) and "Cerrar sesión", below the cards and away from "Guardar". */
@Composable
private fun AccountActions(onOpenCatalog: (() -> Unit)?, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        if (onOpenCatalog != null) {
            SecondaryButton(
                text = stringResource(Res.string.catalog_title),
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth().testTag("profile_catalog_button"),
            )
        }
        GhostButton(
            text = stringResource(Res.string.profile_sign_out),
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button"),
            tone = GhostTone.Destructive,
        )
    }
}

/** The pinned "Guardar" bar above the bottom navigation; always shown, disabled unless there is something to save. */
@Composable
private fun SaveFooter(state: ProfileUiState.Loaded, onEvent: (ProfileEvent) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceBase)
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.md),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.profile_save),
            onClick = { onEvent(ProfileEvent.Save) },
            modifier = Modifier.fillMaxWidth().testTag("profile_save_button"),
            enabled = state.canSave,
            loading = state.isSaving,
        )
    }
}
