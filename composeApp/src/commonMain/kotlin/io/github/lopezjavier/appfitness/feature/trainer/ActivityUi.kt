package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_below_note
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_save_offline
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_sheet_decrease
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_sheet_increase
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_sheet_label
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_sheet_state
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.cardio_sheet_value
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_help_base
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_help_calories
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_help_fat_loss
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_help_muscle
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_row
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_cardio_title
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_header
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_steps_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_steps_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_steps_note_average
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_steps_note_floor
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_steps_row
import io.github.lopezjavier.appfitness.composeapp.resources.plan_activity_steps_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.CardioMinutes
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.StepsBasis
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The paragraphs of the cardio "?" sheet, in order: the base line, the intention line (fat loss, strength and
 * muscle only), and the calories line LAST (`07` §3). Pure so a test can assert the order.
 */
internal fun activityCardioHelp(intention: String): List<StringResource> = buildList {
    add(Res.string.plan_activity_cardio_help_base)
    when (intention) {
        "fat_loss" -> add(Res.string.plan_activity_cardio_help_fat_loss)
        "strength", "muscle" -> add(Res.string.plan_activity_cardio_help_muscle)
    }
    add(Res.string.plan_activity_cardio_help_calories)
}

/**
 * "Actividad" on the plan proposal: the cardio row (tap opens the minutes sheet, the "?" says why) and the steps row
 * (read-only, with a plain note). Calories are never shown to depend on either.
 */
@Composable
internal fun ActivityCard(activity: PlanActivity, intention: String, enabled: Boolean, onEditCardio: () -> Unit) {
    Column(Modifier.testTag("plan_proposal_activity_section")) {
        ListSectionHeader(stringResource(Res.string.plan_activity_header))
        AppCard(Modifier.fillMaxWidth()) {
            AppListRow(
                headline = stringResource(
                    Res.string.plan_activity_cardio_row,
                    AppNumberFormat.integer(activity.cardioMinWeek),
                ),
                modifier = Modifier.testTag("plan_proposal_activity_cardio_row"),
                onClick = onEditCardio,
                enabled = enabled,
                trailing = {
                    HelpButton(
                        contentDescription = stringResource(Res.string.plan_activity_cardio_help_cd),
                        title = stringResource(Res.string.plan_activity_cardio_title),
                        content = HelpContent(activityCardioHelp(intention).map { stringResource(it) }),
                        testTag = "plan_proposal_activity_cardio_help",
                    )
                },
            )
            if (CardioMinutes.isBelowRecommended(activity.cardioMinWeek)) {
                InlineNote(
                    text = stringResource(Res.string.cardio_below_note),
                    testTag = "plan_proposal_activity_below_note",
                )
            }
            AppListRow(
                headline = stringResource(
                    Res.string.plan_activity_steps_row,
                    AppNumberFormat.integer(activity.stepsDay),
                ),
                modifier = Modifier.testTag("plan_proposal_activity_steps_row"),
                supporting = if (activity.stepsBasis == StepsBasis.Floor) {
                    stringResource(
                        Res.string.plan_activity_steps_note_floor,
                        AppNumberFormat.integer(activity.stepsDay),
                    )
                } else {
                    stringResource(Res.string.plan_activity_steps_note_average)
                },
                trailing = {
                    HelpButton(
                        contentDescription = stringResource(Res.string.plan_activity_steps_help_cd),
                        title = stringResource(Res.string.plan_activity_steps_title),
                        body = stringResource(Res.string.plan_activity_steps_help_body),
                        testTag = "plan_proposal_activity_steps_help",
                    )
                },
            )
        }
    }
}

/**
 * Edit sheet of the weekly cardio minutes (R-07, R-47): a stepper 0..600 in steps of 5, the live below-150 note and
 * a pinned "Guardar" that is enabled only when the draft changed (and online). X and back close it without saving.
 */
@Composable
internal fun CardioMinutesSheet(
    edit: CardioEdit,
    online: Boolean,
    onChange: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val value = stringResource(Res.string.cardio_sheet_value, AppNumberFormat.integer(edit.draft))
    AppEditSheet(
        title = stringResource(Res.string.cardio_sheet_title),
        commit = SheetCommit.Save,
        onCommit = onSave,
        onDismiss = onDismiss,
        commitEnabled = edit.canSave && online,
        saving = edit.saving,
        commitHint = if (online) null else offlineHint(stringResource(Res.string.cardio_save_offline)),
        testTagPrefix = "cardio_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.cardio_sheet_label),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                NumberStepper(
                    valueText = value,
                    onDecrement = { onChange(edit.draft - CardioMinutes.STEP) },
                    onIncrement = { onChange(edit.draft + CardioMinutes.STEP) },
                    decrementEnabled = edit.draft > CardioMinutes.MIN && !edit.saving,
                    incrementEnabled = edit.draft < CardioMinutes.MAX && !edit.saving,
                    decrementContentDescription = stringResource(Res.string.cardio_sheet_decrease),
                    incrementContentDescription = stringResource(Res.string.cardio_sheet_increase),
                    stateDescription = stringResource(
                        Res.string.cardio_sheet_state,
                        AppNumberFormat.integer(edit.draft),
                    ),
                    testTagPrefix = "cardio_sheet_stepper",
                )
            }
            if (edit.belowRecommended) {
                InlineNote(text = stringResource(Res.string.cardio_below_note), testTag = "cardio_sheet_note")
            }
        }
    }
}
