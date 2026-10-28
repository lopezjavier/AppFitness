package io.github.lopezjavier.appfitness.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.about_credits_section
import io.github.lopezjavier.appfitness.composeapp.resources.about_health_diagnostics_row
import io.github.lopezjavier.appfitness.composeapp.resources.about_licenses_row
import io.github.lopezjavier.appfitness.composeapp.resources.about_title
import io.github.lopezjavier.appfitness.composeapp.resources.about_version_flavor_format
import io.github.lopezjavier.appfitness.composeapp.resources.about_version_format
import io.github.lopezjavier.appfitness.composeapp.resources.credits
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import io.github.lopezjavier.appfitness.platform.rememberOpenSourceLicensesLauncher
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful "Acerca de": connects [AboutViewModel] and the system licences screen to the stateless [AboutScreen]. */
@Composable
fun AboutRoot(
    onBack: () -> Unit,
    onOpenHealthDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AboutScreen(
        state = state,
        onBack = onBack,
        onOpenLicenses = rememberOpenSourceLicensesLauncher()::open,
        onOpenHealthDiagnostics = onOpenHealthDiagnostics,
        modifier = modifier,
    )
}

/**
 * "Acerca de" (US-45) on [AppScreen]: app name, version line, the credits from the `credits` resource and the
 * "Licencias de código abierto" row. It is static and holds no editable state, so there is nothing to restore.
 */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenHealthDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val credits = stringArrayResource(Res.array.credits)
    val parsed = remember(credits) { credits.map(Credit::parse) }
    AppScreen(
        title = stringResource(Res.string.about_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "about",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("about_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Header(state.buildInfo)
            ListSectionHeader(
                title = stringResource(Res.string.about_credits_section),
                modifier = Modifier.testTag("about_credits_heading"),
            )
            parsed.forEachIndexed { index, credit -> CreditBlock(credit, index) }
            AppCard(Modifier.fillMaxWidth()) {
                NavigationRow(
                    label = stringResource(Res.string.about_licenses_row),
                    onClick = onOpenLicenses,
                    modifier = Modifier.testTag("about_licenses_row"),
                )
                NavigationRow(
                    label = stringResource(Res.string.about_health_diagnostics_row),
                    onClick = onOpenHealthDiagnostics,
                    modifier = Modifier.testTag("about_health_diagnostics_row"),
                )
            }
        }
    }
}

@Composable
private fun Header(info: BuildInfo) {
    val version = stringResource(Res.string.about_version_format, info.versionName, info.versionCode)
    val line = info.flavorLabel?.let { stringResource(Res.string.about_version_flavor_format, version, it) } ?: version
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = info.appName,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("about_app_name"),
        )
        Text(
            text = line,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("about_version"),
        )
    }
}

@Composable
private fun CreditBlock(credit: Credit, index: Int) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("about_credit_$index"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = credit.title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        Text(text = credit.text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
    }
}
