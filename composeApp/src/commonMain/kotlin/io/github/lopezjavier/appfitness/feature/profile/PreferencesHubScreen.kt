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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_avatar_person
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_meal_dinner
import io.github.lopezjavier.appfitness.composeapp.resources.ic_meal_lunch
import io.github.lopezjavier.appfitness.composeapp.resources.ic_notifications
import io.github.lopezjavier.appfitness.composeapp.resources.ic_restaurant_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_open
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_group_account
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_group_general
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_group_nutrition
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_group_training
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_hub_meals_value
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_hub_personal_value
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_hub_reminders_value
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_meals_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_personal_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.biometrics.activityTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The hub's destinations: the section editors, the meals editor and the default comidas editor (T-126, C-154). */
class PreferencesHubActions(
    val onOpenSection: (PreferencesSection) -> Unit = {},
    val onOpenMeals: () -> Unit = {},
    val onOpenMealDefaults: () -> Unit = {},
)

/**
 * Stateful hub: connects [PreferencesHubViewModel] to [PreferencesHubScreen]; the values are read again every time the
 * hub resumes, so a saved editor shows its new value on return.
 */
@Composable
fun PreferencesHubRoot(
    onBack: () -> Unit,
    actions: PreferencesHubActions,
    modifier: Modifier = Modifier,
    viewModel: PreferencesHubViewModel = koinViewModel(),
    accountViewModel: AccountViewModel = koinViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(PreferencesHubEvent.Refresh) }
    PreferencesHubScreen(
        onBack = onBack,
        actions = actions,
        modifier = modifier,
        summary = summary,
        accountState = accountState,
        onAccountEvent = accountViewModel::onEvent,
        accountEffects = accountViewModel.effects,
    )
}

/**
 * Preferencias hub (US-56, R-109): stateless, three named groups of [NavigationRow]s on a full-screen [AppScreen]:
 * General (Datos personales, Avisos), Nutrición (Comidas del día, Comidas por defecto, Cocina; calories and
 * macros live in the Trainer plan, T-303) and Entreno (Registro de series), then the last group "Cuenta" with
 * "Cerrar sesión" and "Borrar cuenta" (R-113, C-172). A row with a current value in [summary] shows it as its
 * supporting line (C-153). "Mi entrenamiento" lives in the Perfil root (T-154). Each row label is the title of the
 * screen it opens (R-04).
 */
@Composable
fun PreferencesHubScreen(
    onBack: () -> Unit,
    actions: PreferencesHubActions,
    modifier: Modifier = Modifier,
    summary: PreferencesHubSummary = PreferencesHubSummary(),
    accountState: AccountUiState = AccountUiState(),
    onAccountEvent: (AccountEvent) -> Unit = {},
    accountEffects: Flow<AccountEffect> = emptyFlow(),
) {
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
            GeneralGroup(summary, actions)
            NutritionGroup(summary, actions)
            HubGroup(Res.string.preferences_group_training) {
                HubRow(Res.string.preferences_training_title, "training") {
                    actions.onOpenSection(PreferencesSection.Training)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ListSectionHeader(stringResource(Res.string.preferences_group_account))
                AccountSection(accountState, onAccountEvent, accountEffects)
            }
        }
    }
}

@Composable
private fun GeneralGroup(summary: PreferencesHubSummary, actions: PreferencesHubActions) {
    HubGroup(Res.string.preferences_group_general) {
        HubRow(Res.string.profile_personal_title, "personal", summary.personal?.let { personalText(it) }) {
            actions.onOpenSection(PreferencesSection.PersonalData)
        }
        val on = summary.remindersOn
        val remindersText = on?.let {
            stringResource(Res.string.preferences_hub_reminders_value, it, PreferencesHubSummary.REMINDER_TOTAL)
        }
        HubRow(Res.string.preferences_reminders_title, "avisos", remindersText) {
            actions.onOpenSection(PreferencesSection.Reminders)
        }
    }
}

@Composable
private fun NutritionGroup(summary: PreferencesHubSummary, actions: PreferencesHubActions) {
    HubGroup(Res.string.preferences_group_nutrition) {
        HubRow(Res.string.preferences_meals_title, "meals", summary.mealCount?.let { mealsText(it) }) {
            actions.onOpenMeals()
        }
        HubRow(Res.string.meal_defaults_open, "meal_defaults") { actions.onOpenMealDefaults() }
        HubRow(Res.string.preferences_cooking_title, "cooking") {
            actions.onOpenSection(PreferencesSection.Cooking)
        }
    }
}

@Composable
private fun personalText(personal: PersonalSummary): String = stringResource(
    Res.string.preferences_hub_personal_value,
    personal.ageYears,
    personal.heightCm,
    stringResource(activityTitle(personal.activity)),
)

@Composable
private fun mealsText(count: Int): String = pluralStringResource(Res.plurals.preferences_hub_meals_value, count, count)

/** A named group (R-109): the `titleSection` header over one card of rows. */
@Composable
private fun HubGroup(title: StringResource, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ListSectionHeader(stringResource(title))
        AppCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun HubRow(label: StringResource, key: String, supporting: String? = null, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val (icon, tint) = when (key) {
        "personal" -> Res.drawable.ic_avatar_person to colors.athleticCyan
        "avisos" -> Res.drawable.ic_notifications to colors.energyAmber
        "meals" -> Res.drawable.ic_restaurant_outlined to colors.energyAmber
        "meal_defaults" -> Res.drawable.ic_meal_lunch to colors.macroFat
        "cooking" -> Res.drawable.ic_meal_dinner to colors.electricGreen
        else -> Res.drawable.ic_fitness_center to colors.athleticCyan
    }
    NavigationRow(
        label = stringResource(label),
        onClick = onClick,
        modifier = Modifier.testTag("preferences_row_$key"),
        supporting = supporting,
        leading = {
            IconBadge(icon, tint, size = IconBadgeDefaults.SmallSize, iconSize = IconBadgeDefaults.SmallIconSize)
        },
    )
}
