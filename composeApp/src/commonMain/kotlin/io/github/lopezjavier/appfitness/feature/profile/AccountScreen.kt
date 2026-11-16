package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.account_title
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Cuenta" (C-155, R-113): "Cerrar sesión" and "Borrar cuenta", opened from the Perfil root, each behind its own
 * confirmation (R-08). A failed delete is a message (R-37).
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accountState: AccountUiState = AccountUiState(),
    onAccountEvent: (AccountEvent) -> Unit = {},
    accountEffects: Flow<AccountEffect> = emptyFlow(),
) {
    AppScreen(
        title = stringResource(Res.string.account_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "account",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("account_screen"),
        ) {
            AccountSection(accountState, onAccountEvent, accountEffects)
        }
    }
}

/** Stateful "Cuenta": connects [AccountViewModel] to [AccountScreen]. */
@Composable
fun AccountRoot(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountScreen(
        onBack = onBack,
        modifier = modifier,
        accountState = state,
        onAccountEvent = viewModel::onEvent,
        accountEffects = viewModel.effects,
    )
}
