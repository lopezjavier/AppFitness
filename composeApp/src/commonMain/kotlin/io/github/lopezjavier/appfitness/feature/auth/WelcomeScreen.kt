package io.github.lopezjavier.appfitness.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.auth_error_generic
import io.github.lopezjavier.appfitness.composeapp.resources.auth_error_no_network
import io.github.lopezjavier.appfitness.composeapp.resources.welcome_google_button
import io.github.lopezjavier.appfitness.composeapp.resources.welcome_logo_description
import io.github.lopezjavier.appfitness.composeapp.resources.welcome_signing_in
import io.github.lopezjavier.appfitness.composeapp.resources.welcome_test_login
import io.github.lopezjavier.appfitness.composeapp.resources.welcome_title_prefix
import io.github.lopezjavier.appfitness.composeapp.resources.welcome_title_suffix
import io.github.lopezjavier.appfitness.designsystem.AppLogo
import io.github.lopezjavier.appfitness.designsystem.AppLogoDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Merging logo and name into one node needs no extra properties.
private val NoExtraSemantics: SemanticsPropertyReceiver.() -> Unit = {}

/** Stateful Welcome: connects [WelcomeViewModel] to the stateless [WelcomeScreen]. */
@Composable
fun WelcomeRoute(
    showTestLogin: Boolean,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WelcomeScreen(state = state, showTestLogin = showTestLogin, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * Welcome: the brand (logo + name) over the sign-in actions pinned to the bottom. "Test login" shows only
 * when [showTestLogin]. A failed attempt shows [WelcomeUiState.error] above the buttons, message only: the
 * user taps "Continuar con Google" again.
 */
@Composable
fun WelcomeScreen(
    state: WelcomeUiState,
    showTestLogin: Boolean,
    onEvent: (WelcomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surfaceBase).testTag("welcome_screen")) {
        val logoWidth = AppLogoDefaults.width(windowWidthClassFor(maxWidth))
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.screenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandZone(logoWidth, Modifier.weight(1f))
            ActionZone(state, showTestLogin, onEvent)
        }
    }
}

@Composable
private fun BrandZone(logoWidth: Dp, modifier: Modifier = Modifier) {
    Column(
        // Scrolls when the window is short (landscape) or the font is large, so nothing clips.
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            // Optically a bit above the centre.
            modifier = Modifier.padding(
                bottom = Spacing.xl,
            ).semantics(mergeDescendants = true, properties = NoExtraSemantics)
                .testTag("welcome_brand"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(
                modifier = Modifier.testTag("welcome_logo"),
                width = logoWidth,
                contentDescription = stringResource(Res.string.welcome_logo_description),
            )
            Spacer(Modifier.height(Spacing.blockGap))
            Text(
                text = brandTitle(),
                style = AppTheme.typography.displayHero,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.testTag("welcome_title"),
            )
        }
    }
}

/** The brand name: "App" in the primary text colour, "Fitness" in the app accent (lime). */
@Composable
private fun brandTitle(): AnnotatedString {
    val accent = AppTheme.colors.neonLime
    val prefix = stringResource(Res.string.welcome_title_prefix)
    val suffix = stringResource(Res.string.welcome_title_suffix)
    return buildAnnotatedString {
        append(prefix)
        withStyle(SpanStyle(color = accent)) { append(suffix) }
    }
}

@Composable
private fun ActionZone(state: WelcomeUiState, showTestLogin: Boolean, onEvent: (WelcomeEvent) -> Unit) {
    val signingIn = stringResource(Res.string.welcome_signing_in)
    Column(
        modifier = Modifier.widthIn(
            max = WindowWidthClassDefaults.NarrowContentMaxWidth,
        ).fillMaxWidth().padding(bottom = Spacing.blockGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        state.error?.let { error -> ErrorBlock(error) }
        PrimaryButton(
            text = stringResource(Res.string.welcome_google_button),
            onClick = { onEvent(WelcomeEvent.ContinueWithGoogle) },
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("welcome_google_button")
                .semantics { if (state.inFlight) stateDescription = signingIn },
            enabled = !state.inFlight,
            loading = state.inFlight,
        )
        if (showTestLogin) {
            SecondaryButton(
                text = stringResource(Res.string.welcome_test_login),
                onClick = { onEvent(WelcomeEvent.TestLogin) },
                modifier = Modifier.fillMaxWidth().testTag("welcome_test_login_button"),
                enabled = !state.inFlight,
            )
        }
    }
}

@Composable
private fun ErrorBlock(error: WelcomeError) {
    val message =
        when (error) {
            WelcomeError.NoNetwork -> stringResource(Res.string.auth_error_no_network)
            WelcomeError.Generic -> stringResource(Res.string.auth_error_generic)
        }
    Text(
        text = message,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.error,
        modifier = Modifier.fillMaxWidth().semantics {
            liveRegion = LiveRegionMode.Polite
        }.testTag("welcome_error_message"),
    )
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    AppTheme { WelcomeScreen(WelcomeUiState(), showTestLogin = true, onEvent = {}) }
}

@Preview
@Composable
private fun WelcomeScreenErrorPreview() {
    AppTheme { WelcomeScreen(WelcomeUiState(error = WelcomeError.NoNetwork), showTestLogin = false, onEvent = {}) }
}
