@file:Suppress("MatchingDeclarationName") // The hub actions are a small holder next to the screen.

package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_account_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_checkin_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_energy_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_macros_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_meals_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_personal_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The hub's destinations: the six section editors, the meals editor (T-126) and "Cuenta". */
class PreferencesHubActions(
    val onOpenSection: (PreferencesSection) -> Unit = {},
    val onOpenMeals: () -> Unit = {},
    val onOpenAccount: () -> Unit = {},
)

/**
 * Preferencias hub (US-56): stateless groups of [NavigationRow]s, one per editor, on a full-screen [AppScreen]:
 * [Datos personales] · [Objetivo calórico, Macros, Comidas del día, Cocina] · [Entrenamiento] ·
 * [Check-in semanal] · [Cuenta]. Each row
 * label is the title of the screen it opens (R-04). It holds no state, so there is nothing to restore.
 */
@Composable
fun PreferencesHubScreen(onBack: () -> Unit, actions: PreferencesHubActions, modifier: Modifier = Modifier) {
    AppScreen(
        title = stringResource(Res.string.preferences_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "preferences",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("preferences_hub"),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            HubGroup {
                HubRow(Res.string.profile_personal_title, "personal") {
                    actions.onOpenSection(PreferencesSection.PersonalData)
                }
            }
            HubGroup {
                HubRow(Res.string.preferences_energy_title, "energy") {
                    actions.onOpenSection(PreferencesSection.EnergyGoal)
                }
                HubRow(Res.string.preferences_macros_title, "macros") {
                    actions.onOpenSection(PreferencesSection.Macros)
                }
                HubRow(Res.string.preferences_meals_title, "meals", actions.onOpenMeals)
                HubRow(Res.string.preferences_cooking_title, "cooking") {
                    actions.onOpenSection(PreferencesSection.Cooking)
                }
            }
            HubGroup {
                HubRow(Res.string.preferences_training_title, "training") {
                    actions.onOpenSection(PreferencesSection.Training)
                }
            }
            HubGroup {
                HubRow(Res.string.preferences_checkin_title, "checkin") {
                    actions.onOpenSection(PreferencesSection.CheckIn)
                }
            }
            HubGroup { HubRow(Res.string.preferences_account_title, "account", actions.onOpenAccount) }
        }
    }
}

@Composable
private fun HubGroup(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun HubRow(label: StringResource, key: String, onClick: () -> Unit) {
    NavigationRow(
        label = stringResource(label),
        onClick = onClick,
        modifier = Modifier.testTag("preferences_row_$key"),
    )
}
